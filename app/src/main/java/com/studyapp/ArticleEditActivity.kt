package com.studyapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputFilter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.studyapp.manager.ApiService
import com.studyapp.model.ArticleBlock
import com.studyapp.util.ImageLoaderUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 志愿推文 发布/编辑（块编辑器）
 * 正文由「文字段落 / 插图」两种块按顺序组成：每个块下方有「＋ 文字 / ＋ 插图」，
 * 可在任意段落之间插入多张图，也可删/换已有插图；显示顺序＝最终排版顺序。
 * - articleId=0 表示发布新推文；>0 表示编辑已有推文（加载标题/封面/正文块）
 * 保存成功后 setResult(RESULT_OK) 返回，供列表/首页刷新。
 */
class ArticleEditActivity : AppCompatActivity() {

    private lateinit var scrollRoot: NestedScrollView
    private lateinit var editTitleText: TextView
    private lateinit var titleEdit: EditText
    private lateinit var coverPreview: ImageView
    private lateinit var removeCoverButton: TextView
    private lateinit var blocksContainer: LinearLayout

    private lateinit var apiService: ApiService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // ---------- 正文块模型 ----------
    private class Block(var type: String) { // "text" | "image"
        var text: String = ""
        var remoteUrl: String? = null      // 已有插图相对路径（保留）
        var file: File? = null             // 新插图临时文件（待上传）
        var widthRatio: Float = 1f         // 插图宽度占可用列的比例 0.2~1（Word 式缩放）
        var x: Float = 0.5f                // 插图在行内水平位置：占(列宽-图宽)空闲区比例 0贴左/0.5居中/1贴右
        var aspect: Float = 0f             // 图片真实高/宽比（未知=0，编辑卡高度随它自适应，宽度改变时高度等比跟随）
    }

    private class BlockView(val block: Block, val edit: EditText?)

    private val blocks = mutableListOf<Block>()
    private val attached = mutableListOf<BlockView>()

    // ---------- 封面 ----------
    private var articleId = 0
    private var existingCoverUrl: String? = null
    private var pendingCoverFile: File? = null
    private var pendingRemoveCover = false
    private var saving = false

    // 图片选择后动作：pendingReplaceIndex>=0 → 替换该块；否则 pendingInsertAt（0..blocks.size）→ 在该位置插入新图块，
    // 0 即插到最顶部（-1 = 无动作）
    private var pendingReplaceIndex = -1
    private var pendingInsertAt = -1

