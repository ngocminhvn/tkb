//! Offline multiple-choice import parsing and rusqlite persistence.
//! No network, external API calls, or timetable DB access.
use rusqlite::{params, Connection, Result as SqlResult};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::BTreeSet;
use std::path::Path;
use std::time::Duration;

use crate::portal::{binary_count, binary_header, binary_i32, binary_i64, binary_str};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct QuizQuestion {
    pub prompt: String,
    pub options: Vec<String>,
    pub correct: Vec<usize>, // 0-based; may be empty for manual review
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct QuizQuestionRecord {
    pub id: i64,
    pub prompt: String,
    pub options: Vec<String>,
    pub correct: Vec<usize>,
    pub selected: Option<Vec<usize>>,
    pub was_correct: Option<bool>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct QuizDeckRecord {
    pub id: i64,
    pub title: String,
    pub total: i32,
    pub attempted: i32,
    pub correct: i32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct SourceLine {
    pub text: String,
    #[serde(default)]
    pub red: bool,
}

fn option_prefix(text: &str) -> Option<(usize, &str)> {
    let text = text.trim_start();
    let bytes = text.as_bytes();
    if bytes.is_empty() || !bytes[0].is_ascii_alphabetic() {
        return None;
    }
    let index = bytes[0].to_ascii_uppercase().wrapping_sub(b'A') as usize;
    if index >= 8 {
        return None;
    }
    // Some Word answer lists contain a standalone "a" without punctuation.
    if bytes.len() == 1 {
        return Some((index, ""));
    }
    if !matches!(bytes[1], b'.' | b')' | b':' | b'-') {
        return None;
    }
    // Prevent snippets such as "e) {" from being treated as extra choices.
    if index >= 4 && bytes[1] != b'.' {
        return None;
    }
    Some((index, text[2..].trim()))
}

fn question_header(text: &str) -> Option<&str> {
    let text = text.trim();
    let lower = text.to_lowercase();
    let prefix = if lower.starts_with("câu ") {
        5
    } else if lower.starts_with("question ") {
        9
    } else {
        0
    };
    if prefix > 0 {
        let tail = text.get(prefix..)?.trim_start();
        let digits = tail.bytes().take_while(u8::is_ascii_digit).count();
        if digits > 0 {
            return Some(tail[digits..].trim_start_matches(['.', ':', ')', '-', ' ']).trim());
        }
    }
    let digits = text.bytes().take_while(u8::is_ascii_digit).count();
    if digits > 0 && text.as_bytes().get(digits).is_some_and(|c| matches!(c, b'.' | b')' | b':')) {
        let remainder = text[digits + 1..].trim();
        if !remainder.is_empty() {
            return Some(remainder);
        }
    }
    None
}

fn explicit_answers(text: &str) -> Option<&str> {
    let lower = text.trim().to_lowercase();
    for marker in ["đáp án", "đ/a", "đ.a", "answer", "đúng"] {
        if lower.starts_with(marker) {
            // Slice original ASCII-safe suffix by the normalized marker byte length.
            let after = text.trim().get(marker.len()..)?.trim_start();
            if let Some(value) = after.strip_prefix(':').or_else(|| after.strip_prefix('：')) {
                return Some(value.trim());
            }
        }
    }
    None
}

fn letters_to_indices(text: &str, option_count: usize) -> Vec<usize> {
    let mut set = BTreeSet::new();
    for token in text.split(|c: char| !c.is_ascii_alphabetic()) {
        if token.len() == 1 {
            let index = (token.as_bytes()[0].to_ascii_uppercase() - b'A') as usize;
            if index < option_count {
                set.insert(index);
            }
        }
    }
    set.into_iter().collect()
}

fn save_question(
    questions: &mut Vec<QuizQuestion>,
    prompt: &mut String,
    options: &mut Vec<Option<String>>,
    correct: &mut BTreeSet<usize>,
    active_option: &mut Option<usize>,
) {
    if let Some(last_index) = options.iter().rposition(Option::is_some) {
        let count = last_index + 1;
        if !prompt.trim().is_empty()
            && count >= 2
            && options[..count]
                .iter()
                .all(|item| item.as_ref().is_some_and(|s| !s.trim().is_empty()))
        {
            questions.push(QuizQuestion {
                prompt: prompt.trim().to_string(),
                options: options[..count]
                    .iter()
                    .map(|item| item.as_ref().expect("checked above").trim().to_string())
                    .collect(),
                correct: correct.iter().copied().filter(|index| *index < count).collect(),
            });
        }
    }
    prompt.clear();
    options.clear();
    correct.clear();
    *active_option = None;
}

/// Core question parser from parsed source lines with red formatting metadata.
pub fn parse_source_lines(lines: &[SourceLine]) -> Result<Vec<QuizQuestion>, String> {
    if lines.len() > 100_000 {
        return Err("Tệp quá nhiều dòng".into());
    }
    let mut questions = Vec::new();
    let mut prompt = String::new();
    let mut options: Vec<Option<String>> = Vec::new();
    let mut correct = BTreeSet::new();
    let mut active_option = None;

    for line in lines {
        let text = line.text.trim();
        if text.is_empty() {
            continue;
        }
        if let Some(header) = question_header(text) {
            save_question(
                &mut questions,
                &mut prompt,
                &mut options,
                &mut correct,
                &mut active_option,
            );
            prompt.push_str(header);
            continue;
        }
        if let Some(answer) = explicit_answers(text) {
            correct.extend(letters_to_indices(answer, options.len()));
            continue;
        }
        if let Some((index, content)) = option_prefix(text) {
            if !prompt.is_empty() && index < 8 {
                // Bare letters can also be code variables. Treat them as an
                // answer label only when there is no active answer yet.
                let bare_letter = text.len() == 1;
                if !(bare_letter && active_option.is_some()) {
                    if options.len() <= index {
                        options.resize(index + 1, None);
                    }
                    if options[index].is_none() {
                        options[index] = Some(content.to_string());
                        active_option = Some(index);
                        if line.red {
                            correct.insert(index);
                        }
                        continue;
                    }
                }
            }
        }
        if let Some(index) = active_option {
            if let Some(Some(option)) = options.get_mut(index) {
                if !option.is_empty() {
                    option.push('\n');
                }
                option.push_str(text);
                if line.red {
                    correct.insert(index);
                }
            }
        } else {
            if !prompt.is_empty() {
                prompt.push('\n');
            }
            prompt.push_str(text);
        }
    }
    save_question(
        &mut questions,
        &mut prompt,
        &mut options,
        &mut correct,
        &mut active_option,
    );
    Ok(questions)
}

/// Parse normal/standalone/multiline answer labels, including source documents
/// that list B before A. Keep multiline C++ snippets intact instead of
/// silently discarding the entire question.
pub fn parse_lines(input: &str) -> Result<String, String> {
    let lines: Vec<SourceLine> = serde_json::from_str(input).map_err(|e| e.to_string())?;
    let questions = parse_source_lines(&lines)?;
    serde_json::to_string(&questions).map_err(|e| e.to_string())
}

fn answer_values(value: &Value, count: usize) -> Vec<usize> {
    let mut set = BTreeSet::new();
    let values: Vec<&Value> = match value {
        Value::Array(items) => items.iter().collect(),
        _ => vec![value],
    };
    for answer in values {
        if let Some(n) = answer.as_u64() {
            if (n as usize) < count {
                set.insert(n as usize); // integer answer indexes are 0-based
            }
        } else if let Some(s) = answer.as_str() {
            set.extend(letters_to_indices(s, count));
        }
    }
    set.into_iter().collect()
}

pub fn parse_json_questions(input: &str) -> Result<Vec<QuizQuestion>, String> {
    let data: Value = serde_json::from_str(input).map_err(|e| e.to_string())?;
    let questions = data
        .as_array()
        .or_else(|| data.get("questions").and_then(Value::as_array))
        .ok_or_else(|| "JSON phải là danh sách câu hỏi hoặc chứa 'questions'".to_string())?;
    if questions.len() > 10_000 {
        return Err("Tệp quá nhiều câu hỏi".into());
    }
    let mut result = Vec::new();
    for item in questions {
        let prompt = ["prompt", "question", "text", "title"]
            .iter()
            .find_map(|key| item.get(key).and_then(Value::as_str))
            .unwrap_or("")
            .trim();
        let choices = item
            .get("options")
            .or_else(|| item.get("choices"))
            .and_then(Value::as_array);
        let Some(choices) = choices else { continue };
        let options: Vec<String> = choices
            .iter()
            .map(|option| {
                option
                    .as_str()
                    .or_else(|| option.get("text").and_then(Value::as_str))
                    .unwrap_or("")
                    .trim()
                    .to_string()
            })
            .collect();
        if prompt.is_empty()
            || !(2..=8).contains(&options.len())
            || options.iter().any(String::is_empty)
        {
            continue;
        }
        let mut correct = BTreeSet::new();
        for (index, option) in choices.iter().enumerate() {
            if option.get("correct").and_then(Value::as_bool) == Some(true) {
                correct.insert(index);
            }
        }
        for key in [
            "correct",
            "correct_indices",
            "correctIndices",
            "answers",
            "answer",
            "correctAnswer",
        ] {
            if let Some(answer) = item.get(key) {
                correct.extend(answer_values(answer, options.len()));
            }
        }
        result.push(QuizQuestion {
            prompt: prompt.into(),
            options,
            correct: correct.into_iter().collect(),
        });
    }
    Ok(result)
}

pub fn parse_json(input: &str) -> Result<String, String> {
    let result = parse_json_questions(input)?;
    serde_json::to_string(&result).map_err(|e| e.to_string())
}

// ---------------------------------------------------------------------------
// DOCX ZIP Extraction & XML Parsing
// ---------------------------------------------------------------------------

const MAX_DOC_XML: usize = 20 * 1024 * 1024;

fn decompress_entry(
    compressed: &[u8],
    comp_method: u16,
    _uncomp_size: usize,
) -> Result<Vec<u8>, String> {
    match comp_method {
        0 => {
            if compressed.len() > MAX_DOC_XML {
                return Err("word/document.xml vượt quá giới hạn 20MB".into());
            }
            Ok(compressed.to_vec())
        }
        8 => {
            let decompressed = miniz_oxide::inflate::decompress_to_vec(compressed)
                .map_err(|e| format!("Lỗi giải nén document.xml: {e:?}"))?;
            if decompressed.len() > MAX_DOC_XML {
                return Err("word/document.xml sau giải nén vượt quá 20MB".into());
            }
            Ok(decompressed)
        }
        other => Err(format!("Phương thức nén zip không hỗ trợ: {other}")),
    }
}

fn read_local_zip_entry(
    zip_bytes: &[u8],
    local_offset: usize,
    comp_method: u16,
    comp_size: usize,
    uncomp_size: usize,
) -> Result<Vec<u8>, String> {
    if local_offset + 30 > zip_bytes.len() || &zip_bytes[local_offset..local_offset + 4] != b"PK\x03\x04" {
        return Err("Tiêu đề local zip không hợp lệ".into());
    }
    let name_len = u16::from_le_bytes([zip_bytes[local_offset + 26], zip_bytes[local_offset + 27]]) as usize;
    let extra_len = u16::from_le_bytes([zip_bytes[local_offset + 28], zip_bytes[local_offset + 29]]) as usize;
    let data_start = local_offset + 30 + name_len + extra_len;
    if data_start + comp_size > zip_bytes.len() {
        return Err("Dữ liệu tệp zip bị cắt ngắn".into());
    }
    let compressed_data = &zip_bytes[data_start..data_start + comp_size];
    decompress_entry(compressed_data, comp_method, uncomp_size)
}

fn scan_local_zip_headers(zip_bytes: &[u8]) -> Result<Vec<u8>, String> {
    let mut i = 0;
    while i + 30 <= zip_bytes.len() {
        if &zip_bytes[i..i + 4] == b"PK\x03\x04" {
            let comp_method = u16::from_le_bytes([zip_bytes[i + 8], zip_bytes[i + 9]]);
            let comp_size = u32::from_le_bytes([
                zip_bytes[i + 18],
                zip_bytes[i + 19],
                zip_bytes[i + 20],
                zip_bytes[i + 21],
            ]) as usize;
            let uncomp_size = u32::from_le_bytes([
                zip_bytes[i + 22],
                zip_bytes[i + 23],
                zip_bytes[i + 24],
                zip_bytes[i + 25],
            ]) as usize;
            let name_len = u16::from_le_bytes([zip_bytes[i + 26], zip_bytes[i + 27]]) as usize;
            let extra_len = u16::from_le_bytes([zip_bytes[i + 28], zip_bytes[i + 29]]) as usize;
            let name_start = i + 30;
            if name_start + name_len <= zip_bytes.len() {
                let name = &zip_bytes[name_start..name_start + name_len];
                if name == b"word/document.xml" {
                    let data_start = name_start + name_len + extra_len;
                    if data_start + comp_size <= zip_bytes.len() && comp_size > 0 {
                        let data = &zip_bytes[data_start..data_start + comp_size];
                        return decompress_entry(data, comp_method, uncomp_size);
                    }
                }
            }
            i += 30 + name_len + extra_len + comp_size;
        } else {
            i += 1;
        }
    }
    Err("Không tìm thấy word/document.xml trong DOCX".into())
}

pub fn extract_docx_xml(zip_bytes: &[u8]) -> Result<Vec<u8>, String> {
    if zip_bytes.len() < 22 {
        return Err("Tệp quá nhỏ hoặc không phải DOCX hợp lệ".into());
    }

    // Try finding Central Directory End record (PK\x05\x06) from end
    let max_search = zip_bytes.len().min(65536 + 22);
    let search_start = zip_bytes.len() - max_search;
    let mut eocd_offset = None;
    for i in (search_start..=zip_bytes.len() - 22).rev() {
        if &zip_bytes[i..i + 4] == b"PK\x05\x06" {
            eocd_offset = Some(i);
            break;
        }
    }

    if let Some(pos) = eocd_offset {
        let total_entries =
            u16::from_le_bytes([zip_bytes[pos + 10], zip_bytes[pos + 11]]) as usize;
        let cd_size = u32::from_le_bytes([
            zip_bytes[pos + 12],
            zip_bytes[pos + 13],
            zip_bytes[pos + 14],
            zip_bytes[pos + 15],
        ]) as usize;
        let cd_offset = u32::from_le_bytes([
            zip_bytes[pos + 16],
            zip_bytes[pos + 17],
            zip_bytes[pos + 18],
            zip_bytes[pos + 19],
        ]) as usize;

        if cd_offset + cd_size <= zip_bytes.len() {
            let mut curr = cd_offset;
            for _ in 0..total_entries {
                if curr + 46 > zip_bytes.len() || &zip_bytes[curr..curr + 4] != b"PK\x01\x02" {
                    break;
                }
                let comp_method =
                    u16::from_le_bytes([zip_bytes[curr + 10], zip_bytes[curr + 11]]);
                let comp_size = u32::from_le_bytes([
                    zip_bytes[curr + 20],
                    zip_bytes[curr + 21],
                    zip_bytes[curr + 22],
                    zip_bytes[curr + 23],
                ]) as usize;
                let uncomp_size = u32::from_le_bytes([
                    zip_bytes[curr + 24],
                    zip_bytes[curr + 25],
                    zip_bytes[curr + 26],
                    zip_bytes[curr + 27],
                ]) as usize;
                let name_len =
                    u16::from_le_bytes([zip_bytes[curr + 28], zip_bytes[curr + 29]]) as usize;
                let extra_len =
                    u16::from_le_bytes([zip_bytes[curr + 30], zip_bytes[curr + 31]]) as usize;
                let comment_len =
                    u16::from_le_bytes([zip_bytes[curr + 32], zip_bytes[curr + 33]]) as usize;
                let local_offset = u32::from_le_bytes([
                    zip_bytes[curr + 42],
                    zip_bytes[curr + 43],
                    zip_bytes[curr + 44],
                    zip_bytes[curr + 45],
                ]) as usize;

                let name_start = curr + 46;
                if name_start + name_len <= zip_bytes.len() {
                    let name = &zip_bytes[name_start..name_start + name_len];
                    if name == b"word/document.xml" {
                        return read_local_zip_entry(
                            zip_bytes,
                            local_offset,
                            comp_method,
                            comp_size,
                            uncomp_size,
                        );
                    }
                }
                curr += 46 + name_len + extra_len + comment_len;
            }
        }
    }

    scan_local_zip_headers(zip_bytes)
}

fn is_red(hex: &str) -> bool {
    let hex = hex.trim().trim_start_matches('#');
    if hex.len() != 6 {
        return false;
    }
    let Ok(code) = u32::from_str_radix(hex, 16) else {
        return false;
    };
    let r = (code >> 16) & 255;
    let g = (code >> 8) & 255;
    let b = code & 255;
    r >= 130 && (r as f64) >= (g as f64) * 1.6 && (r as f64) >= (b as f64) * 1.6
}

#[derive(Debug, Clone, Copy)]
struct ColoredRange {
    start: usize,
    end_exclusive: usize,
}

fn add_docx_paragraph(
    output: &mut Vec<SourceLine>,
    original: &str,
    red_runs: &[ColoredRange],
    numbered: bool,
) {
    if original.trim().is_empty() {
        return;
    }
    let prefix = if numbered { "a. " } else { "" };
    let prefix_len = prefix.chars().count();
    let text = format!("{prefix}{original}");
    let chars: Vec<char> = text.chars().collect();
    let text_len = chars.len();

    // Cuts at inline option labels [a-dA-D]\.(?=[\s\u00A0]|$)
    let mut cuts = vec![0];
    for i in 0..text_len {
        let c = chars[i];
        if c.is_ascii_alphabetic() {
            let upper = c.to_ascii_uppercase();
            if ('A'..='D').contains(&upper) && i + 1 < text_len && chars[i + 1] == '.' {
                let is_end_or_space = if i + 2 < text_len {
                    chars[i + 2].is_whitespace() || chars[i + 2] == '\u{00a0}'
                } else {
                    true
                };
                if is_end_or_space {
                    cuts.push(i);
                }
            }
        }
    }
    cuts.push(text_len);
    cuts.sort_unstable();
    cuts.dedup();

    for w in cuts.windows(2) {
        let start = w[0];
        let end = w[1];
        let chunk: String = chars[start..end].iter().collect();
        let content = chunk.trim();
        if content.is_empty() {
            continue;
        }
        let is_marked_red = red_runs.iter().any(|range| {
            range.start + prefix_len < end && range.end_exclusive + prefix_len > start
        });
        output.push(SourceLine {
            text: content.to_string(),
            red: is_marked_red,
        });
    }
}

fn get_attribute_value<'a>(tag_content: &'a str, attr_name: &str) -> Option<&'a str> {
    let mut search = 0;
    while let Some(pos) = tag_content[search..].find(attr_name) {
        let abs_pos = search + pos;
        let before_ok = abs_pos == 0
            || tag_content.as_bytes()[abs_pos - 1].is_ascii_whitespace()
            || tag_content.as_bytes()[abs_pos - 1] == b':';
        let after = &tag_content[abs_pos + attr_name.len()..];
        let after_trimmed = after.trim_start();
        if before_ok && after_trimmed.starts_with('=') {
            let rest = after_trimmed[1..].trim_start();
            if let Some(quote) = rest.chars().next() {
                if quote == '"' || quote == '\'' {
                    let val_start = &rest[quote.len_utf8()..];
                    if let Some(end_quote) = val_start.find(quote) {
                        return Some(&val_start[..end_quote]);
                    }
                }
            }
        }
        search = abs_pos + attr_name.len();
    }
    None
}

fn unescape_xml(input: &str) -> String {
    if !input.contains('&') {
        return input.to_string();
    }
    let mut out = String::with_capacity(input.len());
    let mut chars = input.chars().peekable();
    while let Some(c) = chars.next() {
        if c == '&' {
            let mut entity = String::new();
            let mut closed = false;
            for _ in 0..10 {
                if let Some(&next_c) = chars.peek() {
                    chars.next();
                    if next_c == ';' {
                        closed = true;
                        break;
                    }
                    entity.push(next_c);
                } else {
                    break;
                }
            }
            if closed {
                match entity.as_str() {
                    "lt" => out.push('<'),
                    "gt" => out.push('>'),
                    "amp" => out.push('&'),
                    "quot" => out.push('"'),
                    "apos" => out.push('\''),
                    "nbsp" => out.push('\u{00a0}'),
                    s if s.starts_with("#x") || s.starts_with("#X") => {
                        if let Ok(code) = u32::from_str_radix(&s[2..], 16) {
                            if let Some(ch) = char::from_u32(code) {
                                out.push(ch);
                                continue;
                            }
                        }
                        out.push('&');
                        out.push_str(&entity);
                        out.push(';');
                    }
                    s if s.starts_with('#') => {
                        if let Ok(code) = s[1..].parse::<u32>() {
                            if let Some(ch) = char::from_u32(code) {
                                out.push(ch);
                                continue;
                            }
                        }
                        out.push('&');
                        out.push_str(&entity);
                        out.push(';');
                    }
                    _ => {
                        out.push('&');
                        out.push_str(&entity);
                        out.push(';');
                    }
                }
            } else {
                out.push('&');
                out.push_str(&entity);
            }
        } else {
            out.push(c);
        }
    }
    out
}

pub fn parse_docx_xml(xml: &[u8]) -> Result<Vec<SourceLine>, String> {
    let xml_str = std::str::from_utf8(xml).map_err(|e| format!("Invalid UTF-8 in XML: {e}"))?;
    let mut result = Vec::new();
    let mut paragraph = String::new();
    let mut red_runs = Vec::new();
    let mut numbered = false;
    let mut run_red = false;
    let mut in_text_node = false;

    let mut i = 0;
    let bytes = xml_str.as_bytes();
    let len = bytes.len();

    while i < len {
        if bytes[i] == b'<' {
            if let Some(close_rel) = bytes[i..].iter().position(|&b| b == b'>') {
                let tag_end = i + close_rel;
                let tag_inner = xml_str[i + 1..tag_end].trim();
                let is_closing = tag_inner.starts_with('/');
                let is_self_closing = tag_inner.ends_with('/');
                let tag_body = if is_closing {
                    tag_inner[1..].trim_start()
                } else if is_self_closing {
                    tag_inner[..tag_inner.len() - 1].trim_end()
                } else {
                    tag_inner
                };

                let name = tag_body.split_whitespace().next().unwrap_or("");
                let local_name = name.split(':').next_back().unwrap_or(name);

                if is_closing {
                    match local_name {
                        "p" => {
                            add_docx_paragraph(&mut result, &paragraph, &red_runs, numbered);
                            paragraph.clear();
                            red_runs.clear();
                            numbered = false;
                        }
                        "t" => {
                            in_text_node = false;
                        }
                        _ => {}
                    }
                } else {
                    match local_name {
                        "p" => {
                            paragraph.clear();
                            red_runs.clear();
                            numbered = false;
                        }
                        "numPr" => {
                            numbered = true;
                        }
                        "r" => {
                            run_red = false;
                        }
                        "color" => {
                            if let Some(val) = get_attribute_value(tag_body, "val") {
                                if is_red(val) {
                                    run_red = true;
                                }
                            }
                        }
                        "t" => {
                            in_text_node = true;
                        }
                        "tab" => {
                            paragraph.push(' ');
                        }
                        "br" => {
                            paragraph.push('\n');
                        }
                        _ => {}
                    }
                    if is_self_closing && local_name == "p" {
                        add_docx_paragraph(&mut result, &paragraph, &red_runs, numbered);
                        paragraph.clear();
                        red_runs.clear();
                        numbered = false;
                    }
                }
                i = tag_end + 1;
            } else {
                break;
            }
        } else {
            let text_start = i;
            let next_tag = bytes[i..].iter().position(|&b| b == b'<').unwrap_or(len - i);
            let text_chunk = &xml_str[text_start..text_start + next_tag];
            if in_text_node && !text_chunk.is_empty() {
                let unescaped = unescape_xml(text_chunk);
                let start_char = paragraph.chars().count();
                paragraph.push_str(&unescaped);
                let end_char = paragraph.chars().count();
                if run_red && end_char > start_char {
                    red_runs.push(ColoredRange {
                        start: start_char,
                        end_exclusive: end_char,
                    });
                }
            }
            i = text_start + next_tag;
        }
    }
    Ok(result)
}

pub fn parse_docx_bytes(bytes: &[u8]) -> Result<Vec<QuizQuestion>, String> {
    let xml = extract_docx_xml(bytes)?;
    let lines = parse_docx_xml(&xml)?;
    parse_source_lines(&lines)
}

pub fn extract_pdf_text(pdf_bytes: &[u8]) -> Result<String, String> {
    let doc = lopdf::Document::load_mem(pdf_bytes)
        .map_err(|e| format!("Không thể đọc tệp PDF: {e}"))?;
    if doc.is_encrypted() {
        return Err("PDF có mật khẩu nên không thể đọc".into());
    }
    let pages: Vec<u32> = doc.get_pages().keys().copied().collect();
    if pages.is_empty() {
        return Err("Tệp PDF không có trang nào".into());
    }
    let text = doc.extract_text(&pages)
        .map_err(|e| format!("Lỗi trích xuất văn bản từ PDF: {e}"))?;
    if text.trim().is_empty() {
        return Err("PDF không có lớp văn bản. Bản scan cần OCR và chưa được hỗ trợ.".into());
    }
    Ok(text)
}

pub fn parse_plain_text(text: &str) -> Result<Vec<QuizQuestion>, String> {
    let lines: Vec<SourceLine> = text
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty())
        .map(|l| SourceLine {
            text: l.to_string(),
            red: false,
        })
        .collect();
    parse_source_lines(&lines)
}

