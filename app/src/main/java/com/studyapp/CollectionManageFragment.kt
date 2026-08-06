package com.studyapp

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.studyapp.util.ImageLoaderUtil
import com.studyapp.manager.ApiService
import com.studyapp.model.ApiCourse
import com.studyapp.model.Category
import com.studyapp.model.CourseCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 合集管理 Fragment（教师端）
 * 两种状态：
 *   - 列表：我的合集，可新建/编辑/删除
 *   - 详情：某合集下的课程列表，可添加课程/排序/移出，可跳转上传新课程
 */
class CollectionManageFragment : Fragment() {

    private lateinit var apiService: ApiService
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var rootContainer: LinearLayout
    private var currentCollection: CourseCollection? = null
    private var currentCourses: List<ApiCourse> = emptyList()
    private var categories = listOf<Category>()
    private var pendingCoverFile: File? = null   // 新选的封面（保存时上传）
    private var generatedCoverUrl: String? = null // AI生成的封面URL（服务器已存，直接使用）
    private var currentCoverPreview: ImageView? = null

    private val coverImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, uri)
                val file = File(requireContext().cacheDir, "collection_cover_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                pendingCoverFile = file
                generatedCoverUrl = null
                currentCoverPreview?.setImageBitmap(bitmap)
                currentCoverPreview?.visibility = View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "读取图片失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiService = ApiService.getInstance(requireContext())
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val scroll = ScrollView(requireContext()).apply {
            isFillViewport = true
        }
        rootContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        scroll.addView(rootContainer)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scope.launch {
            val result = withContext(Dispatchers.IO) { apiService.getCategories() }
            if (result.isSuccess) {
                categories = result.getOrThrow()
            }
        }
        showCollectionList()
    }

    // ==================== 列表状态 ====================

    private fun showCollectionList() {
        currentCollection = null
        currentCourses = emptyList()
        rootContainer.removeAllViews()
        addHeader("📁 我的合集")

        val createBtn = actionButton("＋ 新建合集")
        createBtn.setOnClickListener { showCreateCollectionDialog() }
        rootContainer.addView(createBtn)

        scope.launch {
            val result = withContext(Dispatchers.IO) { apiService.getMyCollections() }
            if (result.isSuccess) {
                val list = result.getOrThrow()
                if (list.isEmpty()) {
                    rootContainer.addView(emptyView("还没有合集，点击上方「新建合集」创建"))
                } else {
                    for (c in list) {
                        rootContainer.addView(collectionCard(c))
                    }
                }
            } else {
                rootContainer.addView(emptyView("加载合集失败：${result.exceptionOrNull()?.message}"))
            }
        }
    }

    private fun collectionCard(collection: CourseCollection): View {
        val card = CardView(requireContext()).apply {
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 10) }
        }

        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
        }

        // 顶部行：封面缩略图 + 名称/分类/数量
        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // 缩略图
        val coverUrl = collection.coverUrl
        if (!coverUrl.isNullOrEmpty()) {
            val fullUrl = if (coverUrl.startsWith("http")) coverUrl
                else "${ApiService.BASE_URL}$coverUrl"
            topRow.addView(ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(44))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.circle_avatar)
                ImageLoaderUtil.load(this, fullUrl, crossfade = true)
            })
        } else {
            topRow.addView(TextView(requireContext()).apply {
                text = "📁"
                textSize = 30f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(44))
            })
        }
        // 信息列
        val infoCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(12, 0, 0, 0)
            }
        }
        infoCol.addView(TextView(requireContext()).apply {
            text = collection.name
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#5A287D"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        infoCol.addView(TextView(requireContext()).apply {
            text = (if (!collection.categoryName.isNullOrBlank()) "${collection.categoryName} · " else "") + "${collection.courseCount} 门课程"
            textSize = 12f
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 3 }
        })
        topRow.addView(infoCol)
        inner.addView(topRow)

        if (!collection.description.isNullOrBlank()) {
            inner.addView(TextView(requireContext()).apply {
                text = collection.description
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8 }
            })
        }

        // 操作按钮
        val btnRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        btnRow.addView(textButton("编辑") {
            showEditCollectionDialog(collection)
        })
        btnRow.addView(textButton("删除") {
            showDeleteCollectionConfirm(collection)
        })
        inner.addView(btnRow)

        card.addView(inner)
        card.setOnClickListener { showCollectionDetail(collection) }
        return card
    }

    // ==================== 详情状态 ====================

    private fun showCollectionDetail(collection: CourseCollection) {
        currentCollection = collection
        rootContainer.removeAllViews()

        // 返回
        rootContainer.addView(backRow("← 返回合集列表") { showCollectionList() })

        // 标题信息
        addHeader("📁 ${collection.name}")
        rootContainer.addView(TextView(requireContext()).apply {
            text = "共 ${collection.courseCount} 门课程"
            textSize = 13f
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
        })
        if (!collection.description.isNullOrBlank()) {
            rootContainer.addView(TextView(requireContext()).apply {
                text = collection.description
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6 }
            })
        }

        // 添加课程 / 编辑 / 删除
        val actionRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        val addBtn = actionButton("＋ 添加课程")
        addBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addBtn.setOnClickListener { showAddCourseDialog() }
        actionRow.addView(addBtn)
        actionRow.addView(textButton("编辑") { showEditCollectionDialog(collection) })
        actionRow.addView(textButton("删除") { showDeleteCollectionConfirm(collection) })
        rootContainer.addView(actionRow)

        // 课程列表
        rootContainer.addView(TextView(requireContext()).apply {
            text = "—— 收录课程 ——"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14 }
        })

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                apiService.getCollectionCourses(collection.id)
            }
            if (result.isSuccess) {
                currentCourses = result.getOrThrow()
                if (currentCourses.isEmpty()) {
                    rootContainer.addView(emptyView("该合集还没有课程，点击上方「添加课程」"))
                } else {
                    for ((index, course) in currentCourses.withIndex()) {
                        rootContainer.addView(courseRow(collection.id, course, index))
                    }
                }
            } else {
                rootContainer.addView(emptyView("加载课程失败：${result.exceptionOrNull()?.message}"))
            }
        }
    }

    private fun courseRow(collectionId: Int, course: ApiCourse, index: Int): View {
        val card = CardView(requireContext()).apply {
            radius = 12f
            cardElevation = 2f
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 10) }
        }

        val inner = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
        }

        // 顶部行：封面缩略图 + 名称/教师
        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // 缩略图
        val imageUrl = course.imageUrl
        if (!imageUrl.isNullOrEmpty()) {
            val fullUrl = if (imageUrl.startsWith("http")) imageUrl
                else "${ApiService.BASE_URL}$imageUrl"
            topRow.addView(ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(44))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.circle_avatar)
                ImageLoaderUtil.load(this, fullUrl, crossfade = true)
            })
        } else {
            topRow.addView(TextView(requireContext()).apply {
                text = "🎬"
                textSize = 26f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(44))
            })
        }
        // 信息列
        val infoCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(12, 0, 0, 0)
            }
        }
        infoCol.addView(TextView(requireContext()).apply {
            text = "${index + 1}. ${course.name}"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#333333"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        infoCol.addView(TextView(requireContext()).apply {
            text = "教师：${course.teacherName ?: course.teacher}"
            textSize = 12f
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 3 }
        })
        topRow.addView(infoCol)
        inner.addView(topRow)

        if (!course.description.isNullOrBlank()) {
            inner.addView(TextView(requireContext()).apply {
                text = course.description
                textSize = 12f
                setTextColor(Color.GRAY)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6 }
            })
        }

        // 底部行：学分/分类 + 上移/下移/移出
        val bottomRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        bottomRow.addView(TextView(requireContext()).apply {
            val creditText = "学分：${course.credit}"
            val categoryText = if (!course.categoryName.isNullOrBlank()) "  |  分类：${course.categoryName}" else ""
            text = creditText + categoryText
            textSize = 12f
            setTextColor(Color.parseColor("#8A6D3B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        bottomRow.addView(textButton("↑ 上移") {
            if (index > 0) moveCourse(index, index - 1)
            else Toast.makeText(requireContext(), "已经是第一门课", Toast.LENGTH_SHORT).show()
        })
        bottomRow.addView(textButton("↓ 下移") {
            if (index < currentCourses.size - 1) moveCourse(index, index + 1)
            else Toast.makeText(requireContext(), "已经是最后一门课", Toast.LENGTH_SHORT).show()
        })
        bottomRow.addView(textButton("移出") {
            showRemoveCourseConfirm(collectionId, course, index)
        })
        inner.addView(bottomRow)

        card.addView(inner)
        return card
    }

    private fun moveCourse(from: Int, to: Int) {
        val collection = currentCollection ?: return
        val list = currentCourses.toMutableList()
        val temp = list[from]
        list[from] = list[to]
        list[to] = temp
        // 重新编号并逐条更新排序
        scope.launch {
            var ok = true
            for ((idx, c) in list.withIndex()) {
                val r = withContext(Dispatchers.IO) {
                    apiService.reorderCollectionCourse(collection.id, c.id, idx + 1)
                }
                if (!r.isSuccess) ok = false
            }
            if (ok) Toast.makeText(requireContext(), "排序已更新", Toast.LENGTH_SHORT).show()
            else Toast.makeText(requireContext(), "排序更新失败", Toast.LENGTH_SHORT).show()
            showCollectionDetail(collection)
        }
    }

    private fun showRemoveCourseConfirm(collectionId: Int, course: ApiCourse, index: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("移出合集")
            .setMessage("确定将「${course.name}」移出该合集吗？（课程本身不会被删除）")
            .setPositiveButton("移出") { _, _ ->
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        apiService.removeCourseFromCollection(collectionId, course.id)
                    }
                    if (r.isSuccess) {
                        Toast.makeText(requireContext(), "已移出", Toast.LENGTH_SHORT).show()
                        currentCollection?.let { showCollectionDetail(it) }
                    } else {
                        Toast.makeText(requireContext(), "移出失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 添加课程 ====================

    private fun showAddCourseDialog() {
        val collection = currentCollection ?: return
        val options = arrayOf("上传新课程", "从我的已有课程中选择")
        AlertDialog.Builder(requireContext())
            .setTitle("添加课程到「${collection.name}」")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 跳转到课程上传页并预选该合集
                        (activity as? TeacherActivity)?.openUploadWithCollection(collection.id)
                            ?: Toast.makeText(requireContext(), "请到「课程上传」页上传课程", Toast.LENGTH_SHORT).show()
                    }
                    1 -> showPickExistingCourseDialog(collection)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPickExistingCourseDialog(collection: CourseCollection) {
        // 加载我的课程
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                apiService.getTeacherCourses(userId())
            }
            if (!result.isSuccess) {
                Toast.makeText(requireContext(), "加载课程失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val all = result.getOrThrow()
            val alreadyIn = currentCourses.map { it.id }.toSet()
            val candidates = all.filter { it.id !in alreadyIn }
            if (candidates.isEmpty()) {
                Toast.makeText(requireContext(), "没有可添加的课程（都已在合集中）", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val dialogLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 16, 24, 8)
            }
            val checks = mutableListOf<Pair<ApiCourse, CheckBox>>()
            for (c in candidates) {
                val cb = CheckBox(requireContext()).apply {
                    text = c.name
                    textSize = 15f
                }
                dialogLayout.addView(cb)
                checks.add(c to cb)
            }
            AlertDialog.Builder(requireContext())
                .setTitle("选择要加入的课程")
                .setView(dialogLayout)
                .setPositiveButton("添加") { _, _ ->
                    val selected = checks.filter { it.second.isChecked }.map { it.first.id }
                    if (selected.isEmpty()) {
                        Toast.makeText(requireContext(), "请至少选择一门课程", Toast.LENGTH_SHORT).show()
                    } else {
                        addCoursesToCollection(collection, selected)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun addCoursesToCollection(collection: CourseCollection, courseIds: List<Int>) {
        scope.launch {
            var okCount = 0
            for (id in courseIds) {
                val r = withContext(Dispatchers.IO) {
                    apiService.addCourseToCollection(collection.id, id)
                }
                if (r.isSuccess) okCount++
            }
            Toast.makeText(requireContext(), "已添加 $okCount 门课程", Toast.LENGTH_SHORT).show()
            showCollectionDetail(collection)
        }
    }

    // ==================== 新建/编辑/删除合集 ====================

    private fun buildCategorySpinner(selectedId: Int?): Spinner {
        val names = mutableListOf("未选择分类")
        names.addAll(categories.map { it.name })
        val spinner = Spinner(requireContext())
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        if (selectedId != null) {
            val idx = categories.indexOfFirst { it.id == selectedId }
            if (idx >= 0) spinner.setSelection(idx + 1)
        }
        return spinner
    }

    private fun selectedCategoryId(spinner: Spinner): Int? {
        val pos = spinner.selectedItemPosition
        return if (pos <= 0 || pos - 1 >= categories.size) null else categories[pos - 1].id
    }

    private fun buildCoverSection(existingCover: String?, nameInput: EditText): LinearLayout {
        val coverLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        coverLayout.addView(TextView(requireContext()).apply {
            text = "合集封面（可选）"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 4) }
        })
        val preview = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140)).apply { setMargins(0, 0, 0, 6) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            if (!existingCover.isNullOrEmpty()) {
                val fullUrl = if (existingCover.startsWith("http")) existingCover
                    else "${ApiService.BASE_URL}$existingCover"
                visibility = View.VISIBLE
                ImageLoaderUtil.load(this, fullUrl, crossfade = true)
            } else {
                visibility = View.GONE
            }
        }
        coverLayout.addView(preview)
        currentCoverPreview = preview

        // 选择封面 + AI生成封面 两个按钮并排
        val btnRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val chooseBtn = Button(requireContext()).apply {
            text = if (existingCover.isNullOrEmpty()) "📷 选择封面" else "📷 更换封面"
            textSize = 13f
            setTextColor(Color.parseColor("#7D2181"))
            setBackgroundColor(Color.parseColor("#F0E8F4"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 4, 0) }
            setOnClickListener {
                try {
                    coverImageLauncher.launch("image/*")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "无法打开相册", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnRow.addView(chooseBtn)

        val aiBtn = Button(requireContext()).apply {
            text = "✨ AI 生成"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7D2181"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 0, 0) }
            setOnClickListener {
                generateAiCover(nameInput, preview, this)
            }
        }
        btnRow.addView(aiBtn)
        coverLayout.addView(btnRow)
        return coverLayout
    }

    private fun generateAiCover(nameInput: EditText, preview: ImageView, btn: Button) {
        val title = nameInput.text?.toString()?.trim() ?: ""
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "请先填写合集名称", Toast.LENGTH_SHORT).show()
            return
        }
        btn.isEnabled = false
        btn.text = "AI生成中..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                apiService.generateCourseImage(title, null)
            }
            if (result.isSuccess) {
                // Pollinations 在线生成成功 → 直接用服务器上的图片URL
                generatedCoverUrl = result.getOrThrow()
                pendingCoverFile = null
                val url = generatedCoverUrl ?: ""
                val fullUrl = if (url.startsWith("http")) url else "${ApiService.BASE_URL}$url"
                preview.setImageResource(R.drawable.circle_avatar)
                preview.visibility = View.VISIBLE
                ImageLoaderUtil.load(preview, fullUrl, crossfade = true)
                Toast.makeText(requireContext(), "AI封面生成成功", Toast.LENGTH_SHORT).show()
            } else {
                // 在线AI服务不可用时，回退到本地 Canvas 绘制封面（与课程封面一致）
                val bitmap = withContext(Dispatchers.IO) {
                    try { CourseImageGenerator.generate(title, "") } catch (e: Exception) { null }
                }
                if (bitmap != null) {
                    val file = File(requireContext().cacheDir, "collection_cover_local_${System.currentTimeMillis()}.jpg")
                    try {
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        pendingCoverFile = file
                        generatedCoverUrl = null
                        preview.setImageBitmap(bitmap)
                        preview.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), "在线AI服务暂不可用，已生成本地封面", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "封面生成失败", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "AI封面生成失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
            btn.isEnabled = true
            btn.text = "✨ AI 生成"
        }
    }

    private fun showCreateCollectionDialog() {
        pendingCoverFile = null
        generatedCoverUrl = null
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }
        val nameInput = EditText(requireContext()).apply {
            hint = "合集名称（必填）"
            textSize = 15f
        }
        val descInput = EditText(requireContext()).apply {
            hint = "合集简介（可选）"
            textSize = 14f
            minLines = 2
            maxLines = 4
        }
        layout.addView(TextView(requireContext()).apply {
            text = "分类标签（可选）"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
        })
        val categorySpinner = buildCategorySpinner(null)
        layout.addView(categorySpinner)
        layout.addView(buildCoverSection(null, nameInput))
        layout.addView(nameInput)
        layout.addView(descInput)

        AlertDialog.Builder(requireContext())
            .setTitle("新建合集")
            .setView(layout)
            .setPositiveButton("创建") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "请填写合集名称", Toast.LENGTH_SHORT).show()
                } else {
                    saveCollection(null, name, descInput.text.toString().trim(), selectedCategoryId(categorySpinner), null)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditCollectionDialog(collection: CourseCollection) {
        pendingCoverFile = null
        generatedCoverUrl = null
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }
        val nameInput = EditText(requireContext()).apply {
            hint = "合集名称"
            setText(collection.name)
            textSize = 15f
        }
        val descInput = EditText(requireContext()).apply {
            hint = "合集简介"
            setText(collection.description ?: "")
            textSize = 14f
            minLines = 2
            maxLines = 4
        }
        layout.addView(TextView(requireContext()).apply {
            text = "分类标签（可选）"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
        })
        val categorySpinner = buildCategorySpinner(collection.categoryId)
        layout.addView(categorySpinner)
        layout.addView(buildCoverSection(collection.coverUrl, nameInput))
        layout.addView(nameInput)
        layout.addView(descInput)

        AlertDialog.Builder(requireContext())
            .setTitle("编辑合集")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "合集名称不能为空", Toast.LENGTH_SHORT).show()
                } else {
                    saveCollection(collection, name, descInput.text.toString().trim(), selectedCategoryId(categorySpinner), collection.coverUrl)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 统一的保存逻辑：如有新选封面先上传，再创建或更新
     */
    private fun saveCollection(collection: CourseCollection?, name: String, description: String, categoryId: Int?, existingCover: String?) {
        scope.launch {
            var coverUrl = existingCover
            val newFile = pendingCoverFile
            if (newFile != null) {
                val up = withContext(Dispatchers.IO) { apiService.uploadCoverImage(newFile, name) }
                if (up.isSuccess) {
                    coverUrl = up.getOrThrow()
                } else {
                    Toast.makeText(requireContext(), "封面上传失败：${up.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                newFile.delete()
            } else if (generatedCoverUrl != null) {
                // AI生成的封面：服务器已保存，直接使用其URL
                coverUrl = generatedCoverUrl
            }
            pendingCoverFile = null
            generatedCoverUrl = null

            if (collection == null) {
                val result = withContext(Dispatchers.IO) {
                    apiService.createCollection(name, description.ifBlank { null }, coverUrl, categoryId)
                }
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "合集创建成功", Toast.LENGTH_SHORT).show()
                    showCollectionList()
                } else {
                    Toast.makeText(requireContext(), "创建失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                val result = withContext(Dispatchers.IO) {
                    apiService.updateCollection(collection.id, name, description.ifBlank { null }, coverUrl, categoryId)
                }
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "合集已更新", Toast.LENGTH_SHORT).show()
                    showCollectionList()
                } else {
                    Toast.makeText(requireContext(), "更新失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteCollectionConfirm(collection: CourseCollection) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除合集")
            .setMessage("确定要删除合集「${collection.name}」吗？合集内的课程不会被删除。")
            .setPositiveButton("删除") { _, _ ->
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        apiService.deleteCollection(collection.id)
                    }
                    if (r.isSuccess) {
                        Toast.makeText(requireContext(), "合集已删除", Toast.LENGTH_SHORT).show()
                        showCollectionList()
                    } else {
                        Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 工具视图 ====================

    private fun userId(): Int {
        return requireActivity().getSharedPreferences("login_prefs", android.content.Context.MODE_PRIVATE)
            .getInt("user_id", 0)
    }

    private fun addHeader(text: String) {
        rootContainer.addView(TextView(requireContext()).apply {
            this.text = text
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#5A287D"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 10) }
        })
    }

    private fun actionButton(text: String): Button {
        return Button(requireContext()).apply {
            this.text = text
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#7D2181"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun textButton(text: String, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#7D2181"))
            setPadding(12, 6, 12, 6)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
    }

    private fun backRow(text: String, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#7D2181"))
            setPadding(0, 0, 0, 12)
            setOnClickListener { onClick() }
        }
    }

    private fun emptyView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            )
        }
    }
}
