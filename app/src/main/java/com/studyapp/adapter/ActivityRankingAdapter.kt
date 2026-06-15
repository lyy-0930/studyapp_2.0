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
 * 活跃度排行榜适配器
 */
class ActivityRankingAdapter : ListAdapter<AdminDataManager.UserActivityStats, ActivityRankingAdapter.ActivityRankingViewHolder>(
    ActivityRankingDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityRankingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_ranking, parent, false)
        return ActivityRankingViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityRankingViewHolder, position: Int) {
        val stats = getItem(position)
        val rank = position + 1 // 排名从1开始
        holder.bind(stats, rank)
    }

    /**
     * ViewHolder类
     */
    inner class ActivityRankingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rankTextView: TextView = itemView.findViewById(R.id.rankTextView)
        private val rankLabelTextView: TextView = itemView.findViewById(R.id.rankLabelTextView)
        private val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        private val roleTextView: TextView = itemView.findViewById(R.id.roleTextView)
        private val activityScoreTextView: TextView = itemView.findViewById(R.id.activityScoreTextView)
        private val studyTimeTextView: TextView = itemView.findViewById(R.id.studyTimeTextView)
        private val activityLevelTextView: TextView = itemView.findViewById(R.id.activityLevelTextView)
        private val rankIconTextView: TextView = itemView.findViewById(R.id.rankIconTextView)

        fun bind(stats: AdminDataManager.UserActivityStats, rank: Int) {
            // 设置排名
            rankTextView.text = rank.toString()

            // 设置排名背景颜色（前三名特殊显示）
            when (rank) {
                1 -> {
                    // 第一名：金色
                    rankTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.tech_orange_warning))
                    rankLabelTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.tech_orange_warning))
                    rankIconTextView.text = "🥇"
                    rankIconTextView.visibility = View.VISIBLE
                }
                2 -> {
                    // 第二名：银色
                    rankTextView.setTextColor(Color.parseColor("#C0C0C0")) // 银色
                    rankLabelTextView.setTextColor(Color.parseColor("#C0C0C0"))
                    rankIconTextView.text = "🥈"
                    rankIconTextView.visibility = View.VISIBLE
                }
                3 -> {
                    // 第三名：铜色
                    rankTextView.setTextColor(Color.parseColor("#CD7F32")) // 铜色
                    rankLabelTextView.setTextColor(Color.parseColor("#CD7F32"))
                    rankIconTextView.text = "🥉"
                    rankIconTextView.visibility = View.VISIBLE
                }
                else -> {
                    // 其他名次：默认颜色
                    rankTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.tech_blue_light))
                    rankLabelTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.tech_blue_accent))
                    rankIconTextView.visibility = View.GONE
                }
            }

            // 设置用户名和角色
            usernameTextView.text = stats.username
            roleTextView.text = translateRole(stats.role)

            // 设置活跃度分数
            activityScoreTextView.text = stats.getFormattedActivityScore()

            // 设置学习时长
            studyTimeTextView.text = stats.getFormattedStudyDuration()

            // 设置活跃度等级
            val activityLevel = getActivityLevel(stats.totalActivityScore)
            activityLevelTextView.text = activityLevel

            // 根据活跃度等级设置背景颜色
            val levelColor = getActivityLevelColor(stats.totalActivityScore)
            activityLevelTextView.setBackgroundColor(Color.parseColor(levelColor))

            // 添加点击事件
            itemView.setOnClickListener {
                // 可以在这里添加点击事件，例如显示用户详情
                // 暂时不做处理
            }
        }

        /**
         * 根据活跃度分数获取等级
         */
        private fun getActivityLevel(score: Double): String {
            return when {
                score >= 200 -> "极高活跃度"
                score >= 100 -> "高活跃度"
                score >= 50 -> "中等活跃度"
                score >= 20 -> "一般活跃度"
                else -> "低活跃度"
            }
        }

        /**
         * 根据活跃度分数获取等级颜色
         */
        private fun getActivityLevelColor(score: Double): String {
            return when {
                score >= 200 -> "#4CAF50" // 绿色
                score >= 100 -> "#8BC34A" // 浅绿
                score >= 50 -> "#FFC107" // 黄色
                score >= 20 -> "#FF9800" // 橙色
                else -> "#9E9E9E" // 灰色
            }
        }

        /**
         * 翻译角色为中文
         */
        private fun translateRole(role: String): String {
            return when (role.lowercase()) {
                "student" -> "学生"
                "teacher" -> "教师"
                else -> role
            }
        }
    }
}

/**
 * 差分回调类，用于优化列表更新
 */
class ActivityRankingDiffCallback : DiffUtil.ItemCallback<AdminDataManager.UserActivityStats>() {
    override fun areItemsTheSame(oldItem: AdminDataManager.UserActivityStats, newItem: AdminDataManager.UserActivityStats): Boolean {
        return oldItem.userId == newItem.userId
    }

    override fun areContentsTheSame(oldItem: AdminDataManager.UserActivityStats, newItem: AdminDataManager.UserActivityStats): Boolean {
        return oldItem == newItem
    }
}