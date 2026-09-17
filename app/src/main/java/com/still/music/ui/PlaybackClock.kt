package com.still.music.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

/** Only progress/lyrics observe this clock; browsing does not need a playback timer. */
internal class PlaybackClock(scope: CoroutineScope, private val readPosition: () -> Long) {
    private val mutablePosition = MutableStateFlow(0L)
    val position = mutablePosition.asStateFlow()

    init {
        scope.launch {
            mutablePosition.subscriptionCount.collectLatest { subscribers ->
                if (subscribers > 0) {
                    while (isActive) {
                        refresh()
                        delay(500)
                    }
                }
            }
        }
    }

    fun refresh() { mutablePosition.value = readPosition().coerceAtLeast(0) }
}
