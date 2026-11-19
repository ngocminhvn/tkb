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
