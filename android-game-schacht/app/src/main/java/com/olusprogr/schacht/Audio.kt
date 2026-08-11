package com.olusprogr.schacht

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Vollstaendig prozedurale Audio-Engine (keine Asset-Dateien): ein
 * geloopter Ambient-/Industrial-Track als Hintergrundmusik plus kurze
 * synthetisierte UI-Sounds. Alles ueber AudioTrack + PCM. Robust gekapselt –
 * faellt Audio aus, laeuft das Spiel unbeeindruckt weiter.
 */
class Audio {

    private val sr = 22050
    @Volatile private var muted = false
    @Volatile private var playing = false
    private var musicTrack: AudioTrack? = null
    private var musicThread: Thread? = null
    private val musicBuf: ShortArray by lazy { buildMusic() }
    private val handler = Handler(Looper.getMainLooper())

    fun isMuted() = muted

    fun setMuted(m: Boolean) {
        muted = m
        if (m) stopMusic() else startMusic()
    }

    fun toggleMuted(): Boolean { setMuted(!muted); return muted }

    fun startMusic() {
        if (muted || playing) return
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(
                    maxOf(
                        AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                        musicBuf.size * 2
                    )
                )
                .build()
            track.setVolume(0.5f)
            track.play()
            musicTrack = track
            playing = true
            val th = Thread {
                try {
                    while (playing) {
                        var off = 0
                        while (off < musicBuf.size && playing) {
                            val w = track.write(musicBuf, off, musicBuf.size - off)
                            if (w <= 0) break
                            off += w
                        }
                    }
                } catch (_: Exception) { }
            }
            th.isDaemon = true
            musicThread = th
            th.start()
        } catch (_: Exception) { }
    }

    private fun stopMusic() {
        playing = false
        try { musicThread?.join(200) } catch (_: Exception) { }
        musicThread = null
        try { musicTrack?.pause(); musicTrack?.flush(); musicTrack?.release() } catch (_: Exception) { }
        musicTrack = null
    }

    fun pause() = stopMusic()
    fun resume() = startMusic()
    fun release() = stopMusic()

    // --- SFX ---
    fun place() = playTone(200.0, 150.0, 90, 0.35, square = true)
    fun buy() = playTone(500.0, 1000.0, 130, 0.30, square = true)
    fun sell() = playTone(720.0, 340.0, 130, 0.30, square = true)
    fun error() = playTone(150.0, 130.0, 170, 0.35, square = true, tremolo = true)
    fun click() = playTone(1300.0, 1300.0, 28, 0.20, square = false)

    private fun playTone(f0: Double, f1: Double, durMs: Int, amp: Double, square: Boolean, tremolo: Boolean = false) {
        if (muted) return
        try {
            val buf = buildTone(f0, f1, durMs, amp, square, tremolo)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(buf.size * 2)
                .build()
            track.write(buf, 0, buf.size)
            track.play()
            handler.postDelayed({ try { track.release() } catch (_: Exception) { } }, (durMs + 80).toLong())
        } catch (_: Exception) { }
    }

    private fun buildTone(f0: Double, f1: Double, durMs: Int, amp: Double, square: Boolean, tremolo: Boolean): ShortArray {
        val nS = (sr * durMs / 1000.0).toInt().coerceAtLeast(1)
        val out = ShortArray(nS)
        var phase = 0.0
        for (i in 0 until nS) {
            val p = i.toDouble() / nS
            val f = f0 + (f1 - f0) * p
            phase += 2.0 * PI * f / sr
            var v = sin(phase)
            if (square) v = if (v >= 0) 1.0 else -1.0
            // Attack/Release-Huellkurve
            val env = when {
                p < 0.06 -> p / 0.06
                p > 0.7 -> (1.0 - p) / 0.3
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            var g = amp * env
            if (tremolo) g *= 0.6 + 0.4 * sin(2.0 * PI * 22.0 * i / sr)
            out[i] = (v * g * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** 4-Sekunden-Loop: tiefer Bass-Puls + sparsame Arpeggio-Plucks. */
    private fun buildMusic(): ShortArray {
        val loopSec = 4.0
        val nS = (sr * loopSec).toInt()
        val mix = DoubleArray(nS)

        // Bass: 2 lange Noten (A1, E2)
        val bassNotes = doubleArrayOf(55.0, 55.0, 82.41, 82.41)
        val stepBass = nS / bassNotes.size
        for (s in bassNotes.indices) {
            val f = bassNotes[s]
            for (i in 0 until stepBass) {
                val idx = s * stepBass + i
                if (idx >= nS) break
                val p = i.toDouble() / stepBass
                val env = (if (p < 0.05) p / 0.05 else if (p > 0.85) (1.0 - p) / 0.15 else 1.0).coerceIn(0.0, 1.0)
                mix[idx] += sin(2.0 * PI * f * idx / sr) * 0.18 * env
            }
        }

        // Arpeggio: 16 Plucks aus A-Moll-Pentatonik
        val arp = doubleArrayOf(220.0, 261.63, 329.63, 392.0)
        val steps = 16
        val stepLen = nS / steps
        for (s in 0 until steps) {
            val f = arp[s % arp.size]
            for (i in 0 until stepLen) {
                val idx = s * stepLen + i
                if (idx >= nS) break
                val p = i.toDouble() / stepLen
                val env = exp(-4.0 * p) * (1.0 - p)   // schneller Pluck-Abfall
                mix[idx] += sin(2.0 * PI * f * idx / sr) * 0.10 * env
            }
        }

        val out = ShortArray(nS)
        for (i in 0 until nS) {
            val v = mix[i].coerceIn(-1.0, 1.0)
            out[i] = (v * 32767.0 * 0.9).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
