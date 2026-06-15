package com.studyapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyapp.manager.ApiService
import com.studyapp.ui.components.HudDivider
import com.studyapp.ui.components.HudRankIcon
import com.studyapp.ui.components.MonitorGlassCard
import com.studyapp.ui.components.ScanlineOverlay
import com.studyapp.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────

private data class MonitorData(
    val onlineStudents: Int = 0,
    val onlineTeachers: Int = 0,
    val totalCourses: Int = 0,
    val activeUsers: Int = 0,
    val totalWatchTime: String = "",
    val studentRankings: List<RankItem> = emptyList(),
    val courseRankings: List<RankItem> = emptyList(),
    val lastRefresh: String = ""
)

private data class RankItem(
    val rank: Int,
    val name: String,
    val value: String,
    val progress: Float = 0f
)

private data class Particle(
    val x: Float, val y: Float, val size: Float, val alpha: Float, val speed: Float
)

// ─────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────

@Composable
fun MonitorScreen(
    username: String,
    apiService: ApiService,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(MonitorData()) }
    var loading by remember { mutableStateOf(true) }

    val visibleCards = remember { mutableStateListOf(false, false, false, false) }
    var showRankings by remember { mutableStateOf(false) }

    fun loadData() {
        loading = true
        scope.launch {
            val onlineResult = withContext(Dispatchers.IO) {
                try { apiService.getOnlineUsers() } catch (_: Exception) { null }
            }
            val masteryResult = withContext(Dispatchers.IO) {
                try { apiService.getCourseMasteryStats(20) } catch (_: Exception) { null }
            }
            val learningResult = withContext(Dispatchers.IO) {
                try { apiService.getLearningStats(20) } catch (_: Exception) { null }
            }
            val activityResult = withContext(Dispatchers.IO) {
                try { apiService.getActivityRanking(7, 20) } catch (_: Exception) { null }
            }

            var students = 0; var teachers = 0; var courses = 0; var active = 0
            val courseRanks = mutableListOf<RankItem>()
            val studentRanks = mutableListOf<RankItem>()
            var watchTime = ""
            val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(java.util.Date())

            if (onlineResult?.isSuccess == true) {
                val users = onlineResult.getOrNull()?.onlineUsers ?: emptyList()
                students = users.count { it.role.lowercase() == "student" }
                teachers = users.count { it.role.lowercase() == "teacher" }
            }
            if (masteryResult?.isSuccess == true) {
                val stats = masteryResult.getOrNull()?.stats
                val items = masteryResult.getOrNull()?.courses ?: emptyList()
                courses = stats?.totalCourses ?: 0
                val sorted = items.sortedByDescending { it.totalStudents }.take(5)
                val maxStudents = sorted.firstOrNull()?.totalStudents?.toFloat()?.coerceAtLeast(1f) ?: 1f
                for ((idx, item) in sorted.withIndex()) {
                    courseRanks.add(RankItem(idx + 1, item.courseName, "${item.totalStudents}人", item.totalStudents / maxStudents))
                }
            }
            if (learningResult?.isSuccess == true) {
                val stats = learningResult.getOrNull()?.stats
                val items = learningResult.getOrNull()?.students ?: emptyList()
                val totalMinutes = stats?.totalWatchTime ?: 0
                watchTime = if (totalMinutes >= 60) "${totalMinutes / 60}h${totalMinutes % 60}m" else "${totalMinutes}分钟"
                val sorted = items.sortedByDescending { it.averageProgress }.take(5)
                for ((idx, item) in sorted.withIndex()) {
                    studentRanks.add(RankItem(
                        idx + 1, item.username,
                        String.format("%.1f%%", item.averageProgress),
                        (item.averageProgress / 100f).toFloat().coerceIn(0f, 1f)
                    ))
                }
            }
            if (activityResult?.isSuccess == true) {
                active = activityResult.getOrNull()?.stats?.activityDistribution?.high ?: 0
            }

            data = MonitorData(students, teachers, courses, active, watchTime, studentRanks, courseRanks, now)
            loading = false

            visibleCards.forEachIndexed { index, _ ->
                kotlinx.coroutines.delay(100); visibleCards[index] = true
            }
            kotlinx.coroutines.delay(200); showRankings = true
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Box(
        modifier = Modifier.fillMaxSize().background(SciFiBackground)
    ) {
        // ===== 1. HUD 动态背景 =====
        HudBackground(modifier = Modifier.fillMaxSize())

        // ===== 2. CRT 扫描线 =====
        ScanlineOverlay(modifier = Modifier.fillMaxSize())

        // ===== 3. 顶部渐变遮罩 =====
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp).background(
                Brush.verticalGradient(listOf(SciFiBackground, Color.Transparent))
            )
        )

        // ===== 4. HUD 四角括号 =====
        HudCornerBrackets(modifier = Modifier.fillMaxSize())

        // ===== 5. 主内容 =====
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            // --- 标题栏 ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Text("◀", color = HudCyan, fontSize = 18.sp, fontFamily = FiraCode)
                }
                Spacer(Modifier.width(8.dp))

                Text(
                    text = "监控中心",
                    style = MaterialTheme.typography.titleLarge,
                    color = SciFiTextPrimary
                )
                Text(
                    text = " v2.0",
                    fontSize = 10.sp,
                    color = HudCyan.copy(alpha = 0.5f),
                    fontFamily = FiraCode,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = username.take(1),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = HudCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(HudCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            HudDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- 4 个指标卡片 ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedCard(visible = visibleCards[0], modifier = Modifier.weight(1f)) {
                    MonitorGlassCard(
                        label = "在线学生", value = animatedInt(data.onlineStudents),
                        valueColor = HudBlue, glowColor = SciFiGlowBlue
                    )
                }
                AnimatedCard(visible = visibleCards[1], modifier = Modifier.weight(1f)) {
                    MonitorGlassCard(
                        label = "在线教师", value = animatedInt(data.onlineTeachers),
                        valueColor = HudGreen, glowColor = SciFiGlowCyan
                    )
                }
                AnimatedCard(visible = visibleCards[2], modifier = Modifier.weight(1f)) {
                    MonitorGlassCard(
                        label = "课程总数", value = animatedInt(data.totalCourses),
                        valueColor = SciFiGold, glowColor = SciFiGlowCyan
                    )
                }
                AnimatedCard(visible = visibleCards[3], modifier = Modifier.weight(1f)) {
                    MonitorGlassCard(
                        label = "活跃用户", value = animatedInt(data.activeUsers),
                        valueColor = SciFiPurple, glowColor = SciFiGlowBlue
                    )
                }
            }

            HudDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- 排行榜区域 ---
            AnimatedVisibility(
                visible = showRankings,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 2 }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RankingPanel(
                        modifier = Modifier.weight(1f), title = "选课人数排行",
                        items = data.courseRankings, barColor = HudBlue
                    )
                    RankingPanel(
                        modifier = Modifier.weight(1f), title = "完成率排行",
                        items = data.studentRankings, barColor = HudGreen
                    )
                }
            }

            // --- 底部信息 ---
            if (data.lastRefresh.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("最后刷新: ${data.lastRefresh}", style = MaterialTheme.typography.labelSmall, color = SciFiTextMuted)
                    Text("总计观看: ${data.totalWatchTime}", style = MaterialTheme.typography.labelSmall, color = SciFiTextMuted)
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        // ===== 6. 加载状态 =====
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "正在连接数据终端...",
                    style = MaterialTheme.typography.bodySmall,
                    color = SciFiTextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SciFiGlassLight)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// HUD 动态背景（含极坐标网格 + 雷达扫描）
