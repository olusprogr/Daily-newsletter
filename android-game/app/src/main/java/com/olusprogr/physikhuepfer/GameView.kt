package com.olusprogr.physikhuepfer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

/**
 * "Physik Hüpfer" – ein kleines Flappy-Bird-artiges Gravitationsspiel.
 *
 * Spielprinzip: Ein Ball fällt durch die Schwerkraft ständig nach unten.
 * Ein Tipp auf den Bildschirm gibt ihm einen Sprungimpuls nach oben.
 * Ziel ist es, so vielen Röhren wie möglich durch die Lücke auszuweichen.
 * Der Highscore wird lokal (offline, SharedPreferences) gespeichert.
 */
class GameView(context: Context) : View(context) {

    // --- Physik-Konstanten (in Pixel pro Sekunde bzw. Pixel pro Sekunde^2) ---
    private val gravity = 1600f
    private val jumpVelocity = -650f
    private val pipeSpeed = 320f
    private val pipeGap = 380f
    private val pipeWidth = 130f
    private val pipeSpacing = 550f
    private val ballRadius = 35f

    // --- Zustand ---
    private var width = 0
    private var height = 0
    private var ballY = 0f
    private var ballVelocity = 0f
    private val ballX get() = width * 0.28f

    private class Pipe(var x: Float, val gapCenter: Float, var scored: Boolean = false)

    private val pipes = mutableListOf<Pipe>()

    private var score = 0
    private var highscore = 0
    private var isRunning = false
    private var isGameOver = false
    private var lastFrameTime = 0L

    private val prefs = context.getSharedPreferences("physik_huepfer", Context.MODE_PRIVATE)

    // --- Paints ---
    private val skyPaint = Paint().apply { color = Color.rgb(135, 206, 250) }
    private val ballPaint = Paint().apply { color = Color.rgb(255, 87, 34); isAntiAlias = true }
    private val pipePaint = Paint().apply { color = Color.rgb(56, 142, 60); isAntiAlias = true }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 72f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
    }
    private val smallTextPaint = Paint(textPaint).apply { textSize = 42f }

    private val handler = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            if (isRunning) {
                step()
                invalidate()
                handler.postDelayed(this, 16)
            }
        }
    }

    init {
        highscore = prefs.getInt("highscore", 0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        width = w
        height = h
        reset()
    }

    private fun reset() {
        ballY = height * 0.4f
        ballVelocity = 0f
        pipes.clear()
        score = 0
        isGameOver = false
        isRunning = true
        lastFrameTime = System.nanoTime()
        for (i in 0 until 3) {
            spawnPipe(width + i * pipeSpacing)
        }
        handler.removeCallbacks(loop)
        handler.post(loop)
    }

    private fun spawnPipe(x: Float) {
        val margin = pipeGap
        val minCenter = margin
        val maxCenter = (height - margin).coerceAtLeast(minCenter + 1f)
        val center = Random.nextFloat() * (maxCenter - minCenter) + minCenter
        pipes.add(Pipe(x, center))
    }

    private fun step() {
        val now = System.nanoTime()
        var dt = (now - lastFrameTime) / 1_000_000_000f
        lastFrameTime = now
        if (dt > 0.05f) dt = 0.05f // Ausreißer (z.B. nach Pause) begrenzen

        if (isGameOver) return

        ballVelocity += gravity * dt
        ballY += ballVelocity * dt

        for (pipe in pipes) {
            pipe.x -= pipeSpeed * dt
        }
        if (pipes.isNotEmpty() && pipes.first().x < -pipeWidth) {
            pipes.removeAt(0)
        }
        val maxX = pipes.maxOfOrNull { it.x } ?: width.toFloat()
        if (maxX < width - pipeSpacing) {
            spawnPipe(maxX + pipeSpacing)
        }

        val ballLeft = ballX - ballRadius
        val ballRight = ballX + ballRadius
        val ballTop = ballY - ballRadius
        val ballBottom = ballY + ballRadius

        for (pipe in pipes) {
            if (ballRight > pipe.x && ballLeft < pipe.x + pipeWidth) {
                val gapTop = pipe.gapCenter - pipeGap / 2
                val gapBottom = pipe.gapCenter + pipeGap / 2
                if (ballTop < gapTop || ballBottom > gapBottom) {
                    gameOver()
                }
            }
            if (!pipe.scored && pipe.x + pipeWidth < ballX) {
                pipe.scored = true
                score++
            }
        }

        if (ballY - ballRadius < 0 || ballY + ballRadius > height) {
            gameOver()
        }
    }

    private fun gameOver() {
        if (isGameOver) return
        isGameOver = true
        if (score > highscore) {
            highscore = score
            prefs.edit().putInt("highscore", highscore).apply()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)

        for (pipe in pipes) {
            val gapTop = pipe.gapCenter - pipeGap / 2
            val gapBottom = pipe.gapCenter + pipeGap / 2
            canvas.drawRect(pipe.x, 0f, pipe.x + pipeWidth, gapTop, pipePaint)
            canvas.drawRect(pipe.x, gapBottom, pipe.x + pipeWidth, height.toFloat(), pipePaint)
        }

        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)
        canvas.drawText(score.toString(), width / 2f, 140f, textPaint)

        if (isGameOver) {
            canvas.drawText("Game Over", width / 2f, height / 2f - 60f, textPaint)
            canvas.drawText("Highscore: $highscore", width / 2f, height / 2f, smallTextPaint)
            canvas.drawText("Tippen zum Neustart", width / 2f, height / 2f + 60f, smallTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isGameOver) {
                reset()
            } else {
                ballVelocity = jumpVelocity
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
        handler.removeCallbacks(loop)
    }
}
