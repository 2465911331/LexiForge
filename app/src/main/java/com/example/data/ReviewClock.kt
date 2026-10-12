package com.example.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

/** Rechecks deadlines while observed, including after returning from the background. */
fun reviewClock(now: () -> Long = System::currentTimeMillis) = flow {
    while (true) {
        emit(now())
        delay(1_000L)
    }
}
