package com.studyapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.manager.AdminDataManager

/**
 * 学生学习情况适配器
 */
class LearningStatsAdapter : ListAdapter<AdminDataManager.StudentLearningStats, LearningStatsAdapter.LearningStatsViewHolder>(
    LearningStatsDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LearningStatsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_learning_stats, parent, false)
        return LearningStatsViewHolder(view)
    }

    override fun onBindViewHolder(holder: LearningStatsViewHolder, position: Int) {
        val stats = getItem(position)
        val rank = position + 1 // 排名从1开始
        holder.bind(stats, rank)
    }

    /**
     * ViewHolder类
     */
    inner class LearningStatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rankTextView: TextView = itemView.findViewById(R.id.rankTextView)
        private val studentNameTextView: TextView = itemView.findViewById(R.id.studentNameTextView)
        private val studyTimeTextView: TextView = itemView.findViewById(R.id.studyTimeTextView)
        private val completionRateTextView: TextView = itemView.findViewById(R.id.completionRateTextView)
        private val accuracyTextView: TextView = itemView.findViewById(R.id.accuracyTextView)
        private val completedCoursesTextView: TextView = itemView.findViewById(R.id.completedCoursesTextView)
        private val learningLevelIcon: TextView = itemView.findViewById(R.id.learningLevelIcon)

        fun bind(stats: AdminDataManager.StudentLearningStats, rank: Int) {
            // 设置排名
            rankTextView.text = rank.toString()

            // 设置学生姓名
            studentNameTextView.text = stats.username

            // 设置学习时长
            studyTimeTextView.text = stats.getFormattedStudyDuration()

            // 设置完成率
            completionRateTextView.text = stats.getFormattedAverageCompletionRate()

            // 设置正确率
            accuracyTextView.text = stats.getFormattedAverageQuizAccuracy()

            // 设置已完成的课程
            completedCoursesTextView.text = "已完成课程：${stats.completedCourseCount}门"

            // 设置学习等级图标
            val (icon, colorRes) = getLearningLevelIcon(stats.averageCompletionRate, stats.averageQuizAccuracy)
            learningLevelIcon.text = icon
            learningLevelIcon.setTextColor(itemView.context.getColor(colorRes))

            // 添加点击事件
            itemView.setOnClickListener {
                // 可以在这里添加点击事件，例如显示学生详情
                // 暂时不做处理
            }
        }

        /**
         * 根据学习表现获取等级图标和颜色
         */
        private fun getLearningLevelIcon(completionRate: Double, accuracy: Double): Pair<String, Int> {
            val overallScore = (completionRate * 0.6 + accuracy * 0.4) // 完成率权重60%，正确率权重40%

            return when {
                overallScore >= 90 -> Pair("🏆", R.color.tech_orange_warning) // 金牌
                overallScore >= 80 -> Pair("⭐", R.color.tech_green_data) // 星星
                overallScore >= 70 -> Pair("🔸", R.color.tech_cyan_info) // 菱形
                overallScore >= 60 -> Pair("🔹", R.color.tech_blue_accent) // 方块
                else -> Pair("📘", R.color.tech_blue_light) // 书本
            }
        }
    }
}

/**
 * 差分回调类，用于优化列表更新
 */
class LearningStatsDiffCallback : DiffUtil.ItemCallback<AdminDataManager.StudentLearningStats>() {
    override fun areItemsTheSame(oldItem: AdminDataManager.StudentLearningStats, newItem: AdminDataManager.StudentLearningStats): Boolean {
        return oldItem.userId == newItem.userId
    }

    override fun areContentsTheSame(oldItem: AdminDataManager.StudentLearningStats, newItem: AdminDataManager.StudentLearningStats): Boolean {
        return oldItem == newItem &&
                oldItem.totalStudyDuration == newItem.totalStudyDuration &&
                oldItem.averageCompletionRate == newItem.averageCompletionRate &&
                oldItem.averageQuizAccuracy == newItem.averageQuizAccuracy
    }
}