pub fn import_quiz(
    file_bytes: &[u8],
    file_ext: &str,
    default_title: &str,
) -> Result<(String, Vec<QuizQuestion>, String), String> {
    let questions = match file_ext.to_lowercase().as_str() {
        "docx" => parse_docx_bytes(file_bytes)?,
        "pdf" => {
            let text = extract_pdf_text(file_bytes)?;
            parse_plain_text(&text)?
        }
        "json" => {
            let s = std::str::from_utf8(file_bytes)
                .map_err(|e| e.to_string())?
                .trim_start_matches('\u{feff}');
            parse_json_questions(s)?
        }
        "txt" => {
            let s = std::str::from_utf8(file_bytes)
                .map_err(|e| e.to_string())?
                .trim_start_matches('\u{feff}');
            parse_plain_text(s)?
        }
        other => return Err(format!("Định dạng tệp không được hỗ trợ: {other}")),
    };

    if questions.is_empty() {
        return Err(
            "Không tìm thấy câu hỏi hợp lệ. Kiểm tra định dạng 'Câu 1:', 'A.', 'B.' và nội dung tệp."
                .into(),
        );
    }

    let missing = questions.iter().filter(|q| q.correct.is_empty()).count();
    let multiple = questions.iter().filter(|q| q.correct.len() > 1).count();
    let mut note = format!("Đã nhận {} câu. ", questions.len());
    if missing > 0 {
        note.push_str(&format!("{missing} câu chưa có đáp án, hãy chọn trước khi lưu. "));
    }
    if multiple > 0 {
        note.push_str(&format!(
            "{multiple} câu có nhiều đáp án được đánh dấu, nên kiểm tra lại. "
        ));
    }
    if file_ext.eq_ignore_ascii_case("pdf") {
        note.push_str("PDF chỉ đọc lớp chữ; chưa tự xác định màu đỏ.");
    }
    let title = default_title.trim();
    let title = if title.is_empty() { "Bộ đề mới" } else { title };
    Ok((title.to_string(), questions, note.trim().to_string()))
}

