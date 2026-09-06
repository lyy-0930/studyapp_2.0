package com.studyapp.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 图片加载工具类
 * 替代 Coil 图片加载库，使用原生 HttpURLConnection + BitmapFactory
 * 支持圆形裁剪和交叉淡入效果
 */
object ImageLoaderUtil {

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 从URL加载图片到ImageView
     * @param imageView 目标ImageView
     * @param url 图片URL
     * @param crossfade 是否启用交叉淡入动画
     * @param circleCrop 是否裁剪为圆形
     */
    fun load(
        imageView: ImageView,
        url: String?,
        crossfade: Boolean = false,
        circleCrop: Boolean = false
    ) {
        if (url.isNullOrEmpty()) return

        executor.execute {
            try {
                val bitmap = downloadBitmap(url)
                if (bitmap != null) {
                    mainHandler.post {
                        val finalBitmap = if (circleCrop) {
                            getCircleBitmap(bitmap)
                        } else bitmap

                        if (crossfade) {
                            // 淡入动画
                            val oldDrawable = imageView.drawable
                            val newDrawable = BitmapDrawable(imageView.resources, finalBitmap)
                            imageView.setImageDrawable(newDrawable)
                            if (oldDrawable != null) {
                                imageView.alpha = 0f
                                imageView.animate().alpha(1f).setDuration(300).start()
                            }
                        } else {
                            imageView.setImageBitmap(finalBitmap)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 自适应真实宽高比加载（公众号正文插图用）
     * 图片铺满给定 widthPx 宽度，高度 = width × 图片实际高/宽（可让竖图变高、横图变矮）
     * 加载成功后才按实际比例设置 ImageView 的宽高，避免拉伸或留白
     */
    fun loadAdaptive(
        imageView: ImageView,
        url: String?,
        widthPx: Int,
        crossfade: Boolean = false
    ) {
        if (url.isNullOrEmpty() || widthPx <= 0) return
        executor.execute {
            try {
                val bitmap = downloadBitmap(url) ?: return@execute
                val w = bitmap.width.coerceAtLeast(1)
                val h = (widthPx.toLong() * bitmap.height / w).toInt().coerceAtLeast(1)
                mainHandler.post {
                    val lp = imageView.layoutParams
                    if (lp != null) {
                        lp.width = widthPx
                        lp.height = h
                        imageView.layoutParams = lp
                    }
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    if (crossfade) {
                        imageView.alpha = 0f
                        imageView.setImageBitmap(bitmap)
                        imageView.animate().alpha(1f).setDuration(250).start()
                    } else {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 同步下载位图（调用方应放 IO 线程）；编辑器取真实宽高比用
     */
    fun loadBitmap(urlString: String): Bitmap? = downloadBitmap(urlString)

    /**
     * 下载位图
     */
    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            try {
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = conn.inputStream
                    BitmapFactory.decodeStream(inputStream)
                } else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 裁剪为圆形位图
     */
    private fun getCircleBitmap(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.Rect(0, 0, size, size)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f

        canvas.drawCircle(cx, cy, radius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, rect, rect, paint)
        return output
    }
}