// ─────────────────────────────────────────────

@Composable
private fun HudBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "hudBg")

    val gradientPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "gradientPhase"
    )
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "scanLine"
    )
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "radarAngle"
    )
    val orbAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb1"
    )
    val orbAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb2"
    )

    val particles = remember {
        val rng = Random(42)
        List(100) {
            Particle(
                x = rng.nextFloat(), y = rng.nextFloat(),
                size = rng.nextFloat() * 2.2f + 0.5f,
                alpha = rng.nextFloat() * 0.6f + 0.2f,
                speed = rng.nextFloat() * 0.8f + 0.3f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val c = Offset(w / 2f, h / 2f)
        val maxDim = maxOf(w, h)
        val radarRad = maxDim * 0.55f

        // 1. 深空径向渐变
        val gx = w * 0.5f + sin(gradientPhase * 0.003f) * w * 0.1f
        val gy = h * 0.3f + cos(gradientPhase * 0.002f) * h * 0.05f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0A1628), Color(0xFF020617)),
                center = Offset(gx, gy), radius = maxDim * 1.3f
            )
        )

        // 2. 极坐标网格（同心圆 + 射线）
        val gridAlpha = 0.06f + sin(gradientPhase * 0.005f) * 0.02f
        val gridColor = HudCyan.copy(alpha = gridAlpha)
        // 同心圆
        for (i in 1..4) {
            val radius = radarRad * (i / 4f)
            drawCircle(color = gridColor, radius = radius, center = c, style = Stroke(width = 0.5f))
        }
        // 径向射线（12 条）
        for (i in 0..11) {
            val angle = Math.toRadians((30.0 * i).toDouble())
            val ex = c.x + radarRad * cos(angle).toFloat()
            val ey = c.y + radarRad * sin(angle).toFloat()
            drawLine(gridColor, c, Offset(ex, ey), strokeWidth = 0.5f)
        }

        // 3. 光晕
        drawCircle(
            brush = Brush.radialGradient(listOf(SciFiGlowBlue.copy(alpha = orbAlpha1), SciFiGlowBlue.copy(alpha = orbAlpha1 * 0.3f), Color.Transparent)),
            radius = w * 0.45f, center = Offset(w * 0.2f, h * 0.25f)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(SciFiGlowCyan.copy(alpha = orbAlpha2), SciFiGlowCyan.copy(alpha = orbAlpha2 * 0.3f), Color.Transparent)),
            radius = w * 0.38f, center = Offset(w * 0.82f, h * 0.72f)
        )

        // 4. 雷达扫描扇形
        val radarRadians = Math.toRadians(radarAngle.toDouble())
        val scanX = c.x + radarRad * cos(radarRadians).toFloat()
        val scanY = c.y + radarRad * sin(radarRadians).toFloat()
        // 扫描线
        drawLine(
            color = HudCyan.copy(alpha = 0.25f),
            start = c, end = Offset(scanX, scanY), strokeWidth = 1.5f
        )
        // 扇形扫描渐变
        val sweepAngle = 30f
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Transparent,
                    HudCyan.copy(alpha = 0.08f),
                    HudCyan.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = c
            ),
            startAngle = radarAngle - sweepAngle,
            sweepAngle = sweepAngle * 2f,
            useCenter = true,
            topLeft = Offset(c.x - radarRad, c.y - radarRad),
            size = Size(radarRad * 2f, radarRad * 2f)
        )

        // 5. 粒子群
        val twinklePhase = gradientPhase * 0.015f
        for (p in particles) {
            val twinkle = sin(twinklePhase + p.x * 12f + p.y * 7f) * 0.35f + 0.65f
            drawCircle(
                color = Color.White.copy(alpha = p.alpha * twinkle),
                radius = p.size, center = Offset(p.x * w, p.y * h)
            )
        }

        // 6. 水平扫描线
        val hScanY = scanLineProgress * h
        drawLine(
            color = HudCyan.copy(alpha = 0.06f),
            start = Offset(0f, hScanY), end = Offset(w, hScanY), strokeWidth = 1.5f
        )
        drawLine(
            color = HudCyan.copy(alpha = 0.015f),
            start = Offset(0f, hScanY - 25f), end = Offset(w, hScanY - 25f), strokeWidth = 50f
        )
    }
}