// ---------------------------------------------------------------------------
// Binary Wire Protocol (TKB1Q & TKB1D)
// ---------------------------------------------------------------------------

pub fn encode_quiz_questions_wire(
    notice: &str,
    questions: &[QuizQuestionRecord],
) -> Result<Vec<u8>, String> {
    let mut out = binary_header(b'Q');
    binary_str(&mut out, notice)?;
    binary_count(&mut out, questions.len())?;
    for q in questions {
        binary_i64(&mut out, q.id);
        binary_str(&mut out, &q.prompt)?;
        binary_count(&mut out, q.options.len())?;
        for opt in &q.options {
            binary_str(&mut out, opt)?;
        }
        binary_count(&mut out, q.correct.len())?;
        for &idx in &q.correct {
            binary_i32(&mut out, idx as i32);
        }
        if let Some(sel) = &q.selected {
            binary_count(&mut out, sel.len())?;
            for &idx in sel {
                binary_i32(&mut out, idx as i32);
            }
        } else {
            binary_i32(&mut out, -1);
        }
        let was_correct_byte = match q.was_correct {
            Some(true) => 1i8,
            Some(false) => 0i8,
            None => -1i8,
        };
        out.push(was_correct_byte as u8);
    }
    Ok(out)
}

pub fn encode_decks_wire(decks: &[QuizDeckRecord]) -> Result<Vec<u8>, String> {
    let mut out = binary_header(b'D');
    binary_count(&mut out, decks.len())?;
    for d in decks {
        binary_i64(&mut out, d.id);
        binary_str(&mut out, &d.title)?;
        binary_i32(&mut out, d.total);
        binary_i32(&mut out, d.attempted);
        binary_i32(&mut out, d.correct);
    }
    Ok(out)
}

