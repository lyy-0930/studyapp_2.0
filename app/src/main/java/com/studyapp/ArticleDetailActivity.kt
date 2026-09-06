package com.studyapp

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import com.studyapp.model.Article
import com.studyapp.util.ImageLoaderUtil
import com.studyapp.view.ArticleUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 志愿推文详情（公众号图文阅读风）
 * 顶部封面（可选）+ 标题 + 作者/时间 + 分隔线后按块渲染正文：
 * 文字块为段落，插图块按真实宽高比自适应高度铺满内容列。
 * 作者本人/管理员可在页内编辑/删除。
 */
class ArticleDetailActivity : AppCompatActivity() {

    private lateinit var bodyContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var topEditButton: TextView

    private lateinit var apiService: ApiService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var articleId = 0
    private var article: Article? = null
    private var changed = false

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            changed = true
            setResult(RESULT_OK) // 返回列表时提示刷新
            load()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_detail)

        apiService = ApiService.getInstance(this)
        articleId = intent.getIntExtra("article_id", 0)
        initViews()
        load()
    }

    private fun initViews() {
        bodyContainer = findViewById(R.id.bodyContainer)
        emptyText = findViewById(R.id.emptyText)
        topEditButton = findViewById(R.id.topEditButton)
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.refreshButton).setOnClickListener { load() }

        topEditButton.setOnClickListener { openEdit() }
    }

    private fun load() {
        coroutineScope.launch {
            bodyContainer.removeAllViews()
            emptyText.visibility = View.GONE
            topEditButton.visibility = View.GONE
            val result = withContext(Dispatchers.IO) { apiService.getArticleDetail(articleId) }
            if (result.isFailure || result.getOrNull()?.id == 0) {
                emptyText.text = "加载推文失败，请稍后重试"
                emptyText.visibility = View.VISIBLE
                return@launch
            }
            article = result.getOrNull()
            val a = article!!
            topEditButton.visibility = if (ArticleUi.canManage(this@ArticleDetailActivity, a)) View.VISIBLE else View.GONE
            render(a)
        }
    }

    private fun render(a: Article) {
        val d = ArticleUi.dp(this, 1)
        val bodyW = bodyContainer.width // px，布局完成后的正文卡片实际宽

        // 封面：满宽裁切显示（可选）
        val coverUrl = ArticleUi.coverFullUrl(a.coverUrl)
        if (coverUrl != null) {
            val cover = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ArticleUi.dp(this@ArticleDetailActivity, 200))
            }
            bodyContainer.addView(cover)
            ImageLoaderUtil.load(cover, coverUrl, crossfade = true)
        }

        // 文本内容区（左右留白 20dp）
        val contentArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20 * d, 18 * d, 20 * d, 18 * d)
        }
        bodyContainer.addView(contentArea)

        // 标题
        contentArea.addView(TextView(this).apply {
            text = a.title
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF1F1F1F.toInt())
            setLineSpacing(4f, 1.15f)
        })

        // 作者/时间行
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 14 * d, 0, 0)
        }
        contentArea.addView(metaRow)

        val avatarText = TextView(this).apply {
            text = a.authorName.trim().take(1).ifBlank { "志" }
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = ArticleUi.roundedDrawable(this@ArticleDetailActivity, 20f, 0xFF6C4AB6.toInt())
            layoutParams = LinearLayout.LayoutParams(36 * d, 36 * d)
        }
        metaRow.addView(avatarText)

        val authorCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10 * d, 0, 0, 0)
        }
        metaRow.addView(authorCol)
        authorCol.addView(TextView(this).apply {
            text = a.authorName
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF333333.toInt())
        })
        authorCol.addView(TextView(this).apply {
            text = Article.displayTime(a.createdAt)
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 2 * d, 0, 0)
        })

        // 分隔线
        contentArea.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                d
            ).apply {
                topMargin = 16 * d
                bottomMargin = 10 * d
            }
            setBackgroundColor(0xFFEEEEEE.toInt())
        })

        // 正文：逐块渲染（文字→段落；插图→自适应真实宽高比）
        val contentW = contentWidthPx(bodyW, d)
        if (a.contentBlocks.isEmpty()) {
            contentArea.addView(TextView(this).apply {
                text = "（正文暂无内容）"
                textSize = 15f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, 8 * d, 0, 0)
            })
        }
        a.contentBlocks.forEach { block ->
            if (block.isText && block.text.isNotBlank()) {
                val t = block.text.replace("\r\n", "\n").replace("\r", "\n")
                contentArea.addView(TextView(this).apply {
                    text = t
                    textSize = 16f
                    setTextColor(0xFF3A3A3A.toInt())
                    setLineSpacing(6f, 1.0f)
                    setPadding(0, 8 * d, 0, 0)
                    setTextIsSelectable(true)
                })
            } else if (block.isImage && !block.url.isNullOrBlank()) {
                val full = if (block.url.startsWith("http")) block.url else ApiService.BASE_URL + block.url
                // 宽度：占列比例（旧数据缺省满宽）；水平位置 x：占(行宽-图宽)空闲区比例 0贴左/0.5居中/1贴右（旧数据居中）
                val imgW = (contentW * block.widthRatio).roundToInt()
                    .coerceAtLeast(ArticleUi.dp(this@ArticleDetailActivity, 40))
                val slack = (contentW - imgW).coerceAtLeast(0)
                val margin = (block.xRatio * slack).roundToInt()
                // 用整行宽的 slot 包一层，靠左留白摆放图片（Word 式任意水平位置）
                val slot = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 12 * d }
                }
                contentArea.addView(slot)
                val img = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        imgW,
                        (imgW * 0.75f).toInt().coerceAtLeast(ArticleUi.dp(this@ArticleDetailActivity, 80)),
                        Gravity.START
                    ).apply { leftMargin = margin }
                    background = ArticleUi.roundedDrawable(
                        this@ArticleDetailActivity, 10f, 0xFFF0EAF9.toInt())
                    clipToOutline = true
                    outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                    contentDescription = "正文插图"
                }
                slot.addView(img)
                // 按真实宽高比自适应高度（图片加载完按比例设置尺寸，margin/位置不变）
                ImageLoaderUtil.loadAdaptive(img, full, imgW, crossfade = true)
            }
        }

        // 底部操作（作者/管理员可编辑/删除）
        if (ArticleUi.canManage(this, a)) {
            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 18 * d, 0, 0)
            }
            contentArea.addView(actionRow)

            actionRow.addView(TextView(this).apply {
                text = "✏️  编辑"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(resources.getColor(R.color.tsinghua_purple_dark))
                setPadding(14 * d, 8 * d, 14 * d, 8 * d)
                isClickable = true
                setOnClickListener { openEdit() }
            })
            actionRow.addView(TextView(this).apply {
                text = "🗑  删除"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(0xFFE53935.toInt())
                setPadding(14 * d, 8 * d, 14 * d, 8 * d)
                isClickable = true
                setOnClickListener { confirmDelete() }
            })
        }
    }

    /** 正文插图的可用宽度 px：正文卡片实际宽 - 内容区左右各 20dp；布局前兜底用屏幕宽估算 */
    private fun contentWidthPx(bodyWidthPx: Int, d: Int): Int {
        val contentPadding = 40 * d
        if (bodyWidthPx > 0 && bodyWidthPx > contentPadding) return bodyWidthPx - contentPadding
        // 兜底：屏幕宽 - 滚动区两侧 18dp - 内容区两侧 20dp
        return (resources.displayMetrics.widthPixels - 2 * dp(18) - contentPadding).coerceAtLeast(1)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun openEdit() {
        val intent = Intent(this, ArticleEditActivity::class.java)
            .putExtra("article_id", articleId)
        editLauncher.launch(intent)
    }

    private fun confirmDelete() {
        val a = article ?: return
        AlertDialog.Builder(this)
            .setTitle("删除推文")
            .setMessage("确定删除《${a.title}》吗？删除后不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                coroutineScope.launch {
                    val ok = withContext(Dispatchers.IO) { apiService.deleteArticle(a.id) }
                    if (ok.isSuccess && ok.getOrNull() == true) {
                        Toast.makeText(this@ArticleDetailActivity, "已删除", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(this@ArticleDetailActivity, "删除失败", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onBackPressed() {
        if (changed) setResult(RESULT_OK)
        finish()
    }
}
