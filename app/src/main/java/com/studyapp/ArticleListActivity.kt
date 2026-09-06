package com.studyapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.studyapp.manager.ApiService
import com.studyapp.model.Article
import com.studyapp.view.ArticleUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 志愿推文列表
 * - 学生/教师首页「查看全部」入口：manage=false，纯阅读列表，点行进详情。
 * - 教师/管理员侧栏「志愿推文管理」入口：manage=true，顶部「发布」+ 行内「编辑/删除」。
 *   （教师只见自己发布的；管理员见全部）
 */
class ArticleListActivity : AppCompatActivity() {

    private lateinit var scrollRoot: NestedScrollView
    private lateinit var pageTitle: TextView
    private lateinit var captionText: TextView
    private lateinit var publishButton: TextView
    private lateinit var articleRows: LinearLayout
    private lateinit var emptyText: TextView

    private lateinit var apiService: ApiService
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var manageMode = false
    private var role = "student"

    private val actionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_list)

        apiService = ApiService.getInstance(this)
        manageMode = intent.getBooleanExtra("manage", false)
        role = ArticleUi.roleOf(this)

        initViews()
        load()
    }

    private fun initViews() {
        scrollRoot = findViewById(R.id.scrollRoot)
        pageTitle = findViewById(R.id.pageTitle)
        captionText = findViewById(R.id.captionText)
        publishButton = findViewById(R.id.publishButton)
        articleRows = findViewById(R.id.articleRows)
        emptyText = findViewById(R.id.emptyText)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.refreshButton).setOnClickListener { load() }

        pageTitle.text = if (manageMode) "志愿推文管理" else "志愿推文"

        // 管理模式：顶部提供「发布」；非管理模式教师/管理员也允许发布
        val canPublish = ArticleUi.canPublish(this)
        if (canPublish) {
            publishButton.visibility = View.VISIBLE
            publishButton.setOnClickListener { openEdit(0) }
        }
        if (manageMode) {
            captionText.visibility = View.VISIBLE
            captionText.text = if (role == "admin")
                "共 管理全部推文 · 可发布/编辑/删除"
            else
                "共 我发布的推文 · 可在此发布/管理"
        }
    }

    private fun load() {
        coroutineScope.launch {
            scrollRoot.scrollTo(0, 0)
            articleRows.removeAllViews()
            emptyText.visibility = View.GONE
            val result = withContext(Dispatchers.IO) { apiService.getArticles(100) }
            if (result.isFailure) {
                emptyText.text = "加载推文失败，请稍后重试"
                emptyText.visibility = View.VISIBLE
                return@launch
            }
            var list = result.getOrNull().orEmpty()
            if (manageMode && role == "teacher") {
                val myId = ArticleUi.myUserId(this@ArticleListActivity)
                list = list.filter { it.authorId == myId }
            }
            if (list.isEmpty()) {
                emptyText.text = if (manageMode) "还没有发布过推文" else "还没有任何推文"
                emptyText.visibility = View.VISIBLE
                return@launch
            }
            list.forEach { a ->
                articleRows.addView(ArticleUi.buildRow(
                    ctx = this@ArticleListActivity,
                    a = a,
                    manageMode = manageMode,
                    canManageArticle = ArticleUi.canManage(this@ArticleListActivity, a),
                    onOpen = { openDetail(it) },
                    onEdit = { openEdit(it.id) },
                    onDelete = { confirmDelete(it) }
                ))
            }
        }
    }

    private fun openDetail(article: Article) {
        val intent = Intent(this, ArticleDetailActivity::class.java)
            .putExtra("article_id", article.id)
        actionLauncher.launch(intent)
    }

    private fun openEdit(articleId: Int) {
        val intent = Intent(this, ArticleEditActivity::class.java)
            .putExtra("article_id", articleId)
        actionLauncher.launch(intent)
    }

    private fun confirmDelete(article: Article) {
        AlertDialog.Builder(this)
            .setTitle("删除推文")
            .setMessage("确定删除《${article.title}》吗？删除后不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                coroutineScope.launch {
                    val ok = withContext(Dispatchers.IO) { apiService.deleteArticle(article.id) }
                    if (ok.isSuccess && ok.getOrNull() == true) {
                        Toast.makeText(this@ArticleListActivity, "已删除", Toast.LENGTH_SHORT).show()
                        load()
                    } else {
                        Toast.makeText(this@ArticleListActivity, "删除失败", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