fn read_wire_i32(bytes: &[u8], offset: &mut usize) -> Result<i32, String> {
    if *offset + 4 > bytes.len() {
        return Err("Hết dữ liệu khi đọc i32".into());
    }
    let val = i32::from_be_bytes([
        bytes[*offset],
        bytes[*offset + 1],
        bytes[*offset + 2],
        bytes[*offset + 3],
    ]);
    *offset += 4;
    Ok(val)
}

fn read_wire_i64(bytes: &[u8], offset: &mut usize) -> Result<i64, String> {
    if *offset + 8 > bytes.len() {
        return Err("Hết dữ liệu khi đọc i64".into());
    }
    let val = i64::from_be_bytes([
        bytes[*offset],
        bytes[*offset + 1],
        bytes[*offset + 2],
        bytes[*offset + 3],
        bytes[*offset + 4],
        bytes[*offset + 5],
        bytes[*offset + 6],
        bytes[*offset + 7],
    ]);
    *offset += 8;
    Ok(val)
}

fn read_wire_str(bytes: &[u8], offset: &mut usize) -> Result<String, String> {
    let len = read_wire_i32(bytes, offset)? as usize;
    if len > 8 * 1024 * 1024 || *offset + len > bytes.len() {
        return Err("Độ dài chuỗi nhị phân không hợp lệ".into());
    }
    let s = std::str::from_utf8(&bytes[*offset..*offset + len])
        .map_err(|e| e.to_string())?
        .to_string();
    *offset += len;
    Ok(s)
}

