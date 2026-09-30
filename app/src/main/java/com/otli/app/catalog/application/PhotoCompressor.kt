package com.otli.app.catalog.application

/** Port that turns a picked image into the small JPEG stored in Firestore (ADR-11). */
fun interface PhotoCompressor {
    /** Fails when [source] is not a readable image or cannot be made small enough. */
    fun compress(source: ByteArray): Result<ByteArray>
}
