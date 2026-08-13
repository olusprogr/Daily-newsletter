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

    // --- Schlagzeug-Oneshots ---
    private fun kickSample(): DoubleArray {
        val n = (sr * 0.16).toInt(); val a = DoubleArray(n); var ph = 0.0
        for (i in 0 until n) {
            val p = i.toDouble() / n
            val f = 120.0 * exp(-9.0 * p) + 46.0
            ph += 2.0 * PI * f / sr
            a[i] = sin(ph) * exp(-6.0 * p)
        }
        return a
    }
    private fun snareSample(): DoubleArray {
        val n = (sr * 0.13).toInt(); val a = DoubleArray(n); val rnd = java.util.Random(7)
        for (i in 0 until n) {
            val p = i.toDouble() / n
            val noise = rnd.nextDouble() * 2.0 - 1.0
            val tone = sin(2.0 * PI * 185.0 * i.toDouble() / sr)
            a[i] = (noise * 0.75 + tone * 0.5) * exp(-9.0 * p)
        }
        return a
    }
    private fun hatSample(): DoubleArray {
        val n = (sr * 0.045).toInt(); val a = DoubleArray(n); val rnd = java.util.Random(11); var prev = 0.0
        for (i in 0 until n) {
            val p = i.toDouble() / n
            val noise = rnd.nextDouble() * 2.0 - 1.0
            val hp = noise - prev; prev = noise            // grobe Hochpass -> heller Hi-Hat
            a[i] = hp * exp(-42.0 * p) * 0.6
        }
        return a
    }
    private fun addSample(mix: DoubleArray, start: Int, s: DoubleArray, amp: Double) {
        for (i in s.indices) { val idx = start + i; if (idx < 0) continue; if (idx >= mix.size) break; mix[idx] += s[i] * amp }
    }

    // --- Bassnote (Sinus + etwas Rechteck-Oberton, perkussive Huelle) ---
    private fun bassNote(mix: DoubleArray, start: Int, dur: Int, midi: Int, amp: Double) {
        val f = nf(midi)
        for (i in 0 until dur) {
            val idx = start + i; if (idx >= mix.size) break
            val p = i.toDouble() / dur
            val env = if (p < 0.02) p / 0.02 else exp(-2.8 * p)
            val s = sin(2.0 * PI * f * i.toDouble() / sr)
            val w = s * 0.85 + (if (s >= 0) 1.0 else -1.0) * 0.15
            mix[idx] += w * amp * env
        }
    }

    // --- Lead-Pluck (Saegezahn+Rechteck, Vibrato, Zupf-Huelle) ---
    private fun pluck(mix: DoubleArray, start: Int, dur: Int, midi: Int, amp: Double) {
        if (midi < 0) return
        val f = nf(midi)
        for (i in 0 until dur) {
            val idx = start + i; if (idx < 0) continue; if (idx >= mix.size) break
            val p = i.toDouble() / dur
            val env = exp(-3.0 * p) * (1.0 - 0.2 * p)
            val ts = i.toDouble() / sr
            val vib = 1.0 + 0.004 * sin(2.0 * PI * 5.5 * ts)
            val ph = f * vib * ts
            val saw = 2.0 * (ph - Math.floor(ph)) - 1.0
            val sq = if (sin(2.0 * PI * f * ts) >= 0) 1.0 else -1.0
            val w = saw * 0.5 + sq * 0.16
            mix[idx] += w * amp * env
        }
    }

    /**
     * Ein richtig komponierter, nahtlos loopender Track: Melodie + Bassline +
     * Akkord-Pad + Schlagzeug (Kick/Snare/HiHat), aufgeteilt in Strophe und
     * Refrain. 120 BPM in a-Moll, ~32s. Alles prozedural (keine Asset-Dateien).
     */
    private fun buildMusic(): ShortArray {
        val bpm = 120.0
        val six = (sr * (60.0 / bpm) / 4.0).toInt()   // Sechzehntel in Samples
        val barLen = six * 16

        // Akkord je Takt (Strophe 8 + Refrain 8)
        val prog = arrayOf(
            "Am", "F", "C", "G", "Am", "F", "G", "E",
            "C", "G", "Am", "F", "C", "G", "Dm", "E"
        )
        // Melodie je Takt: Paare (MIDI, Dauer in Sechzehnteln), -1 = Pause; Summe je Takt = 16
        val mel = arrayOf(
            intArrayOf(64,4, 69,4, 72,4, 71,4),          // Am
            intArrayOf(69,4, 72,4, 69,2, 67,2, 65,4),    // F
            intArrayOf(67,4, 72,4, 76,4, 74,4),          // C
            intArrayOf(74,4, 71,4, 67,4, 62,4),          // G
            intArrayOf(64,4, 69,4, 72,4, 76,4),          // Am
            intArrayOf(77,4, 76,4, 72,4, 69,4),          // F
            intArrayOf(71,4, 74,4, 67,4, 71,4),          // G
            intArrayOf(64,4, 68,4, 71,4, 64,4),          // E
            intArrayOf(72,2, 76,2, 79,4, 76,4, 72,4),    // C  (Refrain)
            intArrayOf(74,2, 71,2, 67,4, 71,4, 74,4),    // G
            intArrayOf(76,2, 72,2, 69,4, 72,4, 76,4),    // Am
            intArrayOf(77,4, 76,4, 74,4, 72,4),          // F
            intArrayOf(76,2, 79,2, 84,4, 79,4, 76,4),    // C
            intArrayOf(74,2, 79,2, 83,4, 79,4, 74,4),    // G
            intArrayOf(69,2, 74,2, 77,4, 81,4, 77,4),    // Dm
            intArrayOf(68,4, 71,4, 76,4, 71,4)           // E
        )
        val nBars = prog.size
        val total = barLen * nBars
        val mix = DoubleArray(total)
        val kick = kickSample(); val snare = snareSample(); val hat = hatSample()
        val echo = six * 3

        for (bar in 0 until nBars) {
            val base = bar * barLen
            val ch = chordOf(prog[bar])
            val chorus = bar >= 8
            // Pad (Dreiklang, weiche Huelle)
            for (i in 0 until barLen) {
                val idx = base + i; val p = i.toDouble() / barLen
                val env = (if (p < 0.08) p / 0.08 else if (p > 0.92) (1.0 - p) / 0.08 else 1.0)
                var pad = 0.0
                for (m in ch.triad) pad += sin(2.0 * PI * nf(m) * idx.toDouble() / sr)
                mix[idx] += pad * 0.016 * env
            }
            // Bass: acht Achtel, Grundton mit Quinte im Groove
            val bassPat = intArrayOf(0, 0, 7, 0, 0, 7, 0, 7)
            for (e in 0 until 8) bassNote(mix, base + e * (six * 2), six * 2, ch.root + bassPat[e], 0.17)
            // Schlagzeug
            addSample(mix, base + 0 * six, kick, 0.55)
            addSample(mix, base + 8 * six, kick, 0.55)
            addSample(mix, base + 10 * six, kick, 0.30)
            addSample(mix, base + 4 * six, snare, 0.42)
            addSample(mix, base + 12 * six, snare, 0.42)
            for (h in 0 until 8) addSample(mix, base + h * 2 * six, hat, if (h % 2 == 0) 0.22 else 0.13)
            if (chorus) { addSample(mix, base + 14 * six, snare, 0.24); addSample(mix, base + 15 * six, hat, 0.18) }
            // Melodie (+ dezentes Echo)
            val mb = mel[bar]; var step = 0; var j = 0
            val lamp = if (chorus) 0.16 else 0.13
            while (j + 1 < mb.size) {
                val midi = mb[j]; val d = mb[j + 1]; j += 2
                val st = base + step * six; val dur = d * six
                pluck(mix, st, dur, midi, lamp)
                pluck(mix, st + echo, dur, midi, lamp * 0.33)
                step += d
            }
        }

        val out = ShortArray(total)
        for (i in 0 until total) {
            val v = kotlin.math.tanh(mix[i] * 1.1)
            out[i] = (v * 32767.0 * 0.85).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