// ─────────────────────────────────────────────
// HUD 四角括号
// ─────────────────────────────────────────────

@Composable
private fun HudCornerBrackets(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "hudBrackets")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bracketAlpha"
    )

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val len = 30.dp.toPx(); val off = 12.dp.toPx()
        val color = HudCyan.copy(alpha = alpha)

        fun corner(ox: Float, oy: Float, dx: Int, dy: Int) {
            val x = ox + off * if (dx >= 0) 0f else w - 2f * off
            val y = oy + off * if (dy >= 0) 0f else h - 2f * off
            val cx = w - x - off
            val cy = h - y - off
            drawLine(color, Offset(x, y + len), Offset(x, y), strokeWidth = 1f)
            drawLine(color, Offset(x, y), Offset(x + len, y), strokeWidth = 1f)
        }
        // Simplified: draw 4 corners manually
        // TL
        drawLine(color, Offset(off, off + len), Offset(off, off), strokeWidth = 1f)
        drawLine(color, Offset(off, off), Offset(off + len, off), strokeWidth = 1f)
        // TR
        drawLine(color, Offset(w - off, off + len), Offset(w - off, off), strokeWidth = 1f)
        drawLine(color, Offset(w - off - len, off), Offset(w - off, off), strokeWidth = 1f)
        // BL
        drawLine(color, Offset(off, h - off - len), Offset(off, h - off), strokeWidth = 1f)
        drawLine(color, Offset(off, h - off), Offset(off + len, h - off), strokeWidth = 1f)
        // BR
        drawLine(color, Offset(w - off, h - off - len), Offset(w - off, h - off), strokeWidth = 1f)
        drawLine(color, Offset(w - off - len, h - off), Offset(w - off, h - off), strokeWidth = 1f)
    }
}

