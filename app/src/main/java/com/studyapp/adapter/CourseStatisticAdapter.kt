package com.studyapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.model.CourseStatistic
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 课程统计列表适配器
 */
class CourseStatisticAdapter(
    private val onCourseClick: ((CourseStatistic) -> Unit)? = null
) : ListAdapter<CourseStatistic, CourseStatisticAdapter.CourseStatisticViewHolder>(
    CourseStatisticDiffCallback()
) {

    // 日期格式化器
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseStatisticViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course_statistic, parent, false)
        return CourseStatisticViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseStatisticViewHolder, position: Int) {
        val courseStatistic = getItem(position)
        holder.bind(courseStatistic)
    }

    /**
     * ViewHolder类
     */
    inner class CourseStatisticViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val courseNameTextView: TextView = itemView.findViewById(R.id.courseNameTextView)
        private val uploadDateTextView: TextView = itemView.findViewById(R.id.uploadDateTextView)
        private val enrolledStudentsTextView: TextView = itemView.findViewById(R.id.enrolledStudentsTextView)
        private val averageDurationTextView: TextView = itemView.findViewById(R.id.averageDurationTextView)
        private val clickRateTextView: TextView = itemView.findViewById(R.id.clickRateTextView)
        private val totalViewsTextView: TextView = itemView.findViewById(R.id.totalViewsTextView)
        private val completionRateTextView: TextView = itemView.findViewById(R.id.completionRateTextView)
        private val totalWatchTimeTextView: TextView = itemView.findViewById(R.id.totalWatchTimeTextView)
        private val summaryTextView: TextView = itemView.findViewById(R.id.summaryTextView)

        fun bind(courseStatistic: CourseStatistic) {
            // 设置课程基本信息
            courseNameTextView.text = courseStatistic.courseName
            uploadDateTextView.text = "上传于 ${dateFormat.format(courseStatistic.uploadDate)}"

            // 设置统计指标
            enrolledStudentsTextView.text = courseStatistic.enrolledStudents.toString()
            averageDurationTextView.text = courseStatistic.getFormattedAverageDuration()
            clickRateTextView.text = courseStatistic.getFormattedClickRate()
            totalViewsTextView.text = courseStatistic.totalViews.toString() + "次"
            completionRateTextView.text = courseStatistic.getFormattedCompletionRate()
            totalWatchTimeTextView.text = courseStatistic.getFormattedTotalWatchTime()

            // 设置统计摘要
            summaryTextView.text = courseStatistic.getSummary()

            // 点击查看详情
            itemView.setOnClickListener {
                onCourseClick?.invoke(courseStatistic)
            }
        }
    }
}

/**
 * 差分回调类，用于优化列表更新
 */
class CourseStatisticDiffCallback : DiffUtil.ItemCallback<CourseStatistic>() {
    override fun areItemsTheSame(oldItem: CourseStatistic, newItem: CourseStatistic): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CourseStatistic, newItem: CourseStatistic): Boolean {
        return oldItem == newItem
    }
}
