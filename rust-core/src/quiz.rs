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
    if bytes.len() < 3 || !bytes[0].is_ascii_alphabetic() {
        return None;
    }
    let index = bytes[0].to_ascii_uppercase().wrapping_sub(b'A') as usize;
    if index >= 8 || !matches!(bytes[1], b'.' | b')' | b':' | b'-') {
        return None;
    }
    let rest = text[2..].trim();
    (!rest.is_empty()).then_some((index, rest))
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
            return Some(tail[digits..].trim_start_matches(|c: char| matches!(c, '.' | ':' | ')' | '-' | ' ')).trim());
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
    options: &mut Vec<String>,
    correct: &mut BTreeSet<usize>,
) {
    if !prompt.trim().is_empty() && options.len() >= 2 && options.iter().all(|x| !x.trim().is_empty()) {
        questions.push(QuizQuestion {
            prompt: prompt.trim().to_string(),
            options: std::mem::take(options),
            correct: correct.iter().copied().filter(|i| *i < 8).collect(),
        });
    }
    prompt.clear();
    options.clear();
    correct.clear();
}

pub fn parse_lines(input: &str) -> Result<String, String> {
    let lines: Vec<SourceLine> = serde_json::from_str(input).map_err(|e| e.to_string())?;
    if lines.len() > 100_000 {
        return Err("Tệp quá nhiều dòng".into());
    }
    let mut questions = Vec::new();
    let (mut prompt, mut options, mut correct) = (String::new(), Vec::new(), BTreeSet::new());
    for line in lines {
        let text = line.text.trim();
        if text.is_empty() {
            continue;
        }
        if let Some(header) = question_header(text) {
            save_question(&mut questions, &mut prompt, &mut options, &mut correct);
            prompt.push_str(header);
        } else if let Some((index, content)) = option_prefix(text) {
            if prompt.is_empty() || index != options.len() {
                // A repeated or out-of-order option should not silently be used as an answer.
                continue;
            }
            options.push(content.to_owned());
            if line.red {
                correct.insert(index);
            }
        } else if let Some(answer) = explicit_answers(text) {
            correct.extend(letters_to_indices(answer, options.len()));
        } else if options.is_empty() {
            if !prompt.is_empty() {
                prompt.push('\n');
            }
            prompt.push_str(text);
        } else if let Some(last) = options.last_mut() {
            last.push(' ');
            last.push_str(text);
            if line.red {
                correct.insert(options.len() - 1);
            }
        }
    }
    save_question(&mut questions, &mut prompt, &mut options, &mut correct);
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
