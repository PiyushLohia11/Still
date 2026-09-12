package com.still.music

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.still.music.playback.StreamingRetryPolicy
import com.still.music.provider.ProviderException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RetryPolicyTest {
    private fun info(count: Int, status: Int = 0) = LoadErrorHandlingPolicy.LoadErrorInfo(
        LoadEventInfo(1, DataSpec(Uri.parse("https://example.org/audio")), 0), MediaLoadData(C.DATA_TYPE_MEDIA),
        ProviderException("test", status), count)
    @Test fun transientFailuresRetryAtMostThreeTimes() {
        val policy = StreamingRetryPolicy()
        assertEquals(1000L, policy.getRetryDelayMsFor(info(1)))
        assertEquals(2000L, policy.getRetryDelayMsFor(info(2)))
        assertEquals(4000L, policy.getRetryDelayMsFor(info(3)))
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(4)))
    }
    @Test fun accessRefusalsNeverRetry() {
        val policy = StreamingRetryPolicy()
        for (status in listOf(400, 401, 403, 404, 410, 416)) assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(info(1, status)))
    }
}
