package com.studyapp

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.studyapp.manager.ApiService
import com.studyapp.model.Note
import com.studyapp.util.ImageLoaderUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 教师 / 管理员 查看「学生笔记」（内嵌面板版，左侧栏常驻）
 *
 * 三级浏览：学生 → 课程 → 笔记（纯阅读，无跳转播放按钮）。
 * 数据范围由后端按 token 角色决定：教师=自己课程里学生写的笔记，管理员=全部学生。
 * 页面内「← 返回...」文本链接负责逐级返回；宿主也可调用 [handleBack]。
 */
class StudentNotesFragment : Fragment() {

    // ==================== 视图 ====================
    private lateinit var scrollRoot: NestedScrollView
    private lateinit var noteRows: LinearLayout
    private lateinit var emptyText: TextView

    private lateinit var apiService: ApiService

    // ==================== 数据 ====================
    /** 学生 id → 该生全部笔记；顺序 = 该生最新一条笔记 updated_at 降序（接口即按 updated_at DESC 返回） */
    private val studentNotes: LinkedHashMap<Int, MutableList<Note>> = LinkedHashMap()
    /** 0 = 未选中：当前显示学生层；>0 = 正在浏览某学生的课程 */
    private var currentStudentId = 0
    /** 0 = 未选中：当前显示课程层；>0 = 正在浏览某课程的笔记 */
    private var currentCourseId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = ApiService.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_student_notes, container, false)
        initViews(root)
        loadNotes()
        return root
    }

    private fun initViews(root: View) {
        scrollRoot = root.findViewById(R.id.notesScrollRoot)
        noteRows = root.findViewById(R.id.noteRows)
        emptyText = root.findViewById(R.id.emptyText)

        // 角色说明文案（数据范围后端决定，这里仅作提示）
        val prefs = requireContext().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val role = prefs.getString("role", null)
        val caption = root.findViewById<TextView>(R.id.roleCaption)
        caption.text = if (role == "admin")
            "查看全部学生的笔记 · 学生 → 课程 → 笔记"
        else
            "查看我课程中学生记录的笔记 · 学生 → 课程 → 笔记"

        root.findViewById<ImageView>(R.id.notesRefreshButton).setOnClickListener { loadNotes() }
    }

    private fun noteDp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmtNoteTime(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%d:%02d", m, sec)
    }

    // ==================== 数据加载 ====================

    private fun loadNotes() {
        viewLifecycleOwner.lifecycleScope.launch {
            currentStudentId = 0
            currentCourseId = 0
            scrollRoot.scrollTo(0, 0)
            noteRows.removeAllViews()
            emptyText.visibility = View.GONE
            val result = withContext(Dispatchers.IO) { apiService.getStudentNotes() }
            if (result.isFailure) {
                emptyText.text = "加载学生笔记失败，请稍后重试"
                emptyText.visibility = View.VISIBLE
                return@launch
            }
            val notes = result.getOrNull().orEmpty()
            if (notes.isEmpty()) {
                emptyText.text = "还没有学生写过笔记"
                emptyText.visibility = View.VISIBLE
                return@launch
            }
            studentNotes.clear()
            notes.forEach { note ->
                studentNotes.getOrPut(note.userId) { mutableListOf() }.add(note)
            }
            renderStudentList()
        }
    }

    // ---------------- 第一层：学生 ----------------

    private fun renderStudentList() {
        val ctx = requireContext()
        currentStudentId = 0
        currentCourseId = 0
        scrollRoot.scrollTo(0, 0)
        noteRows.removeAllViews()
        emptyText.visibility = View.GONE

        val entries = studentNotes.entries.toList()
        entries.forEachIndexed { index, (_, list) ->
            noteRows.addView(buildStudentCard(ctx, list))
            if (index < entries.size - 1) {
                noteRows.addView(makeDivider(ctx))
            }
        }
    }

    /** 一个学生 = 一行横排卡：头像 + 用户名 + 课程数/笔记数 */
    private fun buildStudentCard(ctx: Context, list: List<Note>): View {
        val head = list.firstOrNull()
        val name = head?.userName ?: "未知学生"
        val courseCount = list.map { it.courseId }.toSet().size

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, noteDp(10), 0, noteDp(10))
        row.isClickable = true
        row.isFocusable = true
        row.background = ctx.getDrawable(R.drawable.sidebar_item_selector)

        // 左侧：圆形头像（有 avatarUrl 显示头像，无则首字）
        val avatarFrame = FrameLayout(ctx)
        avatarFrame.background = ctx.getDrawable(R.drawable.circle_avatar)
        avatarFrame.clipToOutline = true
        val avatarSize = noteDp(46)
        row.addView(avatarFrame, LinearLayout.LayoutParams(avatarSize, avatarSize))

        val initial = TextView(ctx)
        initial.text = name.trim().take(1).ifBlank { "生" }
        initial.textSize = 18f
        initial.setTypeface(null, Typeface.BOLD)
        initial.setTextColor(resources.getColor(R.color.tsinghua_purple))
        initial.gravity = Gravity.CENTER
        avatarFrame.addView(initial, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val avatarUrl = head?.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            val avatarImage = ImageView(ctx)
            avatarImage.scaleType = ImageView.ScaleType.CENTER_CROP
            avatarFrame.addView(avatarImage, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val fullUrl = if (avatarUrl.startsWith("http")) avatarUrl else "${ApiService.BASE_URL}$avatarUrl"
            ImageLoaderUtil.load(avatarImage, fullUrl, crossfade = true, circleCrop = true)
        }

        // 右侧：用户名 + 课程/笔记数
        val info = LinearLayout(ctx)
        info.orientation = LinearLayout.VERTICAL
        info.gravity = Gravity.CENTER_VERTICAL
        info.setPadding(noteDp(14), 0, 0, 0)
        row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val nameText = TextView(ctx)
        nameText.text = name
        nameText.textSize = 16f
        nameText.setTypeface(null, Typeface.BOLD)
        nameText.setTextColor(resources.getColor(R.color.tsinghua_purple_dark))
        nameText.maxLines = 1
        nameText.ellipsize = TextUtils.TruncateAt.END
        info.addView(nameText)

        val metaText = TextView(ctx)
        metaText.text = "📚 $courseCount 门课程 · ✏️ ${list.size} 条笔记"
        metaText.textSize = 13f
        metaText.setTextColor(0xFF999999.toInt())
        metaText.setPadding(0, noteDp(3), 0, 0)
        info.addView(metaText)

        val chevron = TextView(ctx)
        chevron.text = "›"
        chevron.textSize = 24f
        chevron.setTextColor(0xFFCCCCCC.toInt())
        chevron.setPadding(noteDp(10), 0, 0, 0)
        row.addView(chevron)

        val studentId = head?.userId ?: 0
        row.setOnClickListener {
            currentStudentId = studentId
            currentCourseId = 0
            renderStudentCourses()
        }
        return row
    }

    // ---------------- 第二层：某学生的课程 ----------------

    private fun renderStudentCourses() {
        val ctx = requireContext()
        val list = studentNotes[currentStudentId].orEmpty()
        if (list.isEmpty()) {
            currentStudentId = 0
            renderStudentList()
            return
        }
        scrollRoot.scrollTo(0, 0)
        noteRows.removeAllViews()
        emptyText.visibility = View.GONE

        val head = list.firstOrNull()
        val studentName = head?.userName ?: "该学生"

        val back = TextView(ctx)
        back.text = "←  返回学生列表"
        back.textSize = 14f
        back.setTypeface(null, Typeface.BOLD)
        back.setTextColor(resources.getColor(R.color.tsinghua_purple))
        back.setPadding(0, noteDp(6), 0, noteDp(8))
        back.isClickable = true
        back.isFocusable = true
        back.background = ctx.getDrawable(R.drawable.sidebar_item_selector)
        back.setOnClickListener { renderStudentList() }
        noteRows.addView(back)

        val title = TextView(ctx)
        title.text = "🎓 $studentName 的课程"
        title.textSize = 17f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(resources.getColor(R.color.tsinghua_purple_dark))
        noteRows.addView(title)

        // 该学生的课程按「最近更新」降序（保留其在学生笔记中的出现顺序）
        val courseMap = LinkedHashMap<Int, MutableList<Note>>()
        list.forEach { note ->
            courseMap.getOrPut(note.courseId) { mutableListOf() }.add(note)
        }
        val entries = courseMap.entries.toList()
        entries.forEachIndexed { index, (courseId, notes) ->
            val cHead = notes.firstOrNull()
            val courseName = cHead?.courseName ?: "未命名课程"
            noteRows.addView(buildCourseCard(ctx, courseId, courseName, cHead?.coverUrl, notes.size))
            if (index < entries.size - 1) {
                noteRows.addView(makeDivider(ctx))
            }
        }
    }

    // ---------------- 课程卡片（同学生端视觉） ----------------

    private fun buildCourseCard(ctx: Context, courseId: Int, name: String, coverUrl: String?, noteCount: Int): View {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(0, noteDp(10), 0, noteDp(10))
        card.isClickable = true
        card.isFocusable = true
        card.background = ctx.getDrawable(R.drawable.sidebar_item_selector)

        // 左侧：圆角封面缩略图（无图时显示课程名首字）
        val thumbFrame = FrameLayout(ctx)
        thumbFrame.background = ctx.getDrawable(R.drawable.bg_note_cover_placeholder)
        thumbFrame.clipToOutline = true
        val thumbSize = noteDp(52)
        card.addView(thumbFrame, LinearLayout.LayoutParams(thumbSize, thumbSize))

        val initialText = TextView(ctx)
        initialText.text = name.trim().take(1).ifBlank { "课" }
        initialText.textSize = 20f
        initialText.setTypeface(null, Typeface.BOLD)
        initialText.setTextColor(0xFFFFFFFF.toInt())
        initialText.gravity = Gravity.CENTER
        thumbFrame.addView(initialText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        if (!coverUrl.isNullOrBlank()) {
            val coverImage = ImageView(ctx)
            coverImage.scaleType = ImageView.ScaleType.CENTER_CROP
            thumbFrame.addView(coverImage, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val fullUrl = if (coverUrl.startsWith("http")) coverUrl else "${ApiService.BASE_URL}$coverUrl"
            ImageLoaderUtil.load(coverImage, fullUrl, crossfade = true)
        }

        // 右侧：课程名 + 笔记条数
        val info = LinearLayout(ctx)
        info.orientation = LinearLayout.VERTICAL
        info.gravity = Gravity.CENTER_VERTICAL
        info.setPadding(noteDp(12), 0, 0, 0)
        card.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val nameText = TextView(ctx)
        nameText.text = name
        nameText.textSize = 16f
        nameText.setTypeface(null, Typeface.BOLD)
        nameText.setTextColor(resources.getColor(R.color.tsinghua_purple_dark))
        nameText.maxLines = 1
        nameText.ellipsize = TextUtils.TruncateAt.END
        info.addView(nameText)

        val countText = TextView(ctx)
        countText.text = "📝 共 $noteCount 条笔记"
        countText.textSize = 13f
        countText.setTextColor(0xFF999999.toInt())
        countText.setPadding(0, noteDp(3), 0, 0)
        info.addView(countText)

        val chevron = TextView(ctx)
        chevron.text = "›"
        chevron.textSize = 24f
        chevron.setTextColor(0xFFCCCCCC.toInt())
        chevron.setPadding(noteDp(10), 0, 0, 0)
        card.addView(chevron)

        card.setOnClickListener {
            currentCourseId = courseId
            renderNotes()
        }
        return card
    }

    // ---------------- 第三层：某课程的笔记（纯阅读） ----------------

    private fun renderNotes() {
        val ctx = requireContext()
        val list = studentNotes[currentStudentId].orEmpty()
            .filter { it.courseId == currentCourseId }
            .sortedBy { it.timestampSeconds }
        if (list.isEmpty()) {
            currentCourseId = 0
            renderStudentCourses()
            return
        }
        scrollRoot.scrollTo(0, 0)
        noteRows.removeAllViews()
        emptyText.visibility = View.GONE

        val head = list.firstOrNull()
        val courseName = head?.courseName ?: "未命名课程"

        val back = TextView(ctx)
        back.text = "←  返回 ${studentNotes[currentStudentId]?.firstOrNull()?.userName ?: ""} 的课程"
        back.textSize = 14f
        back.setTypeface(null, Typeface.BOLD)
        back.setTextColor(resources.getColor(R.color.tsinghua_purple))
        back.setPadding(0, noteDp(6), 0, noteDp(8))
        back.isClickable = true
        back.isFocusable = true
        back.background = ctx.getDrawable(R.drawable.sidebar_item_selector)
        back.setOnClickListener { renderStudentCourses() }
        noteRows.addView(back)

        val title = TextView(ctx)
        title.text = courseName
        title.textSize = 17f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(resources.getColor(R.color.tsinghua_purple_dark))
        noteRows.addView(title)

        val metaText = TextView(ctx)
        metaText.text = "📝 共 ${list.size} 条笔记 · 按视频时间点排序"
        metaText.textSize = 13f
        metaText.setTextColor(0xFF999999.toInt())
        metaText.setPadding(0, noteDp(2), 0, 0)
        noteRows.addView(metaText)

        list.forEachIndexed { index, note ->
            noteRows.addView(buildReadOnlyNoteRow(ctx, note))
            if (index < list.size - 1) {
                noteRows.addView(makeDivider(ctx))
            }
        }
    }

    /** 一条带时间点的笔记：点一下原地展开全文（纯阅读，无播放按钮）；再点收起 */
    private fun buildReadOnlyNoteRow(ctx: Context, note: Note): View {
        val container = LinearLayout(ctx)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(0, noteDp(8), 0, noteDp(8))
        container.isClickable = true
        container.isFocusable = true
        container.background = ctx.getDrawable(R.drawable.sidebar_item_selector)

        val header = LinearLayout(ctx)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val timeText = TextView(ctx)
        timeText.text = "🕐 ${fmtNoteTime(note.timestampSeconds)}"
        timeText.textSize = 14f
        timeText.setTypeface(null, Typeface.BOLD)
        timeText.setTextColor(resources.getColor(R.color.tsinghua_purple))
        header.addView(timeText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val toggleText = TextView(ctx)
        toggleText.text = "展开 ▾"
        toggleText.textSize = 12f
        toggleText.setTextColor(0xFF999999.toInt())
        header.addView(toggleText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = noteDp(8)
        })

        // 正文：折叠时最多 2 行省略，展开时全文
        val content = TextView(ctx)
        content.text = note.content
        content.textSize = 14f
        content.setTextColor(0xFF444444.toInt())
        content.setLineSpacing(0f, 1.15f)
        content.maxLines = 2
        content.ellipsize = TextUtils.TruncateAt.END
        content.setPadding(0, noteDp(6), 0, 0)

        container.addView(header)
        container.addView(content)

        var expanded = false
        container.setOnClickListener {
            expanded = !expanded
            toggleText.text = if (expanded) "收起 ▴" else "展开 ▾"
            content.maxLines = if (expanded) Int.MAX_VALUE else 2
            content.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
        }
        return container
    }

    private fun makeDivider(ctx: Context): View {
        val divider = View(ctx)
        divider.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, noteDp(1)
        )
        divider.setBackgroundColor(0xFFF0F0F0.toInt())
        return divider
    }

    /**
     * 供宿主系统返回键调用：三级逐级回退。
     * @return true=已消费（回到上一级）；false=已在最顶层，宿主自行处理。
     */
    fun handleBack(): Boolean {
        return when {
            currentCourseId != 0 -> { currentCourseId = 0; renderStudentCourses(); true }
            currentStudentId != 0 -> { currentStudentId = 0; renderStudentList(); true }
            else -> false
        }
    }
}
