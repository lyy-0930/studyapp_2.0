package com.studyapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.model.Student

/**
 * 优秀学生列表适配器
 */
class TopStudentsAdapter(
    private var studentList: List<Student> = listOf()
) : RecyclerView.Adapter<TopStudentsAdapter.StudentViewHolder>() {

    // 点击监听器接口
    interface OnStudentClickListener {
        fun onStudentClick(student: Student, position: Int)
    }

    private var onStudentClickListener: OnStudentClickListener? = null

    fun setOnStudentClickListener(listener: OnStudentClickListener?) {
        this.onStudentClickListener = listener
    }

    /**
     * 更新数据
     */
    fun updateData(newList: List<Student>) {
        studentList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = studentList[position]
        holder.bind(student)

        // 设置点击事件
        holder.itemView.setOnClickListener {
            onStudentClickListener?.onStudentClick(student, position)
        }
    }

    override fun getItemCount(): Int = studentList.size

    inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rankTextView: TextView = itemView.findViewById(R.id.rankTextView)
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        private val progressTextView: TextView = itemView.findViewById(R.id.progressTextView)
        private val progressBar: View = itemView.findViewById(R.id.progressBar)

        fun bind(student: Student) {
            // 设置排名（位置+1）
            rankTextView.text = "#${absoluteAdapterPosition + 1}"

            // 设置学生姓名
            nameTextView.text = student.name

            // 设置进度文本
            progressTextView.text = student.getFormattedProgress()

            // 根据进度设置进度条宽度和颜色
            updateProgressBar(student.progress)
        }

        private fun updateProgressBar(progress: Int) {
            // 设置进度条宽度（最大宽度为屏幕宽度的50%）
            val layoutParams = progressBar.layoutParams
            layoutParams.width = (itemView.width * 0.5 * progress / 100).toInt()
            progressBar.layoutParams = layoutParams

            // 根据进度设置颜色
            val colorRes = when {
                progress >= 90 -> R.color.tsinghua_purple_dark  // 优秀
                progress >= 70 -> R.color.tsinghua_purple       // 良好
                progress >= 50 -> R.color.tsinghua_purple_light // 中等
                else -> R.color.darker_gray                     // 待提高
            }
            progressBar.setBackgroundColor(
                ContextCompat.getColor(itemView.context, colorRes)
            )
        }
    }
}