    private val coverPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleCoverPicked(uri)
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleImagePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_edit)

        apiService = ApiService.getInstance(this)
        articleId = intent.getIntExtra("article_id", 0)

        initViews()
        if (articleId > 0) loadExisting()
        else {
            updateButtonLabels("发布")
            blocks.add(Block("text"))
            refreshBlocks()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun initViews() {
        scrollRoot = findViewById(R.id.scrollRoot)
        editTitleText = findViewById(R.id.editTitle)
        titleEdit = findViewById(R.id.titleEdit)
        coverPreview = findViewById(R.id.coverPreview)
        removeCoverButton = findViewById(R.id.removeCoverButton)
        blocksContainer = findViewById(R.id.blocksContainer)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.chooseCoverButton).setOnClickListener {
            pendingRemoveCover = false
            removeCoverButton.visibility = View.GONE
            coverPicker.launch("image/*")
        }
        removeCoverButton.setOnClickListener {
            pendingCoverFile = null
            pendingRemoveCover = true
            coverPreview.visibility = View.GONE
            removeCoverButton.visibility = View.GONE
        }
        findViewById<TextView>(R.id.saveButton).setOnClickListener { doSave() }
        findViewById<TextView>(R.id.bottomSaveButton).setOnClickListener { doSave() }
    }

    private fun updateButtonLabels(action: String) {
        editTitleText.text = if (articleId > 0) "编辑推文" else "发布推文"
        findViewById<TextView>(R.id.saveButton).text = action
        findViewById<TextView>(R.id.bottomSaveButton).text = if (action == "发布") "发  布" else "保  存"
    }

    // ==================== 已有推文加载 ====================

    private fun loadExisting() {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { apiService.getArticleDetail(articleId) }
            val a = result.getOrNull()
            if (result.isFailure || a == null || a.id == 0) {
                Toast.makeText(this@ArticleEditActivity, "加载推文失败", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            updateButtonLabels("保存")
            titleEdit.setText(a.title)
            if (!a.coverUrl.isNullOrBlank()) {
                existingCoverUrl = a.coverUrl
                coverPreview.visibility = View.VISIBLE
                removeCoverButton.visibility = View.VISIBLE
                val fullUrl = if (a.coverUrl.startsWith("http")) a.coverUrl else ApiService.BASE_URL + a.coverUrl
                ImageLoaderUtil.load(coverPreview, fullUrl, crossfade = true)
            }
            blocks.clear()
            for (b in a.contentBlocks) {
                when {
                    b.isImage && !b.url.isNullOrBlank() -> blocks.add(Block("image").apply {
                        remoteUrl = b.url
                        widthRatio = b.widthRatio
                        x = b.xRatio
                    })
                    b.isText -> blocks.add(Block("text").apply { text = b.text })
                }
            }
            if (blocks.isEmpty()) blocks.add(Block("text"))
            refreshBlocks()
        }
    }

    // ==================== 封面选择 ====================

    private fun handleCoverPicked(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val compressed = compressBitmap(bitmap, 1600, 1600)
            val file = File(cacheDir, "article_cover_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                compressed.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            pendingCoverFile = file
            pendingRemoveCover = false
            coverPreview.setImageBitmap(compressed)
            coverPreview.visibility = View.VISIBLE
            removeCoverButton.visibility = View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        if (ratio >= 1f) return bitmap
        return Bitmap.createScaledBitmap(bitmap, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }

    // ==================== 插图选择（新增/替换） ====================

    private fun handleImagePicked(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            val compressed = compressBitmap(bitmap, 1600, 1600)
            val file = File(cacheDir, "article_img_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                compressed.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (pendingReplaceIndex in blocks.indices) {
                // 换图：替换该块的本地新图，去掉旧远程图
                val target = blocks[pendingReplaceIndex]
                target.file?.delete()
                target.file = file
                target.remoteUrl = null
                target.aspect = imageAspectOf(file.absolutePath)
                Toast.makeText(this, "已替换插图", Toast.LENGTH_SHORT).show()
            } else if (pendingInsertAt >= 0) {
                // 插入新插图到指定位置（0=最顶部；n=插到第 n 块前；size=末尾）
                blocks.add(pendingInsertAt, Block("image").also {
                    it.file = file
                    it.aspect = imageAspectOf(file.absolutePath)
                })
            }
        } catch (e: Exception) {
            Toast.makeText(this, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        pendingReplaceIndex = -1
        pendingInsertAt = -1
        refreshBlocks()
    }

    private fun decodeSampled(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val target = dp(480)
        var sample = 1
        while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** 图片文件的高/宽比（未知返回 0），用于编辑卡高度自适应 */
    private fun imageAspectOf(path: String): Float {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            BitmapFactory.decodeFile(path, b)
            if (b.outWidth > 0 && b.outHeight > 0) b.outHeight.toFloat() / b.outWidth else 0f
        } catch (e: Exception) {
            0f
        }
    }

    // ==================== 块渲染与编辑 ====================

    /** 把当前已上屏的 EditText 内容写回模型（重建前调用，避免丢失输入） */
    private fun snapshotTexts() {
        for (v in attached) {
            if (v.block.type == "text" && v.edit != null) v.block.text = v.edit.text.toString()
        }
    }

    private fun refreshBlocks(autofocusIndex: Int = -1) {
        snapshotTexts()
        blocksContainer.removeAllViews()
        attached.clear()

        // 顶部：在开头插入
        blocksContainer.addView(buildTopInsertBar())

        if (blocks.isEmpty()) {
            blocksContainer.addView(TextView(this).apply {
                text = "还没有内容，点上方或块下方的「＋」逐段添加"
                textSize = 13f
                setTextColor(0xFFAAAAAA.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(8))
            })
        }
        for (i in blocks.indices) addBlockRow(i)

        if (autofocusIndex in attached.indices) {
            val et = attached[autofocusIndex].edit
            if (et != null) {
                et.requestFocus() // NestedScrollView 会自动把聚焦的输入框滚入可视区
            }
        }
    }

    private fun borderBg(color: Int, cornerDp: Float, strokeColor: Int, strokeDp: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = cornerDp * resources.displayMetrics.density
            setColor(color)
            setStroke((strokeDp * resources.displayMetrics.density).toInt(), strokeColor)
        }

    private fun makeChip(text: String, onTap: () -> Unit): TextView {
        val chip = TextView(this).apply {
            this.text = text
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(R.color.tsinghua_purple_dark))
            background = borderBg(0xFFF4EEFC.toInt(), 15f, 0xFFCBB8EC.toInt(), 1f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onTap() }
        }
        return chip
    }

    /** 顶部「在开头插入文字/插图」快捷条 */
    private fun buildTopInsertBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        bar.addView(TextView(this).apply {
            text = "在开头添加"
            textSize = 12f
            setTextColor(0xFF999999.toInt())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(makeChip("＋ 文字") { insertTextAfter(-1) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) })
        bar.addView(makeChip("＋ 插图") { insertImageAfter(-1) })
        return bar
    }

    /** 渲染第 index 个内容块及其下方「＋」插入条 */
    private fun addBlockRow(index: Int) {
        val block = blocks[index]
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val edit: EditText?
        if (block.type == "text") {
            val (card, et) = buildTextBlockCard(block)
            root.addView(card)
            edit = et
        } else {
            root.addView(buildImageBlockCard(block, index))
            root.addView(TextView(this).apply {
                text = "左右拖动图片可自由移动位置 · 点按可换图"
                textSize = 10f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, dp(3), 0, 0)
            })
            root.addView(buildPositionRow(block)) // 独占一行：居左/居中/居右 快速摆放
            edit = null
        }
        blocksContainer.addView(root)

        // 插入条（插在“本块”之后；列表最后一块的插入条即“末尾追加”）
        val insertBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(6), 0, 0)
        }
        insertBar.addView(makeChip("＋ 文字") { insertTextAfter(index) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) })
        insertBar.addView(makeChip("＋ 插图") { insertImageAfter(index) })
        root.addView(insertBar)

        attached.add(BlockView(block, edit))
    }

    private fun textCardBg(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(0xFFF7F6FB.toInt())
        }
    }

    private fun buildTextBlockCard(block: Block): Pair<View, EditText> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = textCardBg()
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val et = EditText(this).apply {
            setText(block.text)
            textSize = 15f
            setTextColor(0xFF333333.toInt())
            setHintTextColor(0xFFBBBBBB.toInt())
            hint = "输入段落文字…（空段落发布时自动忽略）"
            gravity = Gravity.TOP
            minLines = 3
            setLineSpacing(dp(2).toFloat(), 1.0f)
            background = null
            isFocusableInTouchMode = true
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(5000))
        }
        card.addView(et)
        return card to et
    }

    // ==================== 插图：Word 式 缩放宽度 + 自由移动位置 ====================
    // 图片独占一行，行内可用宽度 availEditorWidthPx()；
    // 图宽 W = 行宽×widthRatio；图左留白 margin = x×(行宽−W)（x=0 贴左 … 0.5 居中 … 1 贴右）。
    // 拖图片本体左右移动 → 自由位置；拖底部横条 → 调宽度（左缘锚定、向右增/减）；点按 → 换图。

    private val MIN_RATIO = 0.2f
    private var availWpx = 0

    /** 编辑卡里插图可占用的最大宽度 px（滚动区左右 18dp + 白卡左右 18dp 各减两次） */
    private fun availEditorWidthPx(): Int {
        if (availWpx == 0) availWpx = (resources.displayMetrics.widthPixels - dp(72)).coerceAtLeast(1)
        return availWpx
    }

    /** 帧高 = 宽 × 真实宽高比（图片高度不固定、随宽等比缩放）；未知比例前用固定高度兜底 */
    private fun imageHeightFor(W: Int, block: Block): Int =
        if (block.aspect > 0f) (W * block.aspect).roundToInt().coerceAtLeast(dp(40))
        else dp(170)

    /** 按 widthRatio/x/aspect 计算图片 frame 的布局参数：宽 W + 自适应高 + 左留白（贴行左缘，靠 margin 定位） */
    private fun imageFrameParams(block: Block): FrameLayout.LayoutParams {
        val A = availEditorWidthPx()
        val W = (A * block.widthRatio).roundToInt().coerceAtLeast(dp(60))
        val slack = (A - W).coerceAtLeast(0)
        return FrameLayout.LayoutParams(W, imageHeightFor(W, block), Gravity.START).apply {
            leftMargin = (block.x * slack).roundToInt()
        }
    }

    /** 底部横条：横向拖拽调宽度（图片左缘不动，宽度向右增/减），commit 时把左留白折算回 x */
    private fun applyWidthDrag(handle: TextView, block: Block, frame: FrameLayout) {
        var downX = 0f
        var downW = 0
        handle.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downW = (frame.layoutParams as FrameLayout.LayoutParams).width
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    resizeKeepLeft(frame, handle, block, downW + (ev.rawX - downX), commit = false)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    resizeKeepLeft(frame, handle, block, downW + (ev.rawX - downX), commit = true)
                    true
                }
                else -> false
            }
        }
    }

    /** 把宽度设为 wPx（钳在 min(MIN_RATIO*行宽,60dp)..行宽）并等比更新高度，保持左缘 leftMargin 不超新空闲区；commit 时写回 widthRatio/x */
    private fun resizeKeepLeft(frame: FrameLayout, handle: TextView, block: Block, wPx: Float, commit: Boolean) {
        val A = availEditorWidthPx()
        val lp = frame.layoutParams as FrameLayout.LayoutParams
        val minW = Math.max(dp(60), (A * MIN_RATIO).roundToInt())
        val W = wPx.roundToInt().coerceIn(minW, A)
        lp.width = W
        lp.height = imageHeightFor(W, block)
        val slack = (A - W).coerceAtLeast(0)
        lp.leftMargin = lp.leftMargin.coerceIn(0, slack)
        frame.layoutParams = lp
        handle.text = "↔  拖拽调整宽度 · ${(W * 100f / A).roundToInt()}%"
        if (commit) {
            block.widthRatio = (W * 100f / A).roundToInt() / 100f
            block.x = if (slack > 0) lp.leftMargin.toFloat() / slack else 0.5f
        }
    }

    /** 图片本体手势：横向拖动自由移动位置；未拖动的点按仍走 onClick（换图） */
    private fun applyMoveDrag(iv: ImageView, block: Block, frame: FrameLayout) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var downMargin = 0
        var dragging = false
        iv.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    downMargin = (frame.layoutParams as FrameLayout.LayoutParams).leftMargin
                    false // 不拦截：点按可触发 onClick，竖直滑动仍交还滚动区
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!dragging) {
                        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                            dragging = true
                            v.isPressed = false
                        } else {
                            return@setOnTouchListener false
                        }
                    }
                    if (dragging) {
                        val A = availEditorWidthPx()
                        val lp = frame.layoutParams as FrameLayout.LayoutParams
                        val slack = (A - lp.width).coerceAtLeast(0)
                        lp.leftMargin = (downMargin + dx).roundToInt().coerceIn(0, slack)
                        frame.layoutParams = lp
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        val A = availEditorWidthPx()
                        val lp = frame.layoutParams as FrameLayout.LayoutParams
                        val slack = (A - lp.width).coerceAtLeast(0)
                        block.x = if (slack > 0) lp.leftMargin.toFloat() / slack else 0.5f
                        v.post { refreshBlocks() } // 拖完重建一次，刷新“位置”快拣 chips 的选中态
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    /** 按当前 widthRatio/x/aspect 就地刷新 frame 的宽/高/左留白（远程图取到真实比例后用） */
    private fun applyImageFrame(frame: FrameLayout, block: Block) {
        frame.layoutParams = imageFrameParams(block)
    }

    /** 远程插图：拉图 → 算真实高/宽比 → 就地校准帧高并显示（只在 aspect 未知时走一次，避免高度固定） */
    private fun fetchRemoteAspectInto(iv: ImageView, block: Block, full: String) {
        coroutineScope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageLoaderUtil.loadBitmap(full) }
            if (bmp == null || bmp.width <= 0 || bmp.height <= 0) return@launch
            if (block.aspect <= 0f) {
                block.aspect = bmp.height.toFloat() / bmp.width
                val frame = iv.parent as? FrameLayout
                if (frame != null) applyImageFrame(frame, block)
            }
            iv.setImageBitmap(bmp)
        }
    }

    private fun makeAlignChip(label: String, active: Boolean, onTap: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (active) Color.WHITE else 0xFF6C4AB6.toInt())
            background = if (active) borderBg(0xFF6C4AB6.toInt(), 11f, 0xFF6C4AB6.toInt(), 0f)
            else borderBg(0xFFF4EEFC.toInt(), 11f, 0xFFCBB8EC.toInt(), 1f)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onTap() }
        }

    /** 位置快拣条（居左/居中/居右 = 把 x 置 0/0.5/1；不匹配时表示自由拖动中的任意位置） */
    private fun buildPositionRow(block: Block): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        row.addView(TextView(this).apply {
            text = "位置"
            textSize = 11f
            setTextColor(0xFF999999.toInt())
            setPadding(0, 0, dp(6), 0)
        })
        for ((label, px) in listOf("居左" to 0f, "居中" to 0.5f, "居右" to 1f)) {
            val chip = makeAlignChip(label, Math.abs(block.x - px) <= 0.05f) {
                block.x = px
                refreshBlocks()
            }
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(6) })
        }
        return row
    }

    /** 图片卡片：行内按 宽度+位置 摆放；拖本体移动、拖底条调宽、点按换图、✕删除 */
    private fun buildImageBlockCard(block: Block, index: Int): View {
        val stage = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val frame = FrameLayout(this).apply {
            layoutParams = imageFrameParams(block)
            background = getDrawable(R.drawable.bg_note_cover_placeholder)
        }
        stage.addView(frame)

        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "插图预览"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                pendingReplaceIndex = index
                pendingInsertAt = -1
                imagePicker.launch("image/*")
            }
        }
        frame.addView(iv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        applyMoveDrag(iv, block, frame) // 拖本体 → 自由移动位置

        val localFile = block.file
        val remote = block.remoteUrl
        if (localFile != null) {
            decodeSampled(localFile.absolutePath)?.let { iv.setImageBitmap(it) }
        } else if (!remote.isNullOrBlank()) {
            val full = if (remote.startsWith("http")) remote else ApiService.BASE_URL + remote
            if (block.aspect > 0f) {
                ImageLoaderUtil.load(iv, full, crossfade = true)
            } else {
                // 还未取到真实宽高比：拉图算比 → 就地校准帧高并显示（避免固定高度）
                fetchRemoteAspectInto(iv, block, full)
            }
        }

        // 删除插图
        frame.addView(TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = borderBg(0xCC000000.toInt(), 12f, 0x00000000, 0f)
            setPadding(dp(2), dp(1), dp(2), dp(1))
            layoutParams = FrameLayout.LayoutParams(
                dp(26), dp(26), Gravity.TOP or Gravity.END
            ).apply { topMargin = dp(6); rightMargin = dp(6) }
            isClickable = true
            setOnClickListener { removeBlockAt(index) }
        })

        // 底部横条：拖拽调宽度（含实时百分比；点图片本体仍是“换图”）
        val handle = TextView(this).apply {
            text = "↔  拖拽调整宽度 · ${(block.widthRatio * 100).roundToInt()}%"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = borderBg(0x66000000.toInt(), 0f, 0x00000000, 0f)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(26), Gravity.BOTTOM
            )
        }
        frame.addView(handle)
        applyWidthDrag(handle, block, frame)
        return stage
    }

    private fun insertTextAfter(index: Int) {
        val at = index + 1
        blocks.add(at, Block("text"))
        refreshBlocks(autofocusIndex = at)
    }

    /** 在“第 index 块之后”插入新插图；index=-1 表示插到最顶部（新图块插入位置 = index+1） */
    private fun insertImageAfter(index: Int) {
        pendingReplaceIndex = -1
        pendingInsertAt = index + 1
        imagePicker.launch("image/*")
    }

    private fun removeBlockAt(index: Int) {
        if (index !in blocks.indices) return
        blocks[index].file?.delete()
        blocks.removeAt(index)
        refreshBlocks()
    }

    // ==================== 保存 ====================

    private fun doSave() {
        if (saving) return
        val title = titleEdit.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "请输入推文标题", Toast.LENGTH_SHORT).show()
            return
        }
        snapshotTexts()

        // 组正文块 JSON：新插图记 f 序号（对应 imageFiles 顺序），旧插图保留远程 url
        // 插图均带上排版 width(0.2~1) 与 x(0贴左..1贴右，占空闲区比例)
        val arr = JSONArray()
        val uploads = mutableListOf<File>()
        val withStyle = { o: JSONObject, b: Block ->
            o.put("width", (b.widthRatio * 100).roundToInt() / 100f)
                .put("x", (b.x * 100).roundToInt() / 100f)
        }
        for (b in blocks) {
            if (b.type == "image") {
                when {
                    b.file != null -> {
                        uploads.add(b.file!!)
                        arr.put(withStyle(JSONObject().put("type", "image").put("f", uploads.size - 1), b))
                    }
                    !b.remoteUrl.isNullOrBlank() ->
                        arr.put(withStyle(JSONObject().put("type", "image").put("url", b.remoteUrl), b))
                }
            } else {
                val t = b.text.trim()
                if (t.isNotEmpty()) arr.put(JSONObject().put("type", "text").put("text", t))
            }
        }
        if (arr.length() == 0) {
            Toast.makeText(this, "还没有正文内容，请至少添加一段文字或插图", Toast.LENGTH_SHORT).show()
            return
        }
        val contentJson = arr.toString()

        saving = true
        coroutineScope.launch {
            val result = if (articleId > 0) {
                withContext(Dispatchers.IO) {
                    apiService.updateArticle(articleId, title, contentJson, pendingCoverFile, pendingRemoveCover, uploads)
                }
            } else {
                withContext(Dispatchers.IO) {
                    apiService.createArticle(title, contentJson, pendingCoverFile, uploads)
                }
            }
            pendingCoverFile?.delete()
            pendingCoverFile = null
            uploads.forEach { it.delete() }
            saving = false
            if (result.isSuccess) {
                Toast.makeText(this@ArticleEditActivity,
                    if (articleId > 0) "保存成功" else "发布成功",
                    Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this@ArticleEditActivity,
                    "保存失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