pub fn decode_quiz_questions_wire(bytes: &[u8]) -> Result<Vec<QuizQuestion>, String> {
    if bytes.len() < 5 || &bytes[0..5] != b"TKB1Q" {
        return Err("Chữ ký nhị phân không khớp: kỳ vọng TKB1Q".into());
    }
    let mut offset = 5;
    let _notice = read_wire_str(bytes, &mut offset)?;
    let count = read_wire_i32(bytes, &mut offset)? as usize;
    if count > 100_000 {
        return Err("Số lượng câu hỏi trong nhị phân quá lớn".into());
    }
    let mut questions = Vec::with_capacity(count);
    for _ in 0..count {
        let _id = read_wire_i64(bytes, &mut offset)?;
        let prompt = read_wire_str(bytes, &mut offset)?;
        let opt_count = read_wire_i32(bytes, &mut offset)? as usize;
        if opt_count > 20 {
            return Err("Số lựa chọn cho một câu hỏi vượt giới hạn".into());
        }
        let mut options = Vec::with_capacity(opt_count);
        for _ in 0..opt_count {
            options.push(read_wire_str(bytes, &mut offset)?);
        }
        let cor_count = read_wire_i32(bytes, &mut offset)? as usize;
        let mut correct = Vec::with_capacity(cor_count);
        for _ in 0..cor_count {
            let idx = read_wire_i32(bytes, &mut offset)?;
            if idx >= 0 {
                correct.push(idx as usize);
            }
        }
        let sel_count = read_wire_i32(bytes, &mut offset)?;
        if sel_count > 0 {
            for _ in 0..sel_count {
                let _ = read_wire_i32(bytes, &mut offset)?;
            }
        }
        if offset < bytes.len() {
            offset += 1; // was_correct byte
        }
        questions.push(QuizQuestion {
            prompt,
            options,
            correct,
        });
    }
    Ok(questions)
}

// ---------------------------------------------------------------------------
// Rusqlite Database Storage (quiz.db)
// ---------------------------------------------------------------------------

pub struct QuizDatabase {
    conn: Connection,
}

