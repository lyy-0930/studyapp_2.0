package com.studyapp.util

import android.animation.ValueAnimator
import android.widget.TextView
import androidx.core.animation.doOnEnd

/**
 * 数字滚动动画工具类
 * 让数值从 0 滚动到目标值
 */
object CountAnimationUtil {

    /**
     * 为 TextView 执行数字滚动动画
     * @param textView 目标 TextView
     * @param targetValue 目标整数值
     * @param prefix 数字前缀（如 "+"）
     * @param suffix 数字后缀（如 "%"）
     * @param duration 动画时长（毫秒），默认 800ms
     */
    fun animateInt(
        textView: TextView,
        targetValue: Int,
        prefix: String = "",
        suffix: String = "",
        duration: Long = 800L
    ) {
        val animator = ValueAnimator.ofInt(0, targetValue).apply {
            this.duration = duration
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                textView.text = "${prefix}${animation.animatedValue}$suffix"
            }
        }
        // 确保最终值是精确的
        animator.doOnEnd {
            textView.text = "${prefix}$targetValue$suffix"
        }
        animator.start()
    }

    /**
     * 为 TextView 执行浮点数滚动动画
     * @param textView 目标 TextView
     * @param targetValue 目标浮点值
     * @param prefix 数字前缀
     * @param suffix 数字后缀
     * @param decimals 小数位数，默认 1
     * @param duration 动画时长（毫秒），默认 800ms
     */
    fun animateFloat(
        textView: TextView,
        targetValue: Double,
        prefix: String = "",
        suffix: String = "",
        decimals: Int = 1,
        duration: Long = 800L
    ) {
        val formatStr = "%.${decimals}f"
        val animator = ValueAnimator.ofFloat(0f, targetValue.toFloat()).apply {
            this.duration = duration
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                textView.text = "${prefix}${String.format(formatStr, animation.animatedValue)}$suffix"
            }
        }
        animator.doOnEnd {
            textView.text = "${prefix}${String.format(formatStr, targetValue)}$suffix"
        }
        animator.start()
    }
}
