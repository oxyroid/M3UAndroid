package com.m3u.data.service.internal

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import androidx.media3.common.C
import timber.log.Timber

internal class NightAudioEffect {
    private val timber = Timber.tag("NightAudioEffect")
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var effect: DynamicsProcessing? = null
    private var enabled: Boolean = false

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (enabled) {
            attach(audioSessionId)
        } else {
            release()
        }
    }

    fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (this.audioSessionId == audioSessionId) return
        this.audioSessionId = audioSessionId
        attach(audioSessionId)
    }

    fun release() {
        effect?.runCatchingRelease()
        effect = null
    }

    private fun attach(audioSessionId: Int) {
        release()
        if (!enabled || audioSessionId == C.AUDIO_SESSION_ID_UNSET || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }
        effect = runCatching {
            DynamicsProcessing(
                0,
                audioSessionId,
                buildNightModeConfig()
            ).apply {
                setEnabled(true)
            }
        }.onSuccess {
            timber.d("night audio mode attached to session $audioSessionId")
        }.onFailure { error ->
            timber.w(error, "failed to attach night audio mode")
        }.getOrNull()
    }

    private fun DynamicsProcessing.runCatchingRelease() {
        runCatching {
            enabled = false
            release()
        }.onFailure { error ->
            timber.w(error, "failed to release night audio mode")
        }
    }

    private companion object {
        private const val CHANNEL_COUNT = 2
        private const val MBC_BANDS = 3

        private fun buildNightModeConfig(): DynamicsProcessing.Config {
            return DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                CHANNEL_COUNT,
                false,
                0,
                true,
                MBC_BANDS,
                false,
                0,
                true
            )
                .setInputGainAllChannelsTo(-4f)
                .setMbcAllChannelsTo(buildCompressor())
                .setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        1f,
                        80f,
                        12f,
                        -6f,
                        0f
                    )
                )
                .build()
        }

        private fun buildCompressor(): DynamicsProcessing.Mbc {
            return DynamicsProcessing.Mbc(true, true, MBC_BANDS).apply {
                setBand(0, mbcBand(cutoffFrequency = 250f, attack = 8f, release = 90f, ratio = 2f, threshold = -24f))
                setBand(1, mbcBand(cutoffFrequency = 4_000f, attack = 5f, release = 110f, ratio = 3f, threshold = -26f))
                setBand(2, mbcBand(cutoffFrequency = 20_000f, attack = 2f, release = 140f, ratio = 4f, threshold = -28f))
            }
        }

        private fun mbcBand(
            cutoffFrequency: Float,
            attack: Float,
            release: Float,
            ratio: Float,
            threshold: Float
        ): DynamicsProcessing.MbcBand {
            return DynamicsProcessing.MbcBand(
                true,
                cutoffFrequency,
                attack,
                release,
                ratio,
                threshold,
                6f,
                -90f,
                1f,
                0f,
                0f
            )
        }
    }
}
