package com.wayfare.ride.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

object SoundAlertPlayer {
    private const val SAMPLE_RATE = 22_050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isPlaying = AtomicBoolean(false)

    fun playRideOfferChime(context: Context) {
        playChimeInternal(
            context = context.applicationContext,
            firstFreqHz = 880.0,
            secondFreqHz = 1174.66
        )
    }

    fun playStatusUpdateChime(context: Context) {
        playChimeInternal(
            context = context.applicationContext,
            firstFreqHz = 659.25,
            secondFreqHz = 987.77
        )
    }

    internal fun generateChimePcm(
        firstFreqHz: Double = 880.0,
        secondFreqHz: Double = 1174.66
    ): ShortArray {
        val note1Samples = (SAMPLE_RATE * 0.20).toInt()
        val gapSamples = (SAMPLE_RATE * 0.04).toInt()
        val note2Samples = (SAMPLE_RATE * 0.30).toInt()
        val total = note1Samples + gapSamples + note2Samples
        val pcm = ShortArray(total)

        fillTone(pcm, start = 0, count = note1Samples, freqHz = firstFreqHz, amplitude = 0.55)
        fillTone(
            pcm,
            start = note1Samples + gapSamples,
            count = note2Samples,
            freqHz = secondFreqHz,
            amplitude = 0.65
        )
        return pcm
    }

    private fun fillTone(
        buffer: ShortArray,
        start: Int,
        count: Int,
        freqHz: Double,
        amplitude: Double
    ) {
        val fadeSamples = min(count / 4, (SAMPLE_RATE * 0.025).toInt()).coerceAtLeast(1)
        for (i in 0 until count) {
            val attack = if (i < fadeSamples) i.toDouble() / fadeSamples else 1.0
            val release = if (i > count - fadeSamples) (count - i).toDouble() / fadeSamples else 1.0
            val env = min(attack, release).coerceIn(0.0, 1.0)
            val fundamental = sin(2.0 * PI * freqHz * i / SAMPLE_RATE)
            val harmonic = 0.25 * sin(2.0 * PI * (freqHz * 2.0) * i / SAMPLE_RATE)
            val sample = ((fundamental + harmonic) * 0.8 * amplitude * env * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[start + i] = sample.toShort()
        }
    }

    private fun playChimeInternal(
        context: Context,
        firstFreqHz: Double,
        secondFreqHz: Double
    ) {
        if (!isPlaying.compareAndSet(false, true)) return
        scope.launch {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val mediaVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                val notifVolume = audioManager?.getStreamVolume(AudioManager.STREAM_NOTIFICATION) ?: 0
                val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

                // Respect hard silent mode when media is also muted
                if (ringerMode == AudioManager.RINGER_MODE_SILENT && mediaVolume <= 0) {
                    return@launch
                }

                // Prefer media stream when media volume is audible so speaker wake-up and in-app chimes work reliably;
                // fall back to notification usage when media is muted and notification volume is active.
                val useMediaStream = mediaVolume > 0 || notifVolume <= 0
                val usage = if (useMediaStream) {
                    AudioAttributes.USAGE_MEDIA
                } else {
                    AudioAttributes.USAGE_NOTIFICATION_EVENT
                }

                val played = playViaAudioTrack(firstFreqHz, secondFreqHz, usage)
                if (!played) {
                    playViaFallback(context, useMediaStream)
                }
            } catch (_: Throwable) {
                // Never crash caller on audio device unavailability
            } finally {
                isPlaying.set(false)
            }
        }
    }

    private fun playViaAudioTrack(
        firstFreqHz: Double,
        secondFreqHz: Double,
        usage: Int
    ): Boolean {
        return runCatching {
            val pcm = generateChimePcm(firstFreqHz, secondFreqHz)
            val minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufBytes <= 0) return false
            val pcmBytes = pcm.size * 2
            val bufferSize = maxOf(minBufBytes, pcmBytes)
            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                val written = track.write(pcm, 0, pcm.size)
                if (written <= 0) return false
                track.play()
                val durationMs = ((pcm.size * 1000L) / SAMPLE_RATE) + 80L
                Thread.sleep(durationMs)
                true
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }.getOrDefault(false)
    }

    private fun playViaFallback(context: Context, useMediaStream: Boolean) {
        val playedRingtone = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return@runCatching false
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return@runCatching false
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(
                    if (useMediaStream) AudioAttributes.USAGE_MEDIA
                    else AudioAttributes.USAGE_NOTIFICATION_EVENT
                )
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            true
        }.getOrDefault(false)

        if (!playedRingtone) {
            runCatching {
                val stream = if (useMediaStream) AudioManager.STREAM_MUSIC else AudioManager.STREAM_NOTIFICATION
                val tg = ToneGenerator(stream, 90)
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 420)
                    Thread.sleep(450L)
                } finally {
                    tg.release()
                }
            }
        }
    }
}
