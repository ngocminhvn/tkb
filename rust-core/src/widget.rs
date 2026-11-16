//! Widget sizing rules in dp and sp.
#[derive(Clone, Copy)]
#[repr(i32)]
pub enum Layout {
    Square,
    Medium,
    Wide,
}

impl TryFrom<i32> for Layout {
    type Error = ();

    fn try_from(value: i32) -> Result<Self, Self::Error> {
        match value {
            0 => Ok(Self::Square),
            1 => Ok(Self::Medium),
            2 => Ok(Self::Wide),
            _ => Err(()),
        }
    }
}

pub fn layout(width: i32, min_height: i32) -> Layout {
    if width <= 220 || width <= min_height.saturating_add(32) {
        Layout::Square
    } else if width < 320 {
        Layout::Medium
    } else {
        Layout::Wide
    }
}

pub fn density(count: i32, height: i32, layout: Layout) -> [i32; 4] {
    if count <= 0 {
        return [12, 11, 8, 4];
    }
    let (header, base_subject, normal_padding, compact_padding, compact_count) = match layout {
        Layout::Square => (48, 12, 8, 5, 4),
        Layout::Medium => (52, 12, 10, 7, 4),
        Layout::Wide => (52, 14, 12, 8, 5),
    };
    let per_lesson = height.saturating_sub(header).max(40) / count;
    let count_reduction = match count {
        ..=2 => 0,
        3 => 1,
        4 => 2,
        _ => 4,
    };
    let height_reduction = match per_lesson {
        48.. => 0,
        36..=47 => 1,
        28..=35 => 2,
        _ => 4,
    };
    let subject = (base_subject - count_reduction.max(height_reduction)).max(7);
    let vertical = match per_lesson {
        52.. if count <= 2 => 6,
        38.. => 4,
        28..=37 => 2,
        _ => 0,
    };
    let horizontal = if count >= compact_count {
        compact_padding
    } else {
        normal_padding
    };
    [subject, (subject - 1).max(7), horizontal, vertical]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_width_thresholds_and_lesson_density() {
        assert!(matches!(layout(280, 248), Layout::Square));
        assert!(matches!(layout(319, 110), Layout::Medium));
        assert!(matches!(layout(320, 110), Layout::Wide));
        assert_eq!(density(0, 110, Layout::Wide), [12, 11, 8, 4]);
        assert_eq!(density(2, 156, Layout::Wide), [14, 13, 12, 6]);
        assert_eq!(density(4, 160, Layout::Square), [10, 9, 5, 2]);
        assert_eq!(density(5, 110, Layout::Wide), [10, 9, 8, 0]);
    }
}
