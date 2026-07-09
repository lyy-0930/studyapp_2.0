package com.studyapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.manager.ApiService
import com.studyapp.model.ApiCourse
import com.studyapp.util.ImageLoaderUtil

class TeacherCourseAdapter(
    private var courseList: List<ApiCourse> = listOf()
) : RecyclerView.Adapter<TeacherCourseAdapter.ViewHolder>() {

    interface OnCourseActionListener {
        fun onPlayVideo(course: ApiCourse, position: Int)
        fun onManageQuestions(course: ApiCourse, position: Int)
        fun onUploadMaterial(course: ApiCourse, position: Int)
        fun onDelete(course: ApiCourse, position: Int)
    }

    private var listener: OnCourseActionListener? = null

    fun setOnCourseActionListener(listener: OnCourseActionListener?) {
        this.listener = listener
    }

    fun updateData(newList: List<ApiCourse>) {
        courseList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_teacher_course, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val course = courseList[position]
        holder.bind(course, position)
    }

    override fun getItemCount(): Int = courseList.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val courseName: TextView = itemView.findViewById(R.id.teacherCourseName)
        private val courseDesc: TextView = itemView.findViewById(R.id.teacherCourseDesc)
        private val courseTeacher: TextView = itemView.findViewById(R.id.teacherCourseTeacher)
        private val courseDate: TextView = itemView.findViewById(R.id.teacherCourseDate)
        private val coverImage: ImageView = itemView.findViewById(R.id.teacherCourseCoverImage)
        private val iconText: TextView = itemView.findViewById(R.id.teacherCourseIconText)
        private val playBtn: View = itemView.findViewById(R.id.playVideoBtn)
        private val manageBtn: View = itemView.findViewById(R.id.manageQuestionsBtn)
        private val uploadBtn: View = itemView.findViewById(R.id.uploadMaterialBtn)
        private val deleteBtn: View = itemView.findViewById(R.id.deleteCourseBtn)

        fun bind(course: ApiCourse, position: Int) {
            courseName.text = course.name
            courseDesc.text = course.description
            courseTeacher.text = "教师：${course.teacherName ?: course.teacher}"
            courseDate.text = "创建时间：${course.createdAt?.take(10) ?: "未知"}"

            // 加载课程封面图
            val imageUrl = course.imageUrl
            if (!imageUrl.isNullOrEmpty()) {
                val fullUrl = if (imageUrl.startsWith("http")) imageUrl
                    else "${ApiService.BASE_URL}$imageUrl"
                coverImage.visibility = View.VISIBLE
                iconText.visibility = View.GONE
                ImageLoaderUtil.load(coverImage, fullUrl, crossfade = true)
            } else {
                coverImage.visibility = View.GONE
                iconText.visibility = View.VISIBLE
            }

            playBtn.setOnClickListener { listener?.onPlayVideo(course, position) }
            manageBtn.setOnClickListener { listener?.onManageQuestions(course, position) }
            uploadBtn.setOnClickListener { listener?.onUploadMaterial(course, position) }
            deleteBtn.setOnClickListener { listener?.onDelete(course, position) }
        }
    }
}
