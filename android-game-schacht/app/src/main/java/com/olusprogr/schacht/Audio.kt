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
                    // ~1s Puffer; der Stream-Loop schreibt den ganzen (langen) Track
                    // in Haeppchen nach, also muss der Puffer nicht die Songlaenge fassen.
                    maxOf(
                        AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                        sr * 2
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

    // MIDI-Note -> Frequenz (A4=69=440Hz)
    private fun nf(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    private class Ch(val root: Int, val triad: IntArray)
    private fun chordOf(name: String): Ch = when (name) {
        "Am" -> Ch(45, intArrayOf(57, 60, 64))
        "F"  -> Ch(41, intArrayOf(53, 57, 60))
        "C"  -> Ch(48, intArrayOf(60, 64, 67))
        "G"  -> Ch(43, intArrayOf(55, 59, 62))
        "E"  -> Ch(40, intArrayOf(52, 56, 59))
        "Dm" -> Ch(50, intArrayOf(62, 65, 69))
        else -> Ch(45, intArrayOf(57, 60, 64))
    }

    private fun addSample(mix: DoubleArray, start: Int, s: DoubleArray, amp: Double) {
        for (i in s.indices) { val idx = start + i; if (idx < 0) continue; if (idx >= mix.size) break; mix[idx] += s[i] * amp }
    }

    // Warmes Mallet/Glockenspiel-Lead (leicht inharmonisch, weiche Huelle) statt Synth-Saegezahn
    private fun mallet(f: Double, dur: Int, amp: Double): DoubleArray {
        val a = DoubleArray(dur); val at = (sr * 0.007).toInt()
        for (i in 0 until dur) {
            val t = i.toDouble() / sr; val p = i.toDouble() / dur
            var v = sin(2.0 * PI * f * t) * exp(-2.0 * p) +
                    0.42 * sin(2.0 * PI * 2.0 * f * t) * exp(-3.4 * p) +
                    0.16 * sin(2.0 * PI * 3.0 * f * t) * exp(-5.0 * p) +
                    0.07 * sin(2.0 * PI * 4.2 * f * t) * exp(-6.5 * p)
            if (at > 1 && i < at) v *= i.toDouble() / at
            a[i] = v * amp
        }
        return a
    }
    // Warmer Bass (Sinus + leichter Oberton, runde Huelle)
    private fun wbass(f: Double, dur: Int, amp: Double): DoubleArray {
        val a = DoubleArray(dur)
        for (i in 0 until dur) {
            val t = i.toDouble() / sr; val p = i.toDouble() / dur
            val env = if (p < 0.02) p / 0.02 else exp(-2.0 * p)
            a[i] = (sin(2.0 * PI * f * t) + 0.22 * sin(2.0 * PI * 2.0 * f * t)) * env * amp
        }
        return a
    }
    // --- weiche Percussion ---
    private fun softKick(): DoubleArray {
        val n = (sr * 0.20).toInt(); val a = DoubleArray(n); var ph = 0.0
        for (i in 0 until n) { val t = i.toDouble() / sr; val f = 95.0 * exp(-16.0 * t) + 48.0; ph += 2.0 * PI * f / sr; a[i] = sin(ph) * exp(-4.2 * t) }
        return a
    }
    private fun rimSample(): DoubleArray {   // holziger Side-Stick
        val n = (sr * 0.05).toInt(); val a = DoubleArray(n); val rnd = java.util.Random(3)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val nz = (rnd.nextDouble() * 2.0 - 1.0) * exp(-90.0 * t) * 0.25
            a[i] = (sin(2.0 * PI * 430.0 * t) * exp(-55.0 * t) + 0.5 * sin(2.0 * PI * 820.0 * t) * exp(-75.0 * t) + nz) * 0.9
        }
        return a
    }
    private fun shakerSample(): DoubleArray {
        val n = (sr * 0.05).toInt(); val a = DoubleArray(n); val rnd = java.util.Random(8); var prev = 0.0
        for (i in 0 until n) { val t = i.toDouble() / sr; val nz = rnd.nextDouble() * 2.0 - 1.0; val hp = nz - prev; prev = nz; a[i] = hp * exp(-38.0 * t) * 0.5 }
        return a
    }

    /**
     * Warmer, wenig-elektronischer Track: Mallet/Glockenspiel-Melodie (eine Oktave
     * tiefer), warmes Sinus-Pad + Bass, sanfte Percussion (Kick/Side-Stick/Shaker),
     * leiser Arpeggio-Teppich und viel natuerlicher Hall. 110 BPM in a-Moll,
     * Aufbau Intro->Strophe->Refrain->Strophe->Turnaround, ~70s Loop.
     */
    private fun buildMusic(): ShortArray {
        val bpm = 110.0
        val six = (sr * (60.0 / bpm) / 4.0).toInt()
        val barLen = six * 16
        val verseP = arrayOf("Am", "F", "C", "G", "Am", "F", "G", "E")
        val chorusP = arrayOf("C", "G", "Am", "F", "C", "G", "Dm", "E")
        val turnP = arrayOf("F", "G", "Am", "E")
        val prog = arrayOf("Am", "F", "C", "G") + verseP + chorusP + verseP + turnP
        val verseM = arrayOf(
            intArrayOf(64,4,69,4,72,4,71,4), intArrayOf(69,4,72,4,69,2,67,2,65,4),
            intArrayOf(67,4,72,4,76,4,74,4), intArrayOf(74,4,71,4,67,4,62,4),
            intArrayOf(64,4,69,4,72,4,76,4), intArrayOf(77,4,76,4,72,4,69,4),
            intArrayOf(71,4,74,4,67,4,71,4), intArrayOf(64,4,68,4,71,4,64,4))
        val chorusM = arrayOf(
            intArrayOf(72,2,76,2,79,4,76,4,72,4), intArrayOf(74,2,71,2,67,4,71,4,74,4),
            intArrayOf(76,2,72,2,69,4,72,4,76,4), intArrayOf(77,4,76,4,74,4,72,4),
            intArrayOf(76,2,79,2,84,4,79,4,76,4), intArrayOf(74,2,79,2,83,4,79,4,74,4),
            intArrayOf(69,2,74,2,77,4,81,4,77,4), intArrayOf(68,4,71,4,76,4,71,4))
        val turnM = arrayOf(
            intArrayOf(77,4,76,4,74,4,72,4), intArrayOf(74,4,71,4,67,4,62,4),
            intArrayOf(64,4,69,4,72,4,64,4), intArrayOf(71,2,68,2,64,4,-1,8))
        val mel = arrayOf(intArrayOf(), intArrayOf(), intArrayOf(), intArrayOf()) + verseM + chorusM + verseM + turnM

        val nBars = prog.size
        val total = barLen * nBars
        val mix = DoubleArray(total)
        val verb = DoubleArray(total)
        val sk = softKick(); val rm = rimSample(); val sh = shakerSample()

        for (bar in 0 until nBars) {
            val base = bar * barLen
            val ch = chordOf(prog[bar])
            val kind = when { bar < 4 -> 0; bar < 12 -> 1; bar < 20 -> 2; bar < 28 -> 1; else -> 3 } // 0=Intro,1=Strophe,2=Refrain,3=Turn
            val full = kind != 0 || bar >= 2
            val chorus = kind == 2
            // Warmes Pad (Sinus-Dreiklang + Oktave darunter)
            for (i in 0 until barLen) {
                val idx = base + i; val t = idx.toDouble() / sr; val p = i.toDouble() / barLen
                val env = if (p < 0.14) p / 0.14 else if (p > 0.88) (1.0 - p) / 0.12 else 1.0
                var pad = 0.0
                for (m in ch.triad) pad += sin(2.0 * PI * nf(m) * t) + 0.5 * sin(2.0 * PI * nf(m - 12) * t) + 0.25 * sin(2.0 * PI * 2.0 * nf(m) * t)
                val v = pad * 0.011 * env
                mix[idx] += v; verb[idx] += v * 0.5
            }
            // Bass
            if (full) {
                val bp = intArrayOf(0, 0, 7, 0, 0, 7, 0, 7)
                for (e in 0 until 8) addSample(mix, base + e * six * 2, wbass(nf(ch.root + bp[e]), six * 2, 0.20), 1.0)
            }
            // Sanfte Percussion (Kick 1&3, Side-Stick 2&4, leiser Shaker Achtel)
            if (kind != 0 || bar >= 1) {
                addSample(mix, base + 0 * six, sk, 0.5); addSample(mix, base + 8 * six, sk, 0.5)
                addSample(mix, base + 4 * six, rm, 0.38); addSample(mix, base + 12 * six, rm, 0.38)
                addSample(verb, base + 4 * six, rm, 0.18); addSample(verb, base + 12 * six, rm, 0.18)
                if (kind != 0) for (h in 0 until 8) addSample(mix, base + h * 2 * six, sh, if (h % 2 == 0) 0.13 else 0.09)
            }
            // Leiser Arpeggio-Teppich (statt E-Delay)
            if (full) {
                val arp = intArrayOf(ch.triad[0], ch.triad[1], ch.triad[2], ch.triad[1])
                for (aI in 0 until 4) {
                    val st = base + aI * 4 * six; val s = mallet(nf(arp[aI]), 4 * six, 0.045)
                    addSample(mix, st, s, 0.7); addSample(verb, st, s, 0.4)
                }
            }
            // Melodie (warm, eine Oktave tiefer)
            val mb = mel[bar]; var step = 0; var j = 0
            val lamp = if (chorus) 0.20 else 0.17
            while (j + 1 < mb.size) {
                val midi = mb[j]; val d = mb[j + 1]; j += 2
                if (midi >= 0) {
                    val st = base + step * six; val s = mallet(nf(midi - 12), d * six, lamp)
                    addSample(mix, st, s, 0.95); addSample(verb, st, s, 0.6)
                }
                step += d
            }
        }

        // Weicher Multi-Tap-Reverb auf dem Send-Bus
        val rnd = java.util.Random(21)
        for (k in 0 until 24) {
            val tt = 0.03 + rnd.nextDouble() * 0.13
            val d0 = (tt * sr).toInt(); val gain = 0.55 * exp(-tt * 5.0)
            var i = d0
            while (i < total) { mix[i] += verb[i - d0] * gain; i++ }
        }

        // Master: normalisieren + weiche tanh-Begrenzung
        var peak = 1e-6
        for (i in 0 until total) { val av = abs(mix[i]); if (av > peak) peak = av }
        val norm = 1.0 / (peak * 1.1)
        val out = ShortArray(total)
        for (i in 0 until total) {
            val v = kotlin.math.tanh(mix[i] * norm * 1.15)
            out[i] = (v * 32767.0 * 0.9).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
