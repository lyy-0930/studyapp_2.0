package com.studyapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.model.User
import java.text.SimpleDateFormat
import java.util.*

/**
 * 用户列表适配器
 * 用于显示用户信息和管理（删除）用户
 */
class UserAdapter(
    private var users: List<User> = emptyList(),
    private var onDeleteClickListener: OnDeleteClickListener? = null
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    interface OnDeleteClickListener {
        fun onDeleteClick(user: User, position: Int)
    }

    fun setOnDeleteClickListener(listener: OnDeleteClickListener) {
        this.onDeleteClickListener = listener
    }

    fun updateData(newUsers: List<User>) {
        this.users = newUsers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, position)
    }

    override fun getItemCount(): Int = users.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        private val roleTextView: TextView = itemView.findViewById(R.id.roleTextView)
        private val userIdTextView: TextView = itemView.findViewById(R.id.userIdTextView)
        private val registrationDateTextView: TextView = itemView.findViewById(R.id.registrationDateTextView)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(user: User, position: Int) {
            // 设置用户名
            usernameTextView.text = user.username

            // 设置角色，添加颜色和图标
            val roleText = when (user.role.lowercase()) {
                "admin" -> "👑 管理员"
                "teacher" -> "👨‍🏫 教师"
                "student" -> "👨‍🎓 学生"
                else -> "👤 ${user.role}"
            }
            roleTextView.text = roleText

            // 设置用户ID
            userIdTextView.text = "ID: ${user.id}"

            // 设置注册日期
            registrationDateTextView.text = "注册时间: ${formatDate(user.registrationDate)}"

            // 设置删除按钮点击事件
            deleteButton.setOnClickListener {
                onDeleteClickListener?.onDeleteClick(user, position)
            }

            // 如果是当前用户，禁用删除按钮并显示提示
            val sharedPref = itemView.context.getSharedPreferences("login_prefs", android.content.Context.MODE_PRIVATE)
            val currentUserId = sharedPref.getInt("user_id", 0)
            if (user.id == currentUserId) {
                deleteButton.isEnabled = false
                deleteButton.alpha = 0.5f
                deleteButton.contentDescription = "不能删除当前登录用户"
            } else {
                deleteButton.isEnabled = true
                deleteButton.alpha = 1.0f
                deleteButton.contentDescription = "删除用户"
            }
        }

        private fun formatDate(date: Date): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            return formatter.format(date)
        }
    }
}