impl QuizDatabase {
    pub fn open(path: impl AsRef<Path>) -> SqlResult<Self> {
        let conn = Connection::open(path)?;
        conn.busy_timeout(Duration::from_secs(5))?;
        conn.pragma_update(None, "foreign_keys", "ON")?;
        conn.pragma_update(None, "journal_mode", "WAL")?;
        conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS quiz_decks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                created_ms INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS quiz_questions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deck_id INTEGER NOT NULL REFERENCES quiz_decks(id) ON DELETE CASCADE,
                position INTEGER NOT NULL,
                prompt TEXT NOT NULL,
                options_json TEXT NOT NULL,
                correct_json TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_quiz_deck_order ON quiz_questions(deck_id, position);
            CREATE TABLE IF NOT EXISTS quiz_progress (
                question_id INTEGER PRIMARY KEY REFERENCES quiz_questions(id) ON DELETE CASCADE,
                selected_json TEXT NOT NULL,
                is_correct INTEGER NOT NULL,
                updated_ms INTEGER NOT NULL
            );",
        )?;
        Ok(Self { conn })
    }

    pub fn in_memory() -> SqlResult<Self> {
        let conn = Connection::open_in_memory()?;
        conn.pragma_update(None, "foreign_keys", "ON")?;
        conn.execute_batch(
            "CREATE TABLE IF NOT EXISTS quiz_decks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                created_ms INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS quiz_questions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deck_id INTEGER NOT NULL REFERENCES quiz_decks(id) ON DELETE CASCADE,
                position INTEGER NOT NULL,
                prompt TEXT NOT NULL,
                options_json TEXT NOT NULL,
                correct_json TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_quiz_deck_order ON quiz_questions(deck_id, position);
            CREATE TABLE IF NOT EXISTS quiz_progress (
                question_id INTEGER PRIMARY KEY REFERENCES quiz_questions(id) ON DELETE CASCADE,
                selected_json TEXT NOT NULL,
                is_correct INTEGER NOT NULL,
                updated_ms INTEGER NOT NULL
            );",
        )?;
        Ok(Self { conn })
    }

    pub fn list_decks(&self) -> SqlResult<Vec<QuizDeckRecord>> {
        let mut stmt = self.conn.prepare(
            "SELECT d.id, d.title, COUNT(q.id),
                COUNT(p.question_id), COALESCE(SUM(p.is_correct), 0)
                FROM quiz_decks d
                LEFT JOIN quiz_questions q ON q.deck_id=d.id
                LEFT JOIN quiz_progress p ON p.question_id=q.id
                GROUP BY d.id ORDER BY d.created_ms DESC, d.id DESC",
        )?;
        let rows = stmt.query_map([], |row| {
            Ok(QuizDeckRecord {
                id: row.get(0)?,
                title: row.get(1)?,
                total: row.get(2)?,
                attempted: row.get(3)?,
                correct: row.get(4)?,
            })
        })?;
        let mut decks = Vec::new();
        for r in rows {
            decks.push(r?);
        }
        Ok(decks)
    }

    pub fn load_questions(
        &self,
        deck_id: i64,
        wrong_only: bool,
    ) -> SqlResult<Vec<QuizQuestionRecord>> {
        let sql = if wrong_only {
            "SELECT q.id, q.prompt, q.options_json, q.correct_json,
                p.selected_json, p.is_correct
                FROM quiz_questions q
                LEFT JOIN quiz_progress p ON p.question_id=q.id
                WHERE q.deck_id=? AND p.is_correct=0 ORDER BY q.position ASC"
        } else {
            "SELECT q.id, q.prompt, q.options_json, q.correct_json,
                p.selected_json, p.is_correct
                FROM quiz_questions q
                LEFT JOIN quiz_progress p ON p.question_id=q.id
                WHERE q.deck_id=? ORDER BY q.position ASC"
        };
        let mut stmt = self.conn.prepare(sql)?;
        let rows = stmt.query_map(params![deck_id], |row| {
            let id: i64 = row.get(0)?;
            let prompt: String = row.get(1)?;
            let options_json: String = row.get(2)?;
            let correct_json: String = row.get(3)?;
            let selected_json: Option<String> = row.get(4)?;
            let is_correct: Option<i32> = row.get(5)?;
            Ok((
                id,
                prompt,
                options_json,
                correct_json,
                selected_json,
                is_correct,
            ))
        })?;
        let mut questions = Vec::new();
        for r in rows {
            let (id, prompt, options_json, correct_json, selected_json, is_correct) = r?;
            let options: Vec<String> = serde_json::from_str(&options_json).unwrap_or_default();
            let correct: Vec<usize> = serde_json::from_str(&correct_json).unwrap_or_default();
            let selected: Option<Vec<usize>> =
                selected_json.and_then(|s| serde_json::from_str(&s).ok());
            let was_correct = is_correct.map(|v| v == 1);
            questions.push(QuizQuestionRecord {
                id,
                prompt,
                options,
                correct,
                selected,
                was_correct,
            });
        }
        Ok(questions)
    }

    pub fn save_deck(&mut self, title: &str, questions: &[QuizQuestion]) -> SqlResult<i64> {
        if questions.is_empty() {
            return Err(rusqlite::Error::QueryReturnedNoRows);
        }
        let tx = self.conn.transaction()?;
        let name = title.trim();
        let name = if name.is_empty() { "Bộ đề mới" } else { name };
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as i64;
        tx.execute(
            "INSERT INTO quiz_decks(title, created_ms) VALUES (?, ?)",
            params![name, now],
        )?;
        let deck_id = tx.last_insert_rowid();
        {
            let mut stmt = tx.prepare(
                "INSERT INTO quiz_questions(deck_id, position, prompt, options_json, correct_json)
                 VALUES (?, ?, ?, ?, ?)",
            )?;
            for (idx, q) in questions.iter().enumerate() {
                let options_json = serde_json::to_string(&q.options).unwrap_or_default();
                let correct_json = serde_json::to_string(&q.correct).unwrap_or_default();
                stmt.execute(params![
                    deck_id,
                    idx as i64,
                    q.prompt,
                    options_json,
                    correct_json
                ])?;
            }
        }
        tx.commit()?;
        Ok(deck_id)
    }

    pub fn save_answer(&mut self, question_id: i64, selected: &[usize]) -> SqlResult<bool> {
        let correct_json: String = self.conn.query_row(
            "SELECT correct_json FROM quiz_questions WHERE id = ?",
            params![question_id],
            |row| row.get(0),
        )?;
        let mut correct: Vec<usize> = serde_json::from_str(&correct_json).unwrap_or_default();
        correct.sort_unstable();
        correct.dedup();
        let mut sel: Vec<usize> = selected.to_vec();
        sel.sort_unstable();
        sel.dedup();
        let is_correct = !correct.is_empty() && correct == sel;
        let selected_json = serde_json::to_string(&sel).unwrap_or_default();
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as i64;
        self.conn.execute(
            "INSERT OR REPLACE INTO quiz_progress (question_id, selected_json, is_correct, updated_ms)
             VALUES (?, ?, ?, ?)",
            params![question_id, selected_json, if is_correct { 1 } else { 0 }, now],
        )?;
        Ok(is_correct)
    }

    pub fn delete_deck(&mut self, deck_id: i64) -> SqlResult<()> {
        self.conn
            .execute("DELETE FROM quiz_decks WHERE id = ?", params![deck_id])?;
        Ok(())
    }
}

// ---------------------------------------------------------------------------
// JNI Wire Bridge Handlers
// ---------------------------------------------------------------------------

pub fn import_quiz_wire(
    file_bytes: &[u8],
    file_ext: &str,
    default_title: &str,
) -> Result<Vec<u8>, String> {
    let (_title, questions, notice) = import_quiz(file_bytes, file_ext, default_title)?;
    let records: Vec<QuizQuestionRecord> = questions
        .into_iter()
        .map(|q| QuizQuestionRecord {
            id: 0,
            prompt: q.prompt,
            options: q.options,
            correct: q.correct,
            selected: None,
            was_correct: None,
        })
        .collect();
    encode_quiz_questions_wire(&notice, &records)
}

pub fn list_decks_wire(db_path: &str) -> Result<Vec<u8>, String> {
    let db = QuizDatabase::open(db_path).map_err(|e| format!("Lỗi mở quiz.db: {e}"))?;
    let decks = db
        .list_decks()
        .map_err(|e| format!("Lỗi đọc danh sách bộ đề: {e}"))?;
    encode_decks_wire(&decks)
}

