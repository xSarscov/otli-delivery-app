package com.otli.app.catalog.adapters.device

/** Pure sizing rules for photo compression, kept apart from the Android bitmap code. */
internal object ImageScaling {
    /** Scales [width] x [height] down so the longer edge is at most [maxEdge]; never upscales. */
    fun fit(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return width to height
        val factor = maxEdge.toDouble() / longest
        return maxOf(1, Math.round(width * factor).toInt()) to maxOf(1, Math.round(height * factor).toInt())
    }
}
