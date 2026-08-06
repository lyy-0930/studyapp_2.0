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
import com.studyapp.model.CourseCollection

/**
 * 课程列表适配器
 * 支持两种条目：普通课程 + 合集（合集显示在课程列表前部，样式与课程卡片一致）
 * 两种条目都复用 item_course 布局
 */
class CourseAdapter(
    private var courseList: List<Course> = listOf(),
    private var collectionList: List<CourseCollection> = listOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // 记录展开描述的课程位置
    private val expandedDescriptions = mutableSetOf<Int>()

    // 按钮模式枚举
    enum class ButtonMode {
        SELECT, // 选课模式（显示选课/退选）
        PLAY    // 播放模式（显示播放）
    }

    companion object {
        private const val TYPE_COURSE = 0
        private const val TYPE_COLLECTION = 1
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

    // 合集点击监听器接口
    interface OnCollectionClickListener {
        fun onCollectionClick(collection: CourseCollection, position: Int)
    }

    // 合集选课/退选监听器接口
    interface OnCollectionSelectListener {
        fun onCollectionSelected(collection: CourseCollection, position: Int)
    }

    private var onCourseSelectListener: OnCourseSelectListener? = null
    private var onCoursePlayListener: OnCoursePlayListener? = null
    private var onCourseClickListener: OnCourseClickListener? = null
    private var onCollectionClickListener: OnCollectionClickListener? = null
    private var onCollectionSelectListener: OnCollectionSelectListener? = null
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

    fun setOnCollectionClickListener(listener: OnCollectionClickListener?) {
        this.onCollectionClickListener = listener
    }

    fun setOnCollectionSelectListener(listener: OnCollectionSelectListener?) {
        this.onCollectionSelectListener = listener
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

    /**
     * 同时更新课程和合集（合集显示在课程列表前部）
     */
    fun updateData(courses: List<Course>, collections: List<CourseCollection>) {
        courseList = courses
        collectionList = collections
        expandedDescriptions.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = courseList.size + collectionList.size

    override fun getItemViewType(position: Int): Int {
        return if (position < collectionList.size) TYPE_COLLECTION else TYPE_COURSE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return if (viewType == TYPE_COLLECTION) CollectionViewHolder(view) else CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is CollectionViewHolder) {
            val collection = collectionList[position]
            holder.bindCollection(collection)
            // 整卡点击 → 打开合集
            holder.itemView.setOnClickListener {
                onCollectionClickListener?.onCollectionClick(collection, position)
            }
            // 右侧按钮：选课模式=选课/退选，播放模式=打开
            holder.selectButton.setOnClickListener {
                when (buttonMode) {
                    ButtonMode.SELECT -> onCollectionSelectListener?.onCollectionSelected(collection, position)
                    ButtonMode.PLAY -> onCollectionClickListener?.onCollectionClick(collection, position)
                }
            }
            return
        }

        val coursePosition = position - collectionList.size
        val course = courseList[coursePosition]
        val courseHolder = holder as CourseViewHolder
        courseHolder.bind(course, coursePosition)

        // 设置整张卡片点击事件（学生查看资料）
        courseHolder.itemView.setOnClickListener {
            onCourseClickListener?.onCourseClick(course, coursePosition)
        }

        // 设置按钮点击事件
        courseHolder.selectButton.setOnClickListener {
            when (buttonMode) {
                ButtonMode.PLAY -> {
                    // 播放模式：触发播放监听器
                    onCoursePlayListener?.onCoursePlay(course, coursePosition)
                    // 显示播放提示
                    Toast.makeText(courseHolder.itemView.context, "播放课程: ${course.name}", Toast.LENGTH_SHORT).show()
                }
                ButtonMode.SELECT -> {
                    // 选课模式：触发选课监听器
                    onCourseSelectListener?.onCourseSelected(course, coursePosition)
                    // 显示选课提示（根据当前状态）
                    val action = if (course.isSelected) "退选" else "选课"
                    Toast.makeText(courseHolder.itemView.context, "${action}课程: ${course.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 合集条目：复用课程卡片样式，展示为"合集"外观
     */
    inner class CollectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

        fun bindCollection(collection: CourseCollection) {
            // 封面
            val coverUrl = collection.coverUrl
            if (!coverUrl.isNullOrEmpty()) {
                val fullUrl = if (coverUrl.startsWith("http")) coverUrl
                    else "${ApiService.BASE_URL}$coverUrl"
                courseCoverImage.visibility = View.VISIBLE
                courseIconTextView.visibility = View.GONE
                ImageLoaderUtil.load(courseCoverImage, fullUrl, crossfade = true)
            } else {
                courseCoverImage.visibility = View.GONE
                courseIconTextView.visibility = View.VISIBLE
                courseIconTextView.text = "📁"
            }

            courseNameTextView.text = collection.name
            teacherTextView.text = "合集 · ${collection.courseCount} 门课程"
            descriptionTextView.maxLines = 2
            descriptionTextView.text = collection.description ?: "点开合集，查看并选择其中的课程"

            // 分类或"合集"标签
            val categoryText = if (!collection.categoryName.isNullOrBlank()) "分类：${collection.categoryName}" else "合集"
            creditTextView.text = categoryText

            // 状态与按钮（与课程一致：选课模式显示选课/退选，播放模式显示打开）
            when (buttonMode) {
                ButtonMode.SELECT -> {
                    if (collection.isEnrolled) {
                        statusTextView.text = "已选课"
                        statusTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.tsinghua_purple_dark))
                        selectButton.text = "退选"
                    } else {
                        statusTextView.text = "未选课"
                        statusTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.darker_gray))
                        selectButton.text = "选课"
                    }
                }
                ButtonMode.PLAY -> {
                    statusTextView.text = "合集"
                    statusTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.tsinghua_purple_dark))
                    selectButton.text = "打开"
                }
            }
            selectButton.setBackgroundResource(R.drawable.ic_button_background)

            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
        }
    }

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
