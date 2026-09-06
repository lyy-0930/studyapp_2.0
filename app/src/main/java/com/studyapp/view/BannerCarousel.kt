package com.studyapp.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.manager.ApiService
import com.studyapp.model.Banner
import com.studyapp.util.ImageLoaderUtil
import kotlin.math.abs
import kotlin.math.min

/**
 * 首页轮播组件（FrameLayout 子类，零第三方依赖）
 *
 * 采用市面常见的「中间卡片突出 + 两侧露出相邻卡片边缘」形态：
 *   - ≥2 张：每张卡片宽约容器 72%，居中一张为基准（宽高比 CARD_ASPECT），左右各露出
 *     相邻卡片 ~14% 的边，滚动/自动播放时侧边卡片略微缩小（居中突出）；
 *     数据复制多份（banners.size × LOOP_COPIES）模拟双向无限循环，自动轮播按“一页宽”
 *     smoothScrollBy 推进，滑到末尾份再瞬移回中间份。底部圆点指示器。
 *   - 1 张：退化为全宽静态卡片，不轮播、不显示圆点。
 *   - 0 张：整体 GONE，不占位。
 *
 * 高度不写死：由宽度按卡片比例自动算出（onMeasure），保证任何屏宽下图片不裁错，
 * 建议管理员传 3:1 横向图。对外唯一接口 setBanners(List<Banner>)。
 */
