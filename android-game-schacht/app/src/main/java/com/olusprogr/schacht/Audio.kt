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
    // Lautstaerken 0..1, getrennt fuer Musik und Effekte.
    @Volatile var musicVol = 0.5f
        private set
    @Volatile var sfxVol = 0.33f
        private set
    @Volatile private var playing = false
    private var musicTrack: AudioTrack? = null
    private var musicThread: Thread? = null
    private val musicBuf: ShortArray by lazy { buildMusic() }
    private val handler = Handler(Looper.getMainLooper())

    fun setMusicVol(v: Float) {
        musicVol = v.coerceIn(0f, 1f)
        if (musicVol <= 0.001f) stopMusic()
        else if (!playing) startMusic()
        else try { musicTrack?.setVolume(musicVol) } catch (_: Exception) { }
    }

    fun setSfxVol(v: Float) { sfxVol = v.coerceIn(0f, 1f) }

    fun startMusic() {
        if (musicVol <= 0.001f || playing) return
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
            track.setVolume(musicVol)
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

    // --- SFX --- (Basis-Amplitude bewusst niedrig; skaliert zusaetzlich mit sfxVol)
    fun place() = playTone(200.0, 150.0, 90, 0.20, square = true)
    fun buy() = playTone(500.0, 1000.0, 130, 0.17, square = true)
    fun sell() = playTone(720.0, 340.0, 130, 0.17, square = true)
    fun error() = playTone(150.0, 130.0, 170, 0.20, square = true, tremolo = true)
    fun click() = playTone(1300.0, 1300.0, 28, 0.11, square = false)

    private fun playTone(f0: Double, f1: Double, durMs: Int, amp: Double, square: Boolean, tremolo: Boolean = false) {
        if (sfxVol <= 0.001f) return
        try {
            val buf = buildTone(f0, f1, durMs, amp * sfxVol, square, tremolo)
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

    /**
     * 8-Sekunden-Loop mit vierteiliger Akkordfolge (Am – F – C – G): weicher
     * Bass mit Oberton, sanfter Akkord-Pad und eine glockige Lead-Melodie,
     * dazu ein dezenter Puls. Zum Schluss weiche Begrenzung (tanh) statt hartem
     * Clipping – klingt runder als der alte 4s-Loop.
     */
    private fun buildMusic(): ShortArray {
        val loopSec = 8.0
        val nS = (sr * loopSec).toInt()
        val mix = DoubleArray(nS)

        // bass, [drei Akkordtoene]
        val bass = doubleArrayOf(55.0, 43.65, 65.41, 49.0)          // A1, F1, C2, G1
        val tones = arrayOf(
            doubleArrayOf(220.0, 261.63, 329.63),  // Am
            doubleArrayOf(174.61, 220.0, 261.63),  // F
            doubleArrayOf(196.0, 261.63, 329.63),  // C
            doubleArrayOf(196.0, 246.94, 293.66)   // G
        )
        val chords = bass.size
        val chordLen = nS / chords

        for (ci in 0 until chords) {
            val start = ci * chordLen
            val bf = bass[ci]
            val ch = tones[ci]
            for (i in 0 until chordLen) {
                val idx = start + i
                if (idx >= nS) break
                val p = i.toDouble() / chordLen
                val t = idx.toDouble() / sr
                val bEnv = (if (p < 0.03) p / 0.03 else if (p > 0.9) (1.0 - p) / 0.1 else 1.0).coerceIn(0.0, 1.0)
                mix[idx] += sin(2.0 * PI * bf * t) * 0.15 * bEnv
                mix[idx] += sin(2.0 * PI * bf * 2.0 * t) * 0.035 * bEnv
                var pad = 0.0
                for (f in ch) pad += sin(2.0 * PI * f * t)
                val padEnv = (if (p < 0.15) p / 0.15 else if (p > 0.85) (1.0 - p) / 0.15 else 1.0).coerceIn(0.0, 1.0)
                mix[idx] += pad * 0.026 * padEnv
            }
        }

        // Glocken-Lead: 32 Plucks eine Oktave ueber den Akkordtoenen
        val steps = 32
        val stepLen = nS / steps
        val pattern = intArrayOf(0, 2, 1, 2, 0, 1, 2, 1)
        for (s in 0 until steps) {
            val ci = s * chords / steps
            val ch = tones[ci]
            val f = ch[pattern[s % pattern.size] % ch.size] * 2.0
            for (i in 0 until stepLen) {
                val idx = s * stepLen + i
                if (idx >= nS) break
                val p = i.toDouble() / stepLen
                val env = exp(-5.0 * p) * (1.0 - p)
                mix[idx] += sin(2.0 * PI * f * idx / sr) * 0.055 * env
            }
        }

        // dezenter Puls alle 0.5s
        val beat = (sr * 0.5).toInt()
        val tickLen = (sr * 0.03).toInt()
        var b = 0
        while (b < nS) {
            for (i in 0 until tickLen) {
                val idx = b + i
                if (idx >= nS) break
                val env = exp(-28.0 * (i.toDouble() / tickLen))
                mix[idx] += sin(2.0 * PI * 1600.0 * idx / sr) * 0.012 * env
            }
            b += beat
        }

        val out = ShortArray(nS)
        for (i in 0 until nS) {
            val v = kotlin.math.tanh(mix[i] * 1.15)   // weiche Begrenzung
            out[i] = (v * 32767.0 * 0.82).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
