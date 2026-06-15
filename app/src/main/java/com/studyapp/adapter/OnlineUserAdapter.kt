package com.studyapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.model.User

/**
 * 在线用户列表适配器
 */
class OnlineUserAdapter : ListAdapter<User, OnlineUserAdapter.OnlineUserViewHolder>(
    OnlineUserDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnlineUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_online_user, parent, false)
        return OnlineUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnlineUserViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
    }

    /**
     * ViewHolder类
     */
    inner class OnlineUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userIconTextView: TextView = itemView.findViewById(R.id.userIconTextView)
        private val onlineIndicator: View = itemView.findViewById(R.id.onlineIndicator)
        private val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        private val roleTextView: TextView = itemView.findViewById(R.id.roleTextView)
        private val lastLoginTextView: TextView = itemView.findViewById(R.id.lastLoginTextView)
        private val studyTimeTextView: TextView = itemView.findViewById(R.id.studyTimeTextView)
        private val completionRateTextView: TextView = itemView.findViewById(R.id.completionRateTextView)
        private val onlineStatusTextView: TextView = itemView.findViewById(R.id.onlineStatusTextView)

        fun bind(user: User) {
            // 设置用户图标（根据角色使用不同图标）
            val icon = when (user.role) {
                "teacher" -> "👨‍🏫"
                "admin" -> "👨‍💼"
                else -> "👤" // student
            }
            userIconTextView.text = icon

            // 设置在线状态指示器
            onlineIndicator.visibility = if (user.isOnline) View.VISIBLE else View.GONE

            // 设置用户名和角色
            usernameTextView.text = user.username
            roleTextView.text = when (user.role) {
                "student" -> "学生"
                "teacher" -> "教师"
                "admin" -> "管理员"
                else -> user.role
            }

            // 设置最后登录时间
            lastLoginTextView.text = "最后登录：${user.getFormattedLastLoginDate()}"

            // 设置学习时长
            studyTimeTextView.text = user.getFormattedTotalStudyTime()

            // 设置完成率
            completionRateTextView.text = user.getFormattedCompletionRate()

            // 设置在线状态文字
            onlineStatusTextView.text = user.getOnlineStatusText()
            onlineStatusTextView.setTextColor(
                if (user.isOnline) {
                    itemView.context.getColor(R.color.tech_green_data)
                } else {
                    itemView.context.getColor(R.color.tech_blue_accent)
                }
            )

            // 添加点击事件
            itemView.setOnClickListener {
                // 可以在这里添加点击事件，例如显示用户详情
                // 暂时不做处理
            }
        }
    }
}

/**
 * 差分回调类，用于优化列表更新
 */
class OnlineUserDiffCallback : DiffUtil.ItemCallback<User>() {
    override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
        return oldItem == newItem &&
                oldItem.isOnline == newItem.isOnline &&
                oldItem.lastLoginDate == newItem.lastLoginDate
    }
}