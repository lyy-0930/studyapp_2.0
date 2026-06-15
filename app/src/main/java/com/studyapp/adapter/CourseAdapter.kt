package com.studyapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.util.ImageLoaderUtil
import com.studyapp.R
import com.studyapp.manager.ApiService
import com.studyapp.model.Course

class CourseAdapter(
    private var courseList: List<Course> = listOf()
) : RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {

    // 记录展开描述的课程位置
    private val expandedDescriptions = mutableSetOf<Int>()

    // 按钮模式枚举
    enum class ButtonMode {
        SELECT, // 选课模式（显示选课/退选）
        PLAY    // 播放模式（显示播放）
    }

    // 点击监听器接口
    interface OnCourseSelectListener {
        fun onCourseSelected(course: Course, position: Int)
    }

    // 播放监听器接口
    interface OnCoursePlayListener {
        fun onCoursePlay(course: Course, position: Int)
    }

    // 卡片点击监听器接口（用于学生查看资料）
    interface OnCourseClickListener {
        fun onCourseClick(course: Course, position: Int)
    }

    private var onCourseSelectListener: OnCourseSelectListener? = null
    private var onCoursePlayListener: OnCoursePlayListener? = null
    private var onCourseClickListener: OnCourseClickListener? = null
    private var buttonMode: ButtonMode = ButtonMode.SELECT // 默认选课模式

    fun setOnCourseSelectListener(listener: OnCourseSelectListener?) {
        this.onCourseSelectListener = listener
    }

    fun setOnCoursePlayListener(listener: OnCoursePlayListener?) {
        this.onCoursePlayListener = listener
    }

    fun setOnCourseClickListener(listener: OnCourseClickListener?) {
        this.onCourseClickListener = listener
    }

    /**
     * 设置按钮模式
     * @param mode 按钮模式：SELECT（选课）或PLAY（播放）
     */
    fun setButtonMode(mode: ButtonMode) {
        buttonMode = mode
        notifyDataSetChanged() // 通知更新所有视图
    }

    fun updateData(newList: List<Course>) {
        courseList = newList
        expandedDescriptions.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courseList[position]
        holder.bind(course, position)

        // 设置整张卡片点击事件（学生查看资料）
        holder.itemView.setOnClickListener {
            onCourseClickListener?.onCourseClick(course, position)
        }

        // 设置按钮点击事件
        holder.selectButton.setOnClickListener {
            when (buttonMode) {
                ButtonMode.PLAY -> {
                    // 播放模式：触发播放监听器
                    onCoursePlayListener?.onCoursePlay(course, position)
                    // 显示播放提示
                    Toast.makeText(holder.itemView.context, "播放课程: ${course.name}", Toast.LENGTH_SHORT).show()
                }
                ButtonMode.SELECT -> {
                    // 选课模式：触发选课监听器
                    onCourseSelectListener?.onCourseSelected(course, position)
                    // 显示选课提示（根据当前状态）
                    val action = if (course.isSelected) "退选" else "选课"
                    Toast.makeText(holder.itemView.context, "${action}课程: ${course.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun getItemCount(): Int = courseList.size

    inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val courseCoverImage: ImageView = itemView.findViewById(R.id.courseCoverImage)
        private val courseIconTextView: TextView = itemView.findViewById(R.id.courseIconTextView)
        private val courseNameTextView: TextView = itemView.findViewById(R.id.courseNameTextView)
        private val teacherTextView: TextView = itemView.findViewById(R.id.teacherTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        private val creditTextView: TextView = itemView.findViewById(R.id.creditTextView)
        private val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val progressText: TextView = itemView.findViewById(R.id.progressText)
        val selectButton: Button = itemView.findViewById(R.id.selectButton)

        fun bind(course: Course, position: Int) {
            // 加载课程封面图（如果有）
            val imageUrl = course.imageUrl
            if (!imageUrl.isNullOrEmpty()) {
                val fullUrl = if (imageUrl.startsWith("http")) imageUrl
                    else "${ApiService.BASE_URL}$imageUrl"
                courseCoverImage.visibility = View.VISIBLE
                courseIconTextView.visibility = View.GONE
                ImageLoaderUtil.load(courseCoverImage, fullUrl, crossfade = true)
            } else {
                courseCoverImage.visibility = View.GONE
                courseIconTextView.visibility = View.VISIBLE
            }

            // 设置视频信息
            courseNameTextView.text = course.name
            teacherTextView.text = "教师：${course.teacher}"

            // 处理描述信息的展开/收起
            val isExpanded = expandedDescriptions.contains(position)
            descriptionTextView.maxLines = if (isExpanded) Int.MAX_VALUE else 2
            descriptionTextView.text = course.description
            descriptionTextView.setOnClickListener {
                toggleDescription(position, descriptionTextView)
            }

            // 使用creditTextView显示学分和分类
            val creditText = "学分：${course.credit}"
            val categoryText = if (!course.categoryName.isNullOrEmpty()) "  |  分类：${course.categoryName}" else ""
            creditTextView.text = creditText + categoryText

            // 显示学习进度（选课模式下隐藏）
            if (buttonMode == ButtonMode.PLAY) {
                val progress = course.progress.coerceIn(0, 100)
                progressBar.progress = progress
                progressText.text = "${progress}%"
                progressBar.visibility = View.VISIBLE
                progressText.visibility = View.VISIBLE
            } else {
                progressBar.visibility = View.GONE
                progressText.visibility = View.GONE
            }

            // 更新状态显示（根据选课状态）
            updateSelectionState(course.isSelected)
        }

        private fun toggleDescription(position: Int, textView: TextView) {
            if (expandedDescriptions.contains(position)) {
                expandedDescriptions.remove(position)
                textView.maxLines = 2
            } else {
                expandedDescriptions.add(position)
                textView.maxLines = Int.MAX_VALUE
            }
        }

        fun updateSelectionState(isSelected: Boolean) {
            when (buttonMode) {
                ButtonMode.PLAY -> {
                    // 播放模式：显示"播放"按钮
                    statusTextView.text = "已选课"
                    statusTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.tsinghua_purple_dark))
                    selectButton.text = "播放"
                }
                ButtonMode.SELECT -> {
                    // 选课模式：根据选课状态显示
                    if (isSelected) {
                        statusTextView.text = "已选课"
                        statusTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.tsinghua_purple_dark))
                        selectButton.text = "退选"
                    } else {
                        statusTextView.text = "未选课"
                        statusTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.darker_gray))
                        selectButton.text = "选课"
                    }
                }
            }
            selectButton.setBackgroundResource(R.drawable.ic_button_background)
        }
    }
}