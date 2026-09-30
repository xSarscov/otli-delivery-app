package com.otli.app.core.time

/** Time source port so domain and application code never call the system clock directly. */
interface Clock {
    fun nowMillis(): Long
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