pub fn load_questions_wire(
    db_path: &str,
    deck_id: i64,
    wrong_only: bool,
) -> Result<Vec<u8>, String> {
    let db = QuizDatabase::open(db_path).map_err(|e| format!("Lỗi mở quiz.db: {e}"))?;
    let questions = db
        .load_questions(deck_id, wrong_only)
        .map_err(|e| format!("Lỗi đọc câu hỏi: {e}"))?;
    encode_quiz_questions_wire("", &questions)
}

pub fn save_deck_wire(db_path: &str, title: &str, questions_bytes: &[u8]) -> Result<i64, String> {
    let questions = decode_quiz_questions_wire(questions_bytes)?;
    let mut db = QuizDatabase::open(db_path).map_err(|e| format!("Lỗi mở quiz.db: {e}"))?;
    db.save_deck(title, &questions)
        .map_err(|e| format!("Lỗi lưu bộ đề: {e}"))
}

pub fn save_answer_wire(
    db_path: &str,
    question_id: i64,
    selected: &[usize],
) -> Result<bool, String> {
    let mut db = QuizDatabase::open(db_path).map_err(|e| format!("Lỗi mở quiz.db: {e}"))?;
    db.save_answer(question_id, selected)
        .map_err(|e| format!("Lỗi lưu đáp án: {e}"))
}

pub fn delete_deck_wire(db_path: &str, deck_id: i64) -> Result<(), String> {
    let mut db = QuizDatabase::open(db_path).map_err(|e| format!("Lỗi mở quiz.db: {e}"))?;
    db.delete_deck(deck_id)
        .map_err(|e| format!("Lỗi xóa bộ đề: {e}"))
}

