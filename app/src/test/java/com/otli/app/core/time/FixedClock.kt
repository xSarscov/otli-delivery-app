package com.otli.app.core.time

/** A [Clock] frozen at [millis], so time-dependent tests are deterministic. */
class FixedClock(var millis: Long) : Clock {
    override fun nowMillis(): Long = millis
}
