package com.studyapp.view

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.studyapp.ArticleDetailActivity
import com.studyapp.ArticleEditActivity
import com.studyapp.ArticleListActivity
import com.studyapp.R
import com.studyapp.manager.ApiService
import com.studyapp.model.Article
import com.studyapp.util.ImageLoaderUtil

/**
 * 志愿推文 的共享 UI 构件（行卡片 / 时间 / 封面 URL 等）
 * 供 学生/教师首页区块、全部列表、管理模式 复用，保证视觉一致
 */
object ArticleUi {

    fun dp(context: Context, v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    fun roleOf(context: Context): String =
        context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE).getString("role", "student") ?: "student"

    fun myUserId(context: Context): Int =
        context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE).getInt("user_id", 0)

    /** 是否可管理某推文：管理员可管理全部；教师只能管理自己发布的 */
    fun canManage(context: Context, article: Article): Boolean {
        val role = roleOf(context)
        return role == "admin" || (role == "teacher" && article.authorId == myUserId(context))
    }

    fun canPublish(context: Context): Boolean {
        val role = roleOf(context)
        return role == "teacher" || role == "admin"
    }

    fun coverFullUrl(u: String?): String? {
        if (u.isNullOrBlank()) return null
        return if (u.startsWith("http")) u else ApiService.BASE_URL + u
    }

    fun metaText(a: Article): String {
        val t = Article.displayTime(a.createdAt)
        return if (t.isBlank()) a.authorName else "${a.authorName} · $t"
    }

    fun excerptLine(a: Article): String = a.excerpt.replace(Regex("\\s+"), " ").trim()

    /** 推文行卡片（首页小卡 / 全部列表 / 管理模式共用） */
    fun buildRow(
        ctx: Context,
        a: Article,
        manageMode: Boolean,
        canManageArticle: Boolean,
        onOpen: (Article) -> Unit,
        onEdit: ((Article) -> Unit)? = null,
        onDelete: ((Article) -> Unit)? = null
    ): View {
        val card = CardView(ctx).apply {
            radius = dp(ctx, 14).toFloat()
            elevation = dp(ctx, 2).toFloat()
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(ctx, 10) }
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
            isClickable = true
            isFocusable = true
            background = ctx.getDrawable(com.studyapp.R.drawable.sidebar_item_selector)
        }
        card.addView(row)

        // ---- 文本区 ----
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(ctx, 10), 0)
        }
        row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        textCol.addView(TextView(ctx).apply {
            text = a.title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF2B2B2B.toInt())
            maxLines = if (manageMode) 1 else 2
            ellipsize = TextUtils.TruncateAt.END
        })

        val excerpt = excerptLine(a)
        if (excerpt.isNotBlank()) {
            textCol.addView(TextView(ctx).apply {
                text = excerpt
                textSize = 13f
                setTextColor(0xFF999999.toInt())
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(ctx, 4), 0, 0)
            })
        }

        textCol.addView(TextView(ctx).apply {
            text = metaText(a)
            textSize = 12f
            setTextColor(0xFFB0B0B0.toInt())
            setPadding(0, dp(ctx, 6), 0, 0)
        })

        // ---- 右侧封面缩略图（有封面才显示） ----
        val coverUrl = coverFullUrl(a.coverUrl)
        if (coverUrl != null) {
            val frame = FrameLayout(ctx)
            frame.layoutParams = LinearLayout.LayoutParams(dp(ctx, 92), dp(ctx, 70))
            frame.clipToOutline = true
            frame.outlineProvider = ViewOutlineProvider.BACKGROUND
            frame.background = roundedDrawable(ctx, 10f, 0xFFF0EAF9.toInt())
            row.addView(frame)
            val img = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            frame.addView(img, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            ImageLoaderUtil.load(img, coverUrl, crossfade = true)
        }

        // ---- 管理模式：编辑 / 删除（仅可管理的行显示） ----
        if (manageMode && canManageArticle) {
            val actionCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 8), 0, 0, 0)
            }
            row.addView(actionCol, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

            if (onEdit != null) {
                actionCol.addView(TextView(ctx).apply {
                    text = "编辑"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ctx.getColor(com.studyapp.R.color.tsinghua_purple_dark))
                    setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
                    isClickable = true
                    setOnClickListener { onEdit(a) }
                })
            }
            if (onDelete != null) {
                actionCol.addView(TextView(ctx).apply {
                    text = "删除"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(0xFFE53935.toInt())
                    setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
                    isClickable = true
                    setOnClickListener { onDelete(a) }
                })
            }
        }

        row.setOnClickListener { onOpen(a) }
        return card
    }

    /** 圆角纯色 Drawable（缩略图 / 占位用） */
    fun roundedDrawable(ctx: Context, cornerDp: Float, color: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = (cornerDp * ctx.resources.displayMetrics.density)
            setColor(color)
        }
    }

    // ==================== 首页区块构件 ====================

    /**
     * 首页「志愿推文」区块头部行：📰 标题 + （可选）发布 + 查看全部
     */
    fun buildHomeHeader(ctx: Context, canPublish: Boolean, onPublish: (() -> Unit)?, onViewAll: () -> Unit): View {
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(ctx, 4))
        }

        header.addView(TextView(ctx).apply {
            text = "📰  志愿推文"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ctx.getColor(R.color.tsinghua_purple_dark))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (canPublish && onPublish != null) {
            header.addView(TextView(ctx).apply {
                text = "＋ 发布"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = roundedDrawable(ctx, 16f, ctx.getColor(R.color.tsinghua_purple))
                setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
                isClickable = true
                setOnClickListener { onPublish() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(ctx, 6) })
        }

        header.addView(TextView(ctx).apply {
            text = "查看全部 ›"
            textSize = 13f
            setTextColor(ctx.getColor(R.color.tsinghua_purple_dark))
            setPadding(dp(ctx, 8), dp(ctx, 6), 0, dp(ctx, 6))
            isClickable = true
            setOnClickListener { onViewAll() }
        })
        return header
    }

    // ==================== 页面跳转 ====================

    fun openDetail(ctx: Context, article: Article) {
        ctx.startActivity(Intent(ctx, ArticleDetailActivity::class.java).putExtra("article_id", article.id))
    }

    fun openEdit(ctx: Context, articleId: Int) {
        ctx.startActivity(Intent(ctx, ArticleEditActivity::class.java).putExtra("article_id", articleId))
    }

    fun openList(ctx: Context, manage: Boolean = false) {
        ctx.startActivity(Intent(ctx, ArticleListActivity::class.java).putExtra("manage", manage))
    }
}
