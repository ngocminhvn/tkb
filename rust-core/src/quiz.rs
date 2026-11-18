//! Offline multiple-choice import parsing. No network, new dependencies or timetable DB access.
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::BTreeSet;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct QuizQuestion {
    pub prompt: String,
    pub options: Vec<String>,
    pub correct: Vec<usize>, // 0-based; may be empty for manual review
}

#[derive(Deserialize)]
struct SourceLine {
    text: String,
    #[serde(default)]
    red: bool,
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

/// Parse normal/standalone/multiline answer labels, including source documents
/// that list B before A. Keep multiline C++ snippets intact instead of
/// silently discarding the entire question.
pub fn parse_lines(input: &str) -> Result<String, String> {
    let lines: Vec<SourceLine> = serde_json::from_str(input).map_err(|e| e.to_string())?;
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

pub fn parse_json(input: &str) -> Result<String, String> {
    let data: Value = serde_json::from_str(input).map_err(|e| e.to_string())?;
    let questions = data.as_array().or_else(|| data.get("questions").and_then(Value::as_array))
        .ok_or_else(|| "JSON phải là danh sách câu hỏi hoặc chứa 'questions'".to_string())?;
    if questions.len() > 10_000 {
        return Err("Tệp quá nhiều câu hỏi".into());
    }
    let mut result = Vec::new();
    for item in questions {
        let prompt = ["prompt", "question", "text", "title"]
            .iter().find_map(|key| item.get(key).and_then(Value::as_str)).unwrap_or("").trim();
        let choices = item.get("options").or_else(|| item.get("choices"))
            .and_then(Value::as_array);
        let Some(choices) = choices else { continue };
        let options: Vec<String> = choices.iter().map(|option| {
            option.as_str().or_else(|| option.get("text").and_then(Value::as_str))
                .unwrap_or("").trim().to_string()
        }).collect();
        if prompt.is_empty() || !(2..=8).contains(&options.len()) || options.iter().any(String::is_empty) {
            continue;
        }
        let mut correct = BTreeSet::new();
        for (index, option) in choices.iter().enumerate() {
            if option.get("correct").and_then(Value::as_bool) == Some(true) {
                correct.insert(index);
            }
        }
        for key in ["correct", "correct_indices", "correctIndices", "answers", "answer", "correctAnswer"] {
            if let Some(answer) = item.get(key) {
                correct.extend(answer_values(answer, options.len()));
            }
        }
        result.push(QuizQuestion { prompt: prompt.into(), options, correct: correct.into_iter().collect() });
    }
    serde_json::to_string(&result).map_err(|e| e.to_string())
}

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
        let parsed: Vec<QuizQuestion> = serde_json::from_str(&parse_lines(input).unwrap()).unwrap();
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
        let parsed: Vec<QuizQuestion> = serde_json::from_str(&parse_lines(input).unwrap()).unwrap();
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
        let parsed: Vec<QuizQuestion> = serde_json::from_str(&parse_lines(input).unwrap()).unwrap();
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
        let parsed: Vec<QuizQuestion> = serde_json::from_str(&parse_json(input).unwrap()).unwrap();
        assert_eq!(parsed.len(), 2);
        assert_eq!(parsed[0].correct, vec![1]);
        assert!(parsed[1].correct.is_empty());
    }
}
