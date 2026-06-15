package com.studyapp.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class GlobeBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val STAR_COUNT = 60
    }

    // Paints
    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintGlobe = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintGlobeShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintInnerGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintOuterGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintStar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintAtmosphere = Paint(Paint.ANTI_ALIAS_FLAG)

    // Geometry
    private var globeX = 0f
    private var globeY = 0f
    private var globeR = 0f
    private var w = 0
    private var h = 0

    // Texture
    private var textureBitmap: Bitmap? = null
    // Reference geometry
    private val refCx = 839f
    private val refCy = 502f
    private val refR = 695f

    // Stars
    private data class Star(val x: Float, val y: Float, val radius: Float, val baseAlpha: Int, val twinklePhase: Float)
    private var stars: List<Star> = emptyList()

    init {
        paintStar.style = Paint.Style.FILL
        paintGlobeShadow.style = Paint.Style.FILL
        paintAtmosphere.style = Paint.Style.FILL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(width, height, oldw, oldh)
        w = width; h = height
        globeX = width * 0.30f
        globeY = height * 0.48f
        globeR = minOf(width, height) * 0.60f

        // Load and prepare texture
        loadGlobeTexture()

        // Precompute stars
        val starList = mutableListOf<Star>()
        val seed = 42
        for (i in 0 until STAR_COUNT) {
            val px = ((i * 137.508f + seed) % width).toFloat()
            val py = ((i * 269.358f + seed * 7) % height).toFloat()
            val size = ((i * 3 + seed) % 3 + 1).toFloat()
            val dist = sqrt((px - globeX) * (px - globeX) + (py - globeY) * (py - globeY))
            if (dist > globeR * 1.1f) {
                starList.add(Star(
                    x = px, y = py, radius = size,
                    baseAlpha = 30 + (i * 7) % 50,
                    twinklePhase = (i * 1.3f) % 6.28f
                ))
            }
        }
        var fillIdx = STAR_COUNT
        while (starList.size < STAR_COUNT) {
            val px = ((fillIdx * 137.508f + seed) % width).toFloat()
            val py = ((fillIdx * 269.358f + seed * 7) % height).toFloat()
            val size = ((fillIdx * 3 + seed) % 3 + 1).toFloat()
            val dist = sqrt((px - globeX) * (px - globeX) + (py - globeY) * (py - globeY))
            if (dist > globeR * 1.1f) {
                starList.add(Star(
                    x = px, y = py, radius = size,
                    baseAlpha = 30 + (fillIdx * 7) % 50,
                    twinklePhase = (fillIdx * 1.3f) % 6.28f
                ))
            }
            fillIdx++
        }
        stars = starList
    }

    private fun loadGlobeTexture() {
        // 使用自绘纹理代替外部图片
        try {
            val tex = Bitmap.createBitmap((refR * 2).toInt(), (refR * 2).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(tex)
            val cx = refR; val cy = refR

            // 绘制渐变球体
            val shader = RadialGradient(
                cx - refR * 0.25f, cy - refR * 0.25f, refR * 1.2f,
                intArrayOf(0xFF1a6b8a.toInt(), 0xFF0d3b5c.toInt(), 0xFF061e30.toInt()),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            canvas.drawCircle(cx, cy, refR, paint)

            // 添加经纬线效果
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(30, 100, 200, 255)
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }
            for (i in 1..4) {
                val r = refR * (i / 5f)
                canvas.drawCircle(cx, cy, r, linePaint)
            }
            for (i in 0..5) {
                val angle = i * 30f
                val rad = Math.toRadians(angle.toDouble())
                val ex = cx + refR * cos(rad).toFloat()
                val ey = cy + refR * sin(rad).toFloat()
                canvas.drawLine(cx, cy, ex, ey, linePaint)
            }

            textureBitmap = tex
        } catch (e: Exception) {
            textureBitmap = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Dark background
        paintBg.color = Color.parseColor("#0A1528")
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paintBg)

        // 2. Top atmosphere glow
        drawAtmosphere(canvas)

        if (globeR <= 0) return

        // 3. Globe sphere — using reference image as texture
        drawGlobe(canvas)

        // 4. Outer glow
        drawOuterGlow(canvas)

        // 5. Stars
        drawStars(canvas)
    }

    private fun drawAtmosphere(canvas: Canvas) {
        paintAtmosphere.shader = RadialGradient(
            w * 0.5f, 0f, w * 0.8f,
            intArrayOf(
                Color.argb(60, 0, 80, 200),
                Color.argb(25, 0, 40, 120),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.35f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paintAtmosphere)
    }

    private fun drawGlobe(canvas: Canvas) {
        val tex = textureBitmap ?: return

        // Draw bitmap with transform: map ref globe to our globe
        val scale = globeR / refR
        val matrix = Matrix()
        matrix.postTranslate(-refCx, -refCy)
        matrix.postScale(scale, scale)
        matrix.postTranslate(globeX, globeY)

        canvas.save()
        // Clip to circular area
        val path = Path().apply {
            addCircle(globeX, globeY, globeR, Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawBitmap(tex, matrix, null)
        canvas.restore()
    }

    private fun drawOuterGlow(canvas: Canvas) {
        // 内层辉光（青色）
        paintInnerGlow.shader = RadialGradient(
            globeX, globeY, globeR * 1.05f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(45, 0, 100, 200),
                Color.argb(18, 0, 50, 130),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.55f, 0.78f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(globeX, globeY, globeR * 1.18f, paintInnerGlow)

        // 外层辉光（蓝色）
        paintOuterGlow.shader = RadialGradient(
            globeX, globeY, globeR * 1.3f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(22, 0, 50, 160),
                Color.argb(10, 0, 25, 90),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.55f, 0.78f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(globeX, globeY, globeR * 1.4f, paintOuterGlow)

        // 第三层极淡辉光
        paintInnerGlow.shader = RadialGradient(
            globeX, globeY, globeR * 1.5f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(12, 0, 30, 110),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.55f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(globeX, globeY, globeR * 1.6f, paintInnerGlow)
    }

    private fun drawStars(canvas: Canvas) {
        val time = System.currentTimeMillis() / 1000f
        for (star in stars) {
            val twinkle = sin(time * 1.5f + star.twinklePhase).toFloat()
            val alpha = (star.baseAlpha + 25 * twinkle).toInt().coerceIn(15, 80)
            paintStar.color = Color.argb(alpha, 200, 220, 255)
            canvas.drawCircle(star.x, star.y, star.radius, paintStar)
        }
    }
}