// ---------------------------------------------------------------------------
// Unit Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detects_red_and_explicit_answers() {
        let input = r#"[
            {"text":"Câu 1: Ngôn ngữ?"},
            {"text":"A. Java"},
            {"text":"B. Kotlin","red":true},
            {"text":"C. Python"},
            {"text":"Câu 2: Chọn hai"},
            {"text":"A) X"},
            {"text":"B) Y"},
            {"text":"C) Z"},
            {"text":"Đáp án: A, C"}
        ]"#;
        let parsed: Vec<QuizQuestion> =
            serde_json::from_str(&parse_lines(input).unwrap()).unwrap();
        assert_eq!(parsed.len(), 2);
        assert_eq!(parsed[0].correct, vec![1]);
        assert_eq!(parsed[1].correct, vec![0, 2]);
    }

    #[test]
    fn preserves_multiline_cpp_and_standalone_choice_labels() {
        let input = r#"[
          {"text":"Câu 88: Điều kiện nào đúng?"},
          {"text":"a."},
          {"text":"if (n / sqrt(n) == 0)"},
          {"text":"b."},
          {"text":"if (n == sqrt(n))"},
          {"text":"c."},
          {"text":"if (n % 2 == 0)"},
          {"text":"d."},
          {"text":"if (sqrt(n) == (int)sqrt(n))","red":true},
          {"text":"491. Thuật toán tính tổng nào đúng?"},
          {"text":"a."},
          {"text":"int total = 0;"},
          {"text":"b."},
          {"text":"int total = 1;","red":true},
          {"text":"Câu 493 Thuật toán hiển thị nào đúng?"},
          {"text":"a"},
          {"text":"cout << 1;"},
          {"text":"b."},
          {"text":"cout << 2;","red":true}
        ]"#;
        let parsed: Vec<QuizQuestion> =
            serde_json::from_str(&parse_lines(input).unwrap()).unwrap();
        assert_eq!(parsed.len(), 3);
        assert_eq!(parsed[0].correct, vec![3]);
        assert_eq!(parsed[0].options[3], "if (sqrt(n) == (int)sqrt(n))");
        assert_eq!(parsed[1].correct, vec![1]);
        assert_eq!(parsed[2].correct, vec![1]);
    }

    #[test]
    fn handles_out_of_order_choices_and_multiline_code() {
        let input = r#"[
            {"text":"Câu 497. Đếm số lượng?"},
            {"text":"b."},
            {"text":"int dem = 1;"},
            {"text":"for(int i=0; i<8; i++)"},
            {"text":"a."},
            {"text":"int dem = 0;"},
            {"text":"c.","red":true},
            {"text":"int dem=0;","red":true},
            {"text":"for(int i=0; i<8; i++)","red":true},
            {"text":"d."},
            {"text":"int dem=-1;"}
        ]"#;
        let parsed: Vec<QuizQuestion> =
            serde_json::from_str(&parse_lines(input).unwrap()).unwrap();
        assert_eq!(parsed.len(), 1);
        assert_eq!(parsed[0].options.len(), 4);
        assert_eq!(parsed[0].correct, vec![2]);
        assert!(parsed[0].options[1].contains("for(int i=0; i<8; i++)"));
    }

    #[test]
    fn json_import_and_unanswered() {
        let input = r#"{"questions":[
          {"question":"Q","options":["Yes","No"],"answer":"B"},
          {"prompt":"No key","choices":["A","B"]}
        ]}"#;
        let parsed: Vec<QuizQuestion> =
            serde_json::from_str(&parse_json(input).unwrap()).unwrap();
        assert_eq!(parsed.len(), 2);
        assert_eq!(parsed[0].correct, vec![1]);
        assert!(parsed[1].correct.is_empty());
    }

    #[test]
    fn docx_xml_parsing_splits_inline_options_and_preserves_red() {
        let xml = r#"<?xml version="1.0" encoding="UTF-8"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body>
                <w:p>
                    <w:r><w:t>Câu 1: Thủ đô của Việt Nam là gì?</w:t></w:r>
                </w:p>
                <w:p>
                    <w:r><w:t>A. Đà Nẵng  B. Hà Nội</w:t></w:r>
                    <w:r>
                        <w:rPr><w:color w:val="FF0000"/></w:rPr>
                        <w:t> (Thủ đô)</w:t>
                    </w:r>
                    <w:r><w:t>  C. TP.HCM  D. Hải Phòng</w:t></w:r>
                </w:p>
                <w:p>
                    <w:pPr><w:numPr/></w:pPr>
                    <w:r><w:t>Lựa chọn tự động numbering</w:t></w:r>
                </w:p>
            </w:body>
        </w:document>"#;
        let lines = parse_docx_xml(xml.as_bytes()).unwrap();
        assert!(!lines.is_empty());
        assert_eq!(lines[0].text, "Câu 1: Thủ đô của Việt Nam là gì?");
        assert!(!lines[0].red);
        // Choice B should be marked red because of the red run, while A and C are not
        let b_opt = lines.iter().find(|l| l.text.starts_with("B."));
        assert!(b_opt.is_some());
        assert!(b_opt.unwrap().red);

        let a_opt = lines.iter().find(|l| l.text.starts_with("A."));
        assert!(a_opt.is_some());
        assert!(!a_opt.unwrap().red);

        // numPr paragraph gets prefixed with "a. "
        let num_line = lines.iter().find(|l| l.text.contains("Lựa chọn tự động numbering"));
        assert!(num_line.is_some());
        assert!(num_line.unwrap().text.starts_with("a. "));
    }

    #[test]
    fn docx_zip_extraction_and_import() {
        // Build a minimal valid in-memory ZIP containing word/document.xml
        let xml = r#"<w:p><w:r><w:t>Câu 10: 1 + 1 = ?</w:t></w:r></w:p>
                     <w:p><w:r><w:t>A. 1</w:t></w:r></w:p>
                     <w:p><w:r><w:rPr><w:color w:val="FF0000"/></w:rPr><w:t>B. 2</w:t></w:r></w:p>"#;
        let compressed = miniz_oxide::deflate::compress_to_vec(xml.as_bytes(), 6);
        let mut zip = Vec::new();
        // Local header PK\x03\x04
        zip.extend_from_slice(b"PK\x03\x04");
        zip.extend_from_slice(&20u16.to_le_bytes()); // version
        zip.extend_from_slice(&0u16.to_le_bytes()); // flags
        zip.extend_from_slice(&8u16.to_le_bytes()); // comp = deflate
        zip.extend_from_slice(&0u16.to_le_bytes()); // mod time
        zip.extend_from_slice(&0u16.to_le_bytes()); // mod date
        zip.extend_from_slice(&0u32.to_le_bytes()); // crc32
        zip.extend_from_slice(&(compressed.len() as u32).to_le_bytes());
        zip.extend_from_slice(&(xml.len() as u32).to_le_bytes());
        let filename = b"word/document.xml";
        zip.extend_from_slice(&(filename.len() as u16).to_le_bytes());
        zip.extend_from_slice(&0u16.to_le_bytes()); // extra len
        zip.extend_from_slice(filename);
        zip.extend_from_slice(&compressed);

        // Central directory PK\x01\x02
        let cd_offset = zip.len();
        zip.extend_from_slice(b"PK\x01\x02");
        zip.extend_from_slice(&20u16.to_le_bytes()); // version made by
        zip.extend_from_slice(&20u16.to_le_bytes()); // version needed
        zip.extend_from_slice(&0u16.to_le_bytes()); // flags
        zip.extend_from_slice(&8u16.to_le_bytes()); // deflate
        zip.extend_from_slice(&0u16.to_le_bytes()); // mod time
        zip.extend_from_slice(&0u16.to_le_bytes()); // mod date
        zip.extend_from_slice(&0u32.to_le_bytes()); // crc32
        zip.extend_from_slice(&(compressed.len() as u32).to_le_bytes());
        zip.extend_from_slice(&(xml.len() as u32).to_le_bytes());
        zip.extend_from_slice(&(filename.len() as u16).to_le_bytes());
        zip.extend_from_slice(&0u16.to_le_bytes()); // extra
        zip.extend_from_slice(&0u16.to_le_bytes()); // comment
        zip.extend_from_slice(&0u16.to_le_bytes()); // disk #
        zip.extend_from_slice(&0u16.to_le_bytes()); // internal attr
        zip.extend_from_slice(&0u32.to_le_bytes()); // external attr
        zip.extend_from_slice(&0u32.to_le_bytes()); // local header offset = 0
        zip.extend_from_slice(filename);
        let cd_size = zip.len() - cd_offset;

        // EOCD PK\x05\x06
        zip.extend_from_slice(b"PK\x05\x06");
        zip.extend_from_slice(&0u16.to_le_bytes()); // disk #
        zip.extend_from_slice(&0u16.to_le_bytes()); // cd disk
        zip.extend_from_slice(&1u16.to_le_bytes()); // entries disk
        zip.extend_from_slice(&1u16.to_le_bytes()); // total entries
        zip.extend_from_slice(&(cd_size as u32).to_le_bytes());
        zip.extend_from_slice(&(cd_offset as u32).to_le_bytes());
        zip.extend_from_slice(&0u16.to_le_bytes()); // comment len

        let (title, questions, notice) = import_quiz(&zip, "docx", "Đề toán").unwrap();
        assert_eq!(title, "Đề toán");
        assert_eq!(questions.len(), 1);
        assert_eq!(questions[0].correct, vec![1]);
        assert!(notice.contains("Đã nhận 1 câu."));
    }

    #[test]
    fn rusqlite_quiz_crud_and_wire_roundtrip() {
        let mut db = QuizDatabase::in_memory().unwrap();
        assert!(db.list_decks().unwrap().is_empty());

        let questions = vec![
            QuizQuestion {
                prompt: "Câu 1?".into(),
                options: vec!["A".into(), "B".into()],
                correct: vec![1],
            },
            QuizQuestion {
                prompt: "Câu 2?".into(),
                options: vec!["X".into(), "Y".into()],
                correct: vec![0, 1],
            },
        ];
        let deck_id = db.save_deck("Bộ đề kiểm tra", &questions).unwrap();
        assert!(deck_id > 0);

        let decks = db.list_decks().unwrap();
        assert_eq!(decks.len(), 1);
        assert_eq!(decks[0].title, "Bộ đề kiểm tra");
        assert_eq!(decks[0].total, 2);
        assert_eq!(decks[0].attempted, 0);

        let loaded = db.load_questions(deck_id, false).unwrap();
        assert_eq!(loaded.len(), 2);
        assert_eq!(loaded[0].prompt, "Câu 1?");
        assert_eq!(loaded[0].options, vec!["A", "B"]);
        assert_eq!(loaded[0].correct, vec![1]);
        assert_eq!(loaded[0].selected, None);
        assert_eq!(loaded[0].was_correct, None);

        // Answer Question 1 correctly
        let correct = db.save_answer(loaded[0].id, &[1]).unwrap();
        assert!(correct);

        // Answer Question 2 incorrectly
        let incorrect = db.save_answer(loaded[1].id, &[0]).unwrap();
        assert!(!incorrect);

        let decks_after = db.list_decks().unwrap();
        assert_eq!(decks_after[0].attempted, 2);
        assert_eq!(decks_after[0].correct, 1);

        // Load wrong only
        let wrong = db.load_questions(deck_id, true).unwrap();
        assert_eq!(wrong.len(), 1);
        assert_eq!(wrong[0].id, loaded[1].id);

        // Test wire roundtrip
        let wire_decks = encode_decks_wire(&decks_after).unwrap();
        assert!(&wire_decks[0..5] == b"TKB1D");

        let wire_questions = encode_quiz_questions_wire("Thành công", &loaded).unwrap();
        assert!(&wire_questions[0..5] == b"TKB1Q");

        let decoded = decode_quiz_questions_wire(&wire_questions).unwrap();
        assert_eq!(decoded.len(), 2);
        assert_eq!(decoded[0].prompt, "Câu 1?");
        assert_eq!(decoded[1].correct, vec![0, 1]);

        // Delete deck
        db.delete_deck(deck_id).unwrap();
        assert!(db.list_decks().unwrap().is_empty());
        assert!(db.load_questions(deck_id, false).unwrap().is_empty());
    }

    #[test]
    fn pdf_import_error_handling() {
        let invalid = b"%PDF-1.4 corrupt data";
        assert!(extract_pdf_text(invalid).is_err());
        assert!(import_quiz(invalid, "pdf", "Test").is_err());
    }
}
