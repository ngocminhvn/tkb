//! Verify and unpack a downloaded package in app-private storage.
use lzma_rust2::XzReader;
use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::{self, BufReader, BufWriter, Read, Seek, Write};
use std::path::Path;

const BUFFER_SIZE: usize = 64 * 1024;
// Release CI uses xz -9, which needs a 64 MiB dictionary.
const XZ_LIMIT_KIB: u32 = 96 * 1024;

fn verified(input: &mut impl Read, expected: &str) -> io::Result<bool> {
    let mut hash = Sha256::new();
    let mut buffer = vec![0; BUFFER_SIZE];
    loop {
        let count = input.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        hash.update(&buffer[..count]);
    }
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let digest = hash.finalize();
    let mut ascii = [0; 64];
    for (byte, pair) in digest.iter().zip(ascii.as_chunks_mut::<2>().0) {
        pair[0] = HEX[usize::from(byte >> 4)];
        pair[1] = HEX[usize::from(byte & 0xf)];
    }
    Ok(ascii.eq_ignore_ascii_case(expected.as_bytes()))
}

pub fn unpack(
    package: &Path,
    destination: &Path,
    checksum: &str,
    compressed: bool,
) -> io::Result<bool> {
    let mut input = BufReader::with_capacity(BUFFER_SIZE, File::open(package)?);
    if !verified(&mut input, checksum)? {
        return Ok(false);
    }
    input.rewind()?;
    let mut output = BufWriter::with_capacity(BUFFER_SIZE, File::create(destination)?);
    if compressed {
        let mut decoder = XzReader::new_mem_limit(input, true, XZ_LIMIT_KIB);
        io::copy(&mut decoder, &mut output)?;
    } else {
        io::copy(&mut input, &mut output)?;
    }
    output.flush()?;
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn verifies_before_creating_apk_and_unpacks_release_xz() {
        // Produced with xz -9, the preset used in the release workflow.
        const XZ: &[u8] = &[
            253, 55, 122, 88, 90, 0, 0, 4, 230, 214, 180, 70, 4, 192, 21, 17, 33, 1, 28, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 123, 136, 254, 72, 1, 0, 16, 116, 101, 115, 116, 32, 65, 80, 75, 32,
            99, 111, 110, 116, 101, 110, 116, 10, 0, 0, 0, 0, 127, 84, 140, 154, 235, 80, 11, 155,
            0, 1, 49, 17, 107, 146, 107, 140, 31, 182, 243, 125, 1, 0, 0, 0, 0, 4, 89, 90,
        ];
        const PLAIN: &[u8] = b"test APK content\n";
        let stamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let dir = std::env::temp_dir().join(format!("tkb-update-{}-{stamp}", std::process::id()));
        std::fs::create_dir(&dir).unwrap();
        let package = dir.join("app.part");
        let apk = dir.join("app.apk");
        std::fs::write(&package, XZ).unwrap();
        assert!(!unpack(&package, &apk, &"0".repeat(64), true).unwrap());
        assert!(!apk.exists());
        assert!(unpack(
            &package,
            &apk,
            "f6ec3655e0cf82b16d530ac872d09dbc8a306ce2220bd46b3d121b0d6fc97f0b",
            true,
        )
        .unwrap());
        assert_eq!(std::fs::read(&apk).unwrap(), PLAIN);
        std::fs::write(&package, PLAIN).unwrap();
        assert!(unpack(
            &package,
            &apk,
            "40d5b701ecadeee373b0601354e3b8769efd95f1bfd08aa5fd328369f7a18c4b",
            false,
        )
        .unwrap());
        assert_eq!(std::fs::read(&apk).unwrap(), PLAIN);
        std::fs::remove_dir_all(&dir).unwrap();
    }
}