class BannerCarousel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val AUTO_PLAY_INTERVAL = 3000L
        private const val RESUME_DELAY = 3000L
        // 数据复制份数：总页数 = banners.size * COPIES，从中间份起步。
        // 用中等数量模拟“双向无限”，避免 Int.MAX_VALUE 超大起始位导致的布局卡顿。
        private const val LOOP_COPIES = 500
        // ≥2 张时，卡片宽占容器宽的比例（两侧各留 (1-ratio)/2 露边）
        private const val ITEM_WIDTH_FRACTION = 0.72f
        // 卡片宽高比（宽 : 高），管理员按 3:1 传图最贴合
        private const val CARD_ASPECT = 3f
        // 滚动到两侧时卡片缩小到的最小缩放
        private const val SIDE_SCALE = 0.88f
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density + 0.5f).toInt()

    private lateinit var recyclerView: RecyclerView
    private lateinit var dotContainer: LinearLayout
    private val snapHelper = PagerSnapHelper()
    private val layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
    private val autoHandler = Handler(Looper.getMainLooper())

    private var banners: List<Banner> = emptyList()
    private var startIndex = 0
    private var currentPage = 0
    private var isTouching = false

    // 布局时算出的实际尺寸（px），供 onMeasure/自动轮播/缩放动效用
    private var contentWidthPx = 0
    private var cardWidthPx = 0
    private var cardHeightPx = 0

    private val autoPlayTask = object : Runnable {
        override fun run() {
            if (banners.size < 2) return
            val first = layoutManager.findFirstVisibleItemPosition()
            val total = recyclerView.adapter?.itemCount ?: 0
            // 到达末尾份 → 瞬移回中间份；否则按“一页宽”真实滚动，保证推进
            if (first >= total - banners.size && startIndex > 0) {
                layoutManager.scrollToPosition(startIndex)
            } else {
                val stride = cardWidthPx
                if (stride > 0) {
                    recyclerView.smoothScrollBy(stride, 0)
                }
            }
            scheduleAutoPlay()
        }
    }

    init {
        isClickable = true

        // 1) RecyclerView 承载卡片页（两侧露边由左右 padding + clipToPadding=false 实现）
        recyclerView = RecyclerView(context).apply {
            this.layoutManager = this@BannerCarousel.layoutManager
            snapHelper.attachToRecyclerView(this)
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            isNestedScrollingEnabled = false
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    updatePageTransforms()
                }

                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        updatePageTransforms()
                        if (banners.size >= 2) {
                            val first = this@BannerCarousel.layoutManager.findFirstVisibleItemPosition()
                            if (first != RecyclerView.NO_POSITION) {
                                val rel = first - startIndex
                                val idx = ((rel % banners.size) + banners.size) % banners.size
                                updateDots(idx)
                            }
                        }
                    }
                }
            })
            // 触摸时暂停自动轮播；松开后延迟恢复
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isTouching = true
                        stopAutoPlay()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isTouching = false
                        autoHandler.postDelayed({ scheduleAutoPlay() }, RESUME_DELAY)
                    }
                }
                false
            }
        }
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 2) 底部圆点指示器（叠加层，仅 ≥2 张显示）
        dotContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addView(dotContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(6f)
        })
        dotContainer.visibility = View.GONE
    }

    // 高度不写死：按宽度 × 卡片比例自动测量；≥2 张时通过左右 padding 制造两侧露边
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val specWidth = MeasureSpec.getSize(widthMeasureSpec)
        contentWidthPx = if (specWidth > 0) resolveSize(specWidth, widthMeasureSpec) else 0

        if (contentWidthPx > 0) {
            val fraction = if (banners.size < 2) 1f else ITEM_WIDTH_FRACTION
            cardWidthPx = (contentWidthPx * fraction).toInt().coerceAtLeast(1)
            cardHeightPx = (cardWidthPx / CARD_ASPECT).toInt().coerceAtLeast(1)
            val peek = (contentWidthPx - cardWidthPx) / 2
            recyclerView.setPadding(
                if (banners.size < 2) 0 else peek,
                0,
                if (banners.size < 2) 0 else peek,
                0
            )
        } else {
            cardWidthPx = 1
            cardHeightPx = 1
        }

        val childHeightSpec = MeasureSpec.makeMeasureSpec(cardHeightPx, MeasureSpec.EXACTLY)
        val childWidthSpec = MeasureSpec.makeMeasureSpec(contentWidthPx, MeasureSpec.EXACTLY)
        // 先让 FrameLayout 用算好的尺寸测量各子 View
        measureChildren(childWidthSpec, childHeightSpec)
        setMeasuredDimension(contentWidthPx, cardHeightPx)
    }

    /**
     * 设置轮播数据。0 张隐藏；1 张静态全宽；≥2 张开卡片轮播。
     */
    fun setBanners(list: List<Banner>) {
        banners = list.filter { it.imageUrl.isNotBlank() }
        if (banners.isEmpty()) {
            visibility = View.GONE
            stopAutoPlay()
            return
        }
        visibility = View.VISIBLE
        recyclerView.adapter = object : RecyclerView.Adapter<PageHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
                return PageHolder(createPageView())
            }

            override fun getItemCount(): Int =
                if (banners.size >= 2) banners.size * LOOP_COPIES else banners.size

            override fun onBindViewHolder(holder: PageHolder, position: Int) {
                val banner = banners[position % banners.size]
                holder.image.setImageDrawable(null)
                val fullUrl = if (banner.imageUrl.startsWith("http")) banner.imageUrl
                              else "${ApiService.BASE_URL}${banner.imageUrl}"
                ImageLoaderUtil.load(holder.image, fullUrl, crossfade = true)
            }
        }
        requestLayout()
        // 从中间份起步，两侧都有足够页数
        startIndex = if (banners.size >= 2) {
            (LOOP_COPIES / 2) * banners.size
        } else 0
        layoutManager.scrollToPositionWithOffset(startIndex, 0)
        // 宽度/尺寸首次布局后才可用：校正起始位 + 套用居中缩放
        recyclerView.post {
            if (banners.size >= 2) {
                if (layoutManager.itemCount > startIndex) {
                    layoutManager.scrollToPositionWithOffset(startIndex, 0)
                }
            }
            updatePageTransforms()
        }

        if (banners.size >= 2) {
            renderDots()
            updateDots(startIndex % banners.size)
            dotContainer.visibility = View.VISIBLE
            scheduleAutoPlay()
        } else {
            stopAutoPlay()
            dotContainer.visibility = View.GONE
            dotContainer.removeAllViews()
        }
    }

    // 每页一张圆角卡片（ImageLoaderUtil 后台加载回填），内容区域恰为容器去掉两侧 padding 的矩形
    private fun createPageView(): ImageView {
        return ImageView(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = dp(4f).toFloat()
            background = roundedCardBackground()
        }
    }

    private fun roundedCardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14f).toFloat()
            setColor(0xFFF3F0FA.toInt())
        }
    }

    // 滚动时按“距中距离”调整每页缩放：居中 1.0，越靠边越小（中间突出）
    private fun updatePageTransforms() {
        if (cardWidthPx <= 0) return
        val containerCenter = recyclerView.width / 2f
        if (containerCenter <= 0f) return
        for (i in 0 until layoutManager.childCount) {
            val child = layoutManager.getChildAt(i) ?: continue
            val childCenter = (child.left + child.right) / 2f
            val off = abs(childCenter - containerCenter) / cardWidthPx
            val scale = 1f - (1f - SIDE_SCALE) * min(off, 1f)
            child.scaleX = scale
            child.scaleY = scale
        }
    }

    private class PageHolder(val image: ImageView) : RecyclerView.ViewHolder(image)

    private fun renderDots() {
        dotContainer.removeAllViews()
        for (i in banners.indices) {
            val dot = View(context)
            dot.background = resources.getDrawable(R.drawable.bg_dot_carousel_inactive, context.theme)
            dotContainer.addView(dot, LinearLayout.LayoutParams(
                dp(7f), dp(7f)
            ).apply { setMargins(dp(3f), 0, dp(3f), 0) })
        }
    }

    private fun updateDots(index: Int) {
        if (banners.size < 2 || dotContainer.childCount != banners.size) return
        currentPage = index
        for (i in 0 until dotContainer.childCount) {
            val dot = dotContainer.getChildAt(i)
            val active = i == index
            val size = if (active) dp(8f) else dp(6f)
            val lp = dot.layoutParams as LinearLayout.LayoutParams
            lp.width = size
            lp.height = size
            dot.layoutParams = lp
            dot.background = resources.getDrawable(
                if (active) R.drawable.bg_dot_carousel_active
                else R.drawable.bg_dot_carousel_inactive, context.theme)
        }
    }

    private fun scheduleAutoPlay() {
        if (banners.size < 2 || isTouching) return
        stopAutoPlay()
        autoHandler.postDelayed(autoPlayTask, AUTO_PLAY_INTERVAL)
    }

    private fun stopAutoPlay() {
        autoHandler.removeCallbacksAndMessages(null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 若数据在挂载前就绪（先 setBanners 后 attach），在此补启动轮播
        if (banners.size >= 2) scheduleAutoPlay()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoPlay()
    }

    fun release() {
        stopAutoPlay()
    }
}
