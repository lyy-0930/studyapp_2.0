package com.studyapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.studyapp.manager.ApiService
import com.studyapp.model.Article
import com.studyapp.view.ArticleUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 志愿推文 列表/管理（内嵌面板版，左侧栏常驻）
 * - manage=true：教师/管理员侧栏「志愿推文管理」；顶部「＋ 发布」，行内按权限「编辑/删除」。
 * - manage=false：教师首页「查看全部」的阅读列表，仍可对自己发布的文章内联管理。
 * 点行 / 发布 仍整屏打开 ArticleDetailActivity / ArticleEditActivity；返回后靠 onResume 刷新本列表。
 */
class ArticleManageFragment : Fragment() {

    private lateinit var scrollRoot: NestedScrollView
    private lateinit var pageTitle: TextView
    private lateinit var captionText: TextView
    private lateinit var publishButton: TextView
    private lateinit var articleRows: LinearLayout
    private lateinit var emptyText: TextView

    private lateinit var apiService: ApiService

    private var manageMode = false
    private var role = "student"

    val isManageMode: Boolean get() = manageMode

    companion object {
        fun newInstance(manage: Boolean): ArticleManageFragment = ArticleManageFragment().apply {
            arguments = Bundle().apply { putBoolean("manage", manage) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manageMode = arguments?.getBoolean("manage", false) ?: false
        role = ArticleUi.roleOf(requireContext())
        apiService = ApiService.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_article_manage, container, false)
        scrollRoot = root.findViewById(R.id.articleScrollRoot)
        pageTitle = root.findViewById(R.id.articlePageTitle)
        captionText = root.findViewById(R.id.captionText)
        publishButton = root.findViewById(R.id.publishButton)
        articleRows = root.findViewById(R.id.articleRows)
        emptyText = root.findViewById(R.id.emptyText)

        pageTitle.text = if (manageMode) "📰  志愿推文管理" else "📰  志愿推文"

        // 可发布时显示「发布」，否则隐藏
        val canPublish = ArticleUi.canPublish(requireContext())
        if (canPublish) {
            publishButton.visibility = View.VISIBLE
            publishButton.setOnClickListener { openEdit(0) }
        }
        if (manageMode) {
            captionText.visibility = View.VISIBLE
            captionText.text = if (role == "admin")
                "管理全部推文 · 可发布/编辑/删除"
            else
                "我发布的推文 · 可在此发布/管理"
        }

        root.findViewById<View>(R.id.articleRefreshButton).setOnClickListener { load() }
        return root
    }

    override fun onResume() {
        super.onResume()
        // 从整屏 详情/编辑器 返回时刷新列表（无 registerForActivityResult，靠生命周期）
        if (view != null) load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
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
                val myId = ArticleUi.myUserId(requireContext())
                list = list.filter { it.authorId == myId }
            }
            if (list.isEmpty()) {
                emptyText.text = if (manageMode) "还没有发布过推文" else "还没有任何推文"
                emptyText.visibility = View.VISIBLE
                return@launch
            }
            val ctx = requireContext()
            list.forEach { a ->
                articleRows.addView(ArticleUi.buildRow(
                    ctx = ctx,
                    a = a,
                    manageMode = manageMode,
                    canManageArticle = ArticleUi.canManage(ctx, a),
                    onOpen = { openDetail(it) },
                    onEdit = { openEdit(it.id) },
                    onDelete = { confirmDelete(it) }
                ))
            }
        }
    }

    private fun openDetail(article: Article) {
        startActivity(
            Intent(requireContext(), ArticleDetailActivity::class.java)
                .putExtra("article_id", article.id)
        )
    }

    private fun openEdit(articleId: Int) {
        startActivity(
            Intent(requireContext(), ArticleEditActivity::class.java)
                .putExtra("article_id", articleId)
        )
    }

    private fun confirmDelete(article: Article) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除推文")
            .setMessage("确定删除《${article.title}》吗？删除后不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { apiService.deleteArticle(article.id) }
                    if (ok.isSuccess && ok.getOrNull() == true) {
                        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                        load()
                    } else {
                        Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
