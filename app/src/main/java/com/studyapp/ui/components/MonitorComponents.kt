package com.studyapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyapp.ui.theme.*
import androidx.compose.material3.Text
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────
// 1. HUD 排名图标（Canvas 绘制，替代 emoji）
// ─────────────────────────────────────────────

/**
 * HUD 风格排名序号图标
 * 第1名: 金色六边形
 * 第2名: 银色圆形
 * 第3名: 铜色倒三角
 * 其他: 灰色菱形
 */
@Composable
fun HudRankIcon(
    rank: Int,
    iconSize: Dp = 20.dp
) {
    val color = when (rank) {
        1 -> Color(0xFFFFD700)   // 金
        2 -> Color(0xFFC0C0C0)   // 银
        3 -> Color(0xFFCD7F32)   // 铜
        else -> SciFiTextMuted
    }

    Canvas(modifier = Modifier.size(iconSize)) {
        val s = this.size // DrawScope size
        val cx = s.width / 2f
        val cy = s.height / 2f
        val r = s.width / 2f - 1.dp.toPx()

        when (rank) {
            1 -> {
                // 六边形
                val path = androidx.compose.ui.graphics.Path().apply {
                    for (i in 0..5) {
                        val angle = Math.toRadians((60.0 * i - 30.0))
                        val px = cx + r * cos(angle).toFloat()
                        val py = cy + r * sin(angle).toFloat()
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(path, color = color, style = Stroke(width = 1.5f))
                drawPath(path, color = color.copy(alpha = 0.15f))
                // 内部光晕
                drawCircle(color = color.copy(alpha = 0.3f), radius = r * 0.4f, center = Offset(cx, cy))
            }
            2 -> {
                // 圆形
                drawCircle(color = color, radius = r, style = Stroke(width = 1.5f))
                drawCircle(color = color.copy(alpha = 0.15f), radius = r)
                drawCircle(color = color.copy(alpha = 0.3f), radius = r * 0.4f, center = Offset(cx, cy))
            }
            3 -> {
                // 倒三角
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy + r)
                    lineTo(cx - r * 0.87f, cy - r * 0.5f)
                    lineTo(cx + r * 0.87f, cy - r * 0.5f)
                    close()
                }
                drawPath(path, color = color, style = Stroke(width = 1.5f))
                drawPath(path, color = color.copy(alpha = 0.15f))
            }
            else -> {
                // 菱形
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r * 0.7f, cy)
                    lineTo(cx, cy + r)
                    lineTo(cx - r * 0.7f, cy)
                    close()
                }
                drawPath(path, color = color, style = Stroke(width = 1.2f))
                drawPath(path, color = color.copy(alpha = 0.1f))
            }
        }

        // 排名数字
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                this.color = color.hashCode()
                textSize = s.width * 0.45f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.MONOSPACE
                isAntiAlias = true
            }
            drawText("$rank", cx, cy + s.width * 0.16f, paint)
        }
    }
}

// ─────────────────────────────────────────────
// 2. 环形进度图（Donut Chart）
// ─────────────────────────────────────────────

/**
 * HUD 风格环形进度图
 * @param progress 0f..1f
 * @param color 弧线颜色
 * @param trackColor 轨道颜色
 * @param strokeWidth 弧线宽度
 * @param animated 是否启用动画
 */
