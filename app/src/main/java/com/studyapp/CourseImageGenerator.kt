package com.studyapp

import android.graphics.*
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

/**
 * 根据课程名称和提示词生成封面图
 * 通过关键词匹配主题，使用 Canvas 绘制与提示词相关的视觉元素（无需联网）
 */
object CourseImageGenerator {

    // ==================== 主题色板 ====================
    // 每个主题对应多组色板，选择更丰富
    private val PALETTES_TECH = arrayOf(
        intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt()),
        intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt()),
        intArrayOf(0xFF48C6EF.toInt(), 0xFF6F86D6.toInt()),
    )
    private val PALETTES_NATURE = arrayOf(
        intArrayOf(0xFF134E5E.toInt(), 0xFF71B280.toInt()),
        intArrayOf(0xFF43E97B.toInt(), 0xFF38F9D7.toInt()),
        intArrayOf(0xFF56AB2F.toInt(), 0xFFA8E063.toInt()),
    )
    private val PALETTES_ART = arrayOf(
        intArrayOf(0xFFDA22FF.toInt(), 0xFF9733EE.toInt()),
        intArrayOf(0xFFF093FB.toInt(), 0xFFF5576C.toInt()),
        intArrayOf(0xFFA18CD1.toInt(), 0xFFFBC2EB.toInt()),
    )
    private val PALETTES_SCIENCE = arrayOf(
        intArrayOf(0xFF4FACFE.toInt(), 0xFF00F2FE.toInt()),
        intArrayOf(0xFFFC5C7D.toInt(), 0xFF6A82FB.toInt()),
        intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt()),
    )
    private val PALETTES_HISTORY = arrayOf(
        intArrayOf(0xFFF5CE67.toInt(), 0xFFF28E6B.toInt()),
        intArrayOf(0xFF8E6B3F.toInt(), 0xFFD4A76A.toInt()),
        intArrayOf(0xFFFCCB90.toInt(), 0xFFD57EEB.toInt()),
    )
    private val PALETTES_MUSIC = arrayOf(
        intArrayOf(0xFFC471F5.toInt(), 0xFFFA71CD.toInt()),
        intArrayOf(0xFFDA22FF.toInt(), 0xFF9733EE.toInt()),
        intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt()),
    )
    private val PALETTES_SPORTS = arrayOf(
        intArrayOf(0xFFFF4B1F.toInt(), 0xFFFF9068.toInt()),
        intArrayOf(0xFFFA709A.toInt(), 0xFFFEE140.toInt()),
        intArrayOf(0xFF43E97B.toInt(), 0xFF38F9D7.toInt()),
    )
    private val PALETTES_DEFAULT = arrayOf(
        intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt()),
        intArrayOf(0xFF4FACFE.toInt(), 0xFF00F2FE.toInt()),
        intArrayOf(0xFF43E97B.toInt(), 0xFF38F9D7.toInt()),
        intArrayOf(0xFFFA709A.toInt(), 0xFFFEE140.toInt()),
        intArrayOf(0xFFFCCB90.toInt(), 0xFFD57EEB.toInt()),
        intArrayOf(0xFF48C6EF.toInt(), 0xFF6F86D6.toInt()),
    )

    // ==================== 关键词 ====================
    private val TECH_KW = listOf("科技", "技术", "编程", "计算机", "程序", "代码", "软件", "网络", "数据", "智能", "AI", "人工智能", "算法", "互联网", "科技感", "数码", "tech", "code", "programming", "computer", "digital", "cyber", "future", "robot")
    private val NATURE_KW = listOf("自然", "山水", "海洋", "森林", "花", "草", "树", "植物", "动物", "风景", "天空", "大地", "河流", "湖泊", "环保", "丛林", "原野", "绿色", "nature", "forest", "ocean", "mountain", "green", "landscape", "garden")
    private val ART_KW = listOf("艺术", "设计", "创意", "美术", "绘画", "时尚", "创造", "艺术感", "art", "design", "creative", "fashion", "modern", "artistic")
    private val SCIENCE_KW = listOf("科学", "数学", "物理", "化学", "生物", "天文", "地理", "实验", "science", "math", "physics", "chemistry", "biology", "lab")
    private val HISTORY_KW = listOf("历史", "文化", "文学", "哲学", "传统", "古典", "古代", "文明", "history", "culture", "literature", "philosophy", "ancient")
    private val MUSIC_KW = listOf("音乐", "乐器", "歌曲", "旋律", "节奏", "钢琴", "吉他", "music", "song", "melody", "piano", "guitar", "rhythm")
    private val SPORTS_KW = listOf("运动", "体育", "健身", "跑步", "篮球", "足球", "游泳", "sport", "fitness", "exercise", "running", "training")

    private val ALL_KW = listOf(TECH_KW, NATURE_KW, ART_KW, SCIENCE_KW, HISTORY_KW, MUSIC_KW, SPORTS_KW)
    private val PALETTES_BY_THEME = listOf(PALETTES_TECH, PALETTES_NATURE, PALETTES_ART, PALETTES_SCIENCE, PALETTES_HISTORY, PALETTES_MUSIC, PALETTES_SPORTS)

    private const val W = 600
    private const val H = 400
    private val rng = java.util.Random()

    fun generate(courseName: String, prompt: String = ""): Bitmap {
        val name = courseName.ifEmpty { "课程" }
        val text = "$prompt $name".lowercase()
        val seed = abs(text.hashCode()).toLong()
        rng.setSeed(seed)

        val themeIdx = analyzeTheme(text)
        val colors = pickColors(themeIdx, seed)

        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. 绘制渐变底色
        drawBaseGradient(canvas, colors)

        // 2. 绘制主题背景底纹
        drawThemePattern(canvas, themeIdx, colors)

        // 3. 绘制主题大号 emoji 装饰
        drawThemeEmoji(canvas, themeIdx)

        // 4. 绘制文字覆盖
        drawText(canvas, name)

        return bitmap
    }

    // ==================== 主题分析 ====================

    private fun analyzeTheme(text: String): Int {
        val scores = ALL_KW.map { it.count { kw -> text.contains(kw) } }
        val best = scores.indices.maxByOrNull { scores[it] }
        return if (best != null && scores[best] > 0) best else -1
    }

    private fun pickColors(themeIdx: Int, seed: Long): IntArray {
        return if (themeIdx < 0) {
            PALETTES_DEFAULT[abs(seed).toInt() % PALETTES_DEFAULT.size]
        } else {
            val pal = PALETTES_BY_THEME[themeIdx]
            pal[abs(seed).toInt() % pal.size]
        }
    }

    // ==================== 底色绘制 ====================

    private fun drawBaseGradient(canvas: Canvas, colors: IntArray) {
        val c0 = colors[0]
        val c1 = if (colors.size > 2) colors[1] else colors[1]
        val c2 = if (colors.size > 2) colors[2] else colors[0]

        // 对角渐变
        val g1 = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), c0, c1, Shader.TileMode.CLAMP)
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = g1 }.let { canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), it) }

        // 叠加半透明径向渐变增加层次
        val g2 = RadialGradient(W * 0.3f, H * 0.2f, 350f, c2, (c0 and 0x00FFFFFF) or (0x55 shl 24), Shader.TileMode.CLAMP)
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = g2 }.let { canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), it) }
    }

    // ==================== 主题底纹 ====================

    private fun drawThemePattern(canvas: Canvas, themeIdx: Int, colors: IntArray) {
        when (themeIdx) {
            0 -> drawCircuitPattern(canvas)
            1 -> drawLeafPattern(canvas)
            2 -> drawColorfulPattern(canvas)
            3 -> drawFormulaPattern(canvas)
            4 -> drawParchmentPattern(canvas)
            5 -> drawNotePattern(canvas)
            6 -> drawStadiumPattern(canvas)
            else -> drawDefaultPattern(canvas)
        }
    }

    private fun drawDefaultPattern(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 14 }
        canvas.drawCircle(W * 0.85f, H * 0.12f, 220f, p)
        canvas.drawCircle(W * 0.15f, H * 0.85f, 160f, p)
        p.alpha = 20
        canvas.drawCircle(W * 0.5f, H * 0.5f, 100f, p)
    }

    // 科技：电路板轨迹
    private fun drawCircuitPattern(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 12; strokeWidth = 2f; style = Paint.Style.STROKE }
        for (i in 0 until 8) {
            val startX = rng.nextFloat() * W * 0.6f + W * 0.1f
            val startY = rng.nextFloat() * H * 0.6f + H * 0.1f
            val path = Path()
            path.moveTo(startX, startY)
            var cx = startX
            var cy = startY
            for (seg in 0 until 4) {
                // 直角转弯（电路风格）
                if (rng.nextBoolean()) {
                    cx += (rng.nextFloat() * 100f + 30f) * (if (rng.nextBoolean()) 1f else -1f)
                } else {
                    cy += (rng.nextFloat() * 100f + 30f) * (if (rng.nextBoolean()) 1f else -1f)
                }
                path.lineTo(cx, cy)
                // 拐点画个小圆点（焊点）
                if (seg < 3) {
                    p.style = Paint.Style.FILL
                    p.alpha = 20
                    canvas.drawCircle(cx, cy, 3f, p)
                    p.style = Paint.Style.STROKE
                    p.alpha = 12
                }
            }
            p.alpha = (8 + i * 2).coerceIn(6, 22)
            canvas.drawPath(path, p)
        }
    }

    // 自然：树叶/波浪
    private fun drawLeafPattern(canvas: Canvas) {
        // 弧线（山脉/波浪）
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 10; style = Paint.Style.STROKE; strokeWidth = 2f }
        for (w in 0 until 4) {
            val path = Path()
            val baseY = H * (0.15f + w * 0.2f)
            val amp = 15f + w * 10f
            path.moveTo(0f, baseY)
            for (x in 0..W step 8) {
                val y = baseY + sin(x.toDouble() / 60.0 + w * 1.5) * amp
                path.lineTo(x.toFloat(), y.toFloat())
            }
            p.alpha = (8 + w * 3).coerceIn(6, 22)
            canvas.drawPath(path, p)
        }
        // 散落的小点（花瓣/叶子）
        val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 16 }
        for (i in 0 until 15) {
            dotP.alpha = (8 + rng.nextInt(20)).coerceIn(6, 30)
            canvas.drawCircle(rng.nextFloat() * W, rng.nextFloat() * H, 3f + rng.nextFloat() * 5f, dotP)
        }
    }

    // 艺术：多彩重叠圆圈
    private fun drawColorfulPattern(canvas: Canvas) {
        val tints = intArrayOf(
            Color.argb(18, 255, 200, 200),
            Color.argb(14, 200, 255, 200),
            Color.argb(16, 200, 200, 255),
            Color.argb(12, 255, 255, 200),
            Color.argb(10, 255, 200, 255),
        )
        for (i in 0 until 6) {
            val cx = rng.nextFloat() * W * 0.9f + W * 0.05f
            val cy = rng.nextFloat() * H * 0.9f + H * 0.05f
            val r = 30f + rng.nextFloat() * 80f
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tints[rng.nextInt(tints.size)]
            }.let { canvas.drawCircle(cx, cy, r, it) }
        }
    }

    // 科学：几何图形 + 圆形
    private fun drawFormulaPattern(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 10; style = Paint.Style.STROKE; strokeWidth = 1.5f }
        for (i in 0 until 6) {
            val cx = rng.nextFloat() * W * 0.8f + W * 0.1f
            val cy = rng.nextFloat() * H * 0.8f + H * 0.1f
            val r = 20f + rng.nextFloat() * 40f
            // 圆
            p.alpha = 12
            canvas.drawCircle(cx, cy, r, p)
            // 内接三角形
            val tri = Path()
            for (j in 0 until 3) {
                val a = j * (2 * PI / 3) - PI / 2
                val tx = cx + cos(a).toFloat() * r
                val ty = cy + sin(a).toFloat() * r
                if (j == 0) tri.moveTo(tx, ty) else tri.lineTo(tx, ty)
            }
            tri.close()
            p.alpha = 10
            canvas.drawPath(tri, p)
        }
    }

    // 历史：水平线条 + 竖线（古典柱式）
    private fun drawParchmentPattern(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 10; strokeWidth = 2f }
        // 横线
        for (i in 0 until 6) {
            val y = H * (0.12f + i * 0.14f)
            p.alpha = (12 - i).coerceIn(4, 16)
            p.strokeWidth = 1.5f + i * 0.4f
            canvas.drawLine(W * 0.08f, y, W * 0.92f, y, p)
        }
        // 竖线（柱式）
        val vp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 8; strokeWidth = 3f }
        for (i in 0 until 3) {
            val x = W * (0.2f + i * 0.3f)
            canvas.drawLine(x, H * 0.1f, x, H * 0.9f, vp)
        }
    }

    // 音乐：五线谱 + 音符
    private fun drawNotePattern(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 8; strokeWidth = 1.2f }
        // 五线谱
        for (line in 0 until 5) {
            val y = H * 0.3f + line * 10f
            canvas.drawLine(W * 0.1f, y, W * 0.9f, y, p)
        }
        // 波浪旋律
        val melody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 14; style = Paint.Style.STROKE; strokeWidth = 2f }
        val path = Path()
        path.moveTo(W * 0.1f, H * 0.35f)
        for (x in 0..(W - 60) step 6) {
            val y = H * 0.35f + sin(x.toDouble() / 50.0) * 25f + sin(x.toDouble() / 25.0) * 8f
            path.lineTo(W * 0.1f + x.toFloat(), y.toFloat())
        }
        canvas.drawPath(path, melody)
    }

    // 运动：放射线 + 弧线
    private fun drawStadiumPattern(canvas: Canvas) {
        val cx = W / 2f
        val cy = H * 0.4f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 10; strokeWidth = 1.5f; style = Paint.Style.STROKE }
        // 放射
        for (i in 0 until 10) {
            val angle = i * (PI / 5)
            val ex = cx + cos(angle).toFloat() * 180f
            val ey = cy + sin(angle).toFloat() * 180f
            p.alpha = (8 + (i * 3) % 18).coerceIn(6, 25)
            canvas.drawLine(cx, cy, ex, ey, p)
        }
        // 同心圆
        val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 12; style = Paint.Style.STROKE; strokeWidth = 1.5f }
        canvas.drawCircle(cx, cy, 30f, cp)
        canvas.drawCircle(cx, cy, 55f, cp)
    }

    // ==================== 主题 Emoji 装饰 ====================

    private fun drawThemeEmoji(canvas: Canvas, themeIdx: Int) {
        val pairs = when (themeIdx) {
            0 -> arrayOf("💻" to 90f, "⚙️" to 70f, "🔧" to 60f)
            1 -> arrayOf("🌿" to 100f, "🌳" to 80f, "🌸" to 65f)
            2 -> arrayOf("🎨" to 90f, "✏️" to 70f, "✨" to 60f)
            3 -> arrayOf("🔬" to 80f, "📐" to 70f, "⚗️" to 60f)
            4 -> arrayOf("📜" to 80f, "🏛️" to 75f, "📖" to 65f)
            5 -> arrayOf("🎵" to 90f, "🎶" to 80f, "🎹" to 65f)
            6 -> arrayOf("⚽" to 85f, "🏀" to 75f, "🏃" to 65f)
            else -> return // 默认主题不画 emoji
        }

        // 在画面不同位置绘制大号 emoji
        val positions = arrayOf(
            floatArrayOf(W * 0.18f, H * 0.22f),
            floatArrayOf(W * 0.82f, H * 0.20f),
            floatArrayOf(W * 0.10f, H * 0.80f),
        )

        for (i in pairs.indices) {
            val (emoji, size) = pairs[i]
            val pos = positions.getOrElse(i) { floatArrayOf(W * 0.5f, H * 0.5f) }
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                textAlign = Paint.Align.CENTER
                alpha = (30 - i * 8).coerceIn(12, 35)
                isAntiAlias = true
            }
            canvas.drawText(emoji, pos[0], pos[1] + size * 0.35f, p)
        }
    }

    // ==================== 文字叠加 ====================

    private fun drawText(canvas: Canvas, name: String) {
        val cx = W / 2f
        val cy = H / 2f

        // 首字装饰（大字背景）
        val first = name.first().toString()
        val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 22; textSize = 220f
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(first, cx, cy + 80f, bgP)

        // 标题
        val title = if (name.length > 10) name.substring(0, 10) + "..." else name
        val tP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 38f
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 2f, Color.argb(80, 0, 0, 0))
        }
        if (tP.measureText(title) > W * 0.8f) tP.textSize = 30f
        canvas.drawText(title, cx, cy + 12f, tP)

        // 副标题
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 160; textSize = 14f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }.let { canvas.drawText("COURSE", cx, cy + 60f, it) }
    }
}