// ─────────────────────────────────────────────
// Rank Panels & Rows
// ─────────────────────────────────────────────

@Composable
private fun AnimatedCard(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600)) + scaleIn(initialScale = 0.8f, animationSpec = tween(600))
    ) {
        Box(modifier) { content() }
    }
}

@Composable
private fun RankingPanel(
    modifier: Modifier = Modifier, title: String,
    items: List<RankItem>, barColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SciFiGlassLight)
            .border(0.5.dp, HudCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(3.dp, 12.dp).background(barColor))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = SciFiTextSecondary)
            }
            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = SciFiTextMuted,
                    modifier = Modifier.padding(vertical = 8.dp))
            } else {
                items.forEachIndexed { idx, item ->
                    RankingRow(rank = item.rank, name = item.name, value = item.value,
                        progress = item.progress, barColor = barColor)
                    if (idx < items.size - 1) Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun RankingRow(
    rank: Int, name: String, value: String,
    progress: Float, barColor: Color
) {
    val animProgress by animateFloatAsState(
        targetValue = if (progress > 0) progress else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "rankBar"
    )

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // ★ HUD 排名图标（替代 emoji）
            HudRankIcon(rank = rank, iconSize = 18.dp)
            Spacer(Modifier.width(6.dp))
            // 名称
            Text(
                text = name, maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = SciFiTextPrimary.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            // 数值
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = barColor
            )
        }
        Spacer(Modifier.height(2.dp))
        // 进度条
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(SciFiGlassLight)
        ) {
            Box(
                Modifier.fillMaxWidth(animProgress).height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.5f), barColor)))
            )
        }
    }
}

// ─────────────────────────────────────────────
// Animated Integer Formatting
// ─────────────────────────────────────────────

@Composable
private fun animatedInt(target: Int): String {
    val animatedValue by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "counter"
    )
    return animatedValue.toInt().toString()
}
