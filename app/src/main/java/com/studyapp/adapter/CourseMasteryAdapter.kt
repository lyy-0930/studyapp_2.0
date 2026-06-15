package com.studyapp.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.manager.AdminDataManager

/**
 * 课程掌握度适配器
 */
class CourseMasteryAdapter : ListAdapter<AdminDataManager.CourseMasteryStats, CourseMasteryAdapter.CourseMasteryViewHolder>(
    CourseMasteryDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseMasteryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course_mastery, parent, false)
        return CourseMasteryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseMasteryViewHolder, position: Int) {
        val stats = getItem(position)
        holder.bind(stats)
    }

    /**
     * ViewHolder类
     */
    inner class CourseMasteryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val courseIconTextView: TextView = itemView.findViewById(R.id.courseIconTextView)
        private val masteryIndicator: View = itemView.findViewById(R.id.masteryIndicator)
        private val courseNameTextView: TextView = itemView.findViewById(R.id.courseNameTextView)
        private val masteryLevelTextView: TextView = itemView.findViewById(R.id.masteryLevelTextView)
        private val accuracyTextView: TextView = itemView.findViewById(R.id.accuracyTextView)
        private val completionRateTextView: TextView = itemView.findViewById(R.id.completionRateTextView)
        private val studentCountTextView: TextView = itemView.findViewById(R.id.studentCountTextView)
        private val masteryLevelTag: TextView = itemView.findViewById(R.id.masteryLevelTag)

        fun bind(stats: AdminDataManager.CourseMasteryStats) {
            // 设置课程图标（根据课程类型）
            val icon = getCourseIcon(stats.courseName)
            courseIconTextView.text = icon

            // 设置掌握度指示器颜色
            val masteryColor = Color.parseColor(stats.getMasteryLevelColor())
            masteryIndicator.setBackgroundColor(masteryColor)

            // 设置课程名称
            courseNameTextView.text = stats.courseName

            // 设置掌握度
            masteryLevelTextView.text = stats.getFormattedAverageMasteryLevel()

            // 设置正确率
            accuracyTextView.text = stats.getFormattedAverageQuizAccuracy()

            // 设置完成率
            completionRateTextView.text = stats.getFormattedAverageCompletionRate()

            // 设置学习学生数
            studentCountTextView.text = "学习学生：${stats.totalStudents}人"

            // 设置掌握度等级标签
            masteryLevelTag.text = stats.getMasteryLevel()

            // 设置掌握度等级标签颜色
            val tagColor = Color.parseColor(stats.getMasteryLevelColor())
            masteryLevelTag.setBackgroundColor(tagColor)

            // 根据背景颜色调整文字颜色（确保可读性）
            masteryLevelTag.setTextColor(
                if (isDarkColor(tagColor)) {
                    Color.WHITE
                } else {
                    Color.BLACK
                }
            )

            // 添加点击事件
            itemView.setOnClickListener {
                // 可以在这里添加点击事件，例如显示课程详情
                // 暂时不做处理
            }
        }

        /**
         * 根据课程名称获取图标
         */
        private fun getCourseIcon(courseName: String): String {
            return when {
                courseName.contains("Android") -> "📱"
                courseName.contains("Kotlin") -> "⚡"
                courseName.contains("Jetpack") -> "🎨"
                courseName.contains("架构") -> "🏗️"
                courseName.contains("性能") -> "🚀"
                courseName.contains("设计") -> "🎯"
                courseName.contains("网络") -> "🌐"
                courseName.contains("数据库") -> "🗄️"
                courseName.contains("测试") -> "🧪"
                courseName.contains("项目") -> "📂"
                else -> "📚"
            }
        }

        /**
         * 判断颜色是否为深色
         */
        private fun isDarkColor(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.5
        }
    }
}

/**
 * 差分回调类，用于优化列表更新
 */
class CourseMasteryDiffCallback : DiffUtil.ItemCallback<AdminDataManager.CourseMasteryStats>() {
    override fun areItemsTheSame(oldItem: AdminDataManager.CourseMasteryStats, newItem: AdminDataManager.CourseMasteryStats): Boolean {
        return oldItem.courseId == newItem.courseId
    }

    override fun areContentsTheSame(oldItem: AdminDataManager.CourseMasteryStats, newItem: AdminDataManager.CourseMasteryStats): Boolean {
        return oldItem == newItem &&
                oldItem.averageMasteryLevel == newItem.averageMasteryLevel &&
                oldItem.averageQuizAccuracy == newItem.averageQuizAccuracy &&
                oldItem.averageCompletionRate == newItem.averageCompletionRate
    }
}