@Composable
fun DonutChart(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = HudCyan,
    trackColor: Color = SciFiGlassLight,
    strokeWidth: Float = 8f,
    animated: Boolean = true,
    label: String = "",
    value: String = ""
) {
    val animProgress by animateFloatAsState(
        targetValue = if (animated) progress.coerceIn(0f, 1f) else progress.coerceIn(0f, 1f),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "donutProgress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val halfStroke = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(halfStroke, halfStroke)

            // 轨道
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 进度弧
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(color.copy(alpha = 0.5f), color),
                    center = Offset(size.width / 2f, size.height / 2f)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 起点圆点
            val startAngleRad = Math.toRadians(-90.0)
            val sx = size.width / 2f + (size.width / 2f - halfStroke) * cos(startAngleRad).toFloat()
            val sy = size.height / 2f + (size.height / 2f - halfStroke) * sin(startAngleRad).toFloat()
            drawCircle(color = color, radius = strokeWidth / 3f, center = Offset(sx, sy))
        }

        // 中心文字
        if (value.isNotEmpty() || label.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (value.isNotEmpty()) {
                    Text(
                        text = value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = com.studyapp.ui.theme.FiraCode,
                        color = color
                    )
                }
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        fontSize = 8.sp,
                        fontFamily = com.studyapp.ui.theme.FiraSans,
                        color = SciFiTextMuted
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// 3. 科幻风格玻璃态卡片（增强版）
// ─────────────────────────────────────────────

/**
 * HUD 玻璃态指标卡片
 * 半透明背景 + 呼吸发光边框 + 四角装饰
 */
@Composable
fun MonitorGlassCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color = SciFiCyan,
    glowColor: Color = SciFiGlowCyan,
    unit: String = "",
    showHoloBorder: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        SciFiGlassLight,
                        Color(0x0A0080FF) // 极淡蓝色
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = glowAlpha * 0.5f),
                        glowColor.copy(alpha = glowAlpha),
                        glowColor.copy(alpha = glowAlpha * 0.5f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        // 全息装饰 — 四角标记 + 虚线边框
        if (showHoloBorder) {
            val dashAlpha = 0.3f * (glowAlpha / 0.9f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cornerLen = 14.dp.toPx()
                val off = 4.dp.toPx()
                val c = HudCyan.copy(alpha = dashAlpha)

                // 四角 L 形标记（更精致的小尺寸）
                // 左上
                drawLine(c, Offset(off, off + cornerLen), Offset(off, off), strokeWidth = 1.2f)
                drawLine(c, Offset(off, off), Offset(off + cornerLen, off), strokeWidth = 1.2f)
                // 右上
                drawLine(c, Offset(size.width - off, off + cornerLen), Offset(size.width - off, off), strokeWidth = 1.2f)
                drawLine(c, Offset(size.width - off - cornerLen, off), Offset(size.width - off, off), strokeWidth = 1.2f)
                // 左下
                drawLine(c, Offset(off, size.height - off - cornerLen), Offset(off, size.height - off), strokeWidth = 1.2f)
                drawLine(c, Offset(off, size.height - off), Offset(off + cornerLen, size.height - off), strokeWidth = 1.2f)
                // 右下
                drawLine(c, Offset(size.width - off, size.height - off - cornerLen), Offset(size.width - off, size.height - off), strokeWidth = 1.2f)
                drawLine(c, Offset(size.width - off - cornerLen, size.height - off), Offset(size.width - off, size.height - off), strokeWidth = 1.2f)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 数值（Fira Code 等宽）
            Text(
                text = "$value$unit",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = com.studyapp.ui.theme.FiraCode,
                color = valueColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            // 标签（Fira Sans）
            Text(
                text = label,
                fontSize = 10.sp,
                fontFamily = com.studyapp.ui.theme.FiraSans,
                fontWeight = FontWeight.Light,
                color = SciFiTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            content()
        }
    }
}

// ─────────────────────────────────────────────
// 4. 扫描线动画覆盖层
// ─────────────────────────────────────────────

@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineSpacing = 4.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.White.copy(alpha = 0.025f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5f
            )
            y += lineSpacing
        }
    }
}

// ─────────────────────────────────────────────
// 5. HUD 装饰分割线
// ─────────────────────────────────────────────

@Composable
fun HudDivider(
    modifier: Modifier = Modifier,
    color: Color = HudCyan.copy(alpha = 0.25f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hudDivider")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dividerAlpha"
    )

    Canvas(
        modifier = modifier.height(1.5.dp).fillMaxWidth()
    ) {
        drawRoundRect(
            color = color.copy(alpha = alpha),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
        // 中间亮点
        drawCircle(
            color = color.copy(alpha = alpha * 2f),
            radius = 2.dp.toPx(),
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}
