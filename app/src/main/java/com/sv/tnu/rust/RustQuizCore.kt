package com.sv.tnu.rust

/**
 * Single JNI call per quiz import. The parser is pure Rust and needs no network.
 * JSON result uses 0-based option indexes for correct answers.
 */
object RustQuizCore {
    init {
        System.loadLibrary("tkb_core")
    }

    private external fun parseLinesNative(linesJson: String): String
    private external fun parseJsonNative(sourceJson: String): String

    fun parseLines(linesJson: String): String = parseLinesNative(linesJson)
    fun parseJson(sourceJson: String): String = parseJsonNative(sourceJson)
}
