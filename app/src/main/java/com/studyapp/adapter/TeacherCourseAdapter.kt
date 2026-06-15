package com.studyapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.model.ApiCourse

class TeacherCourseAdapter(
    private var courseList: List<ApiCourse> = listOf()
) : RecyclerView.Adapter<TeacherCourseAdapter.ViewHolder>() {

    interface OnCourseActionListener {
        fun onPreview(course: ApiCourse, position: Int)
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
        private val courseStudents: TextView = itemView.findViewById(R.id.teacherCourseStudents)
        private val courseDate: TextView = itemView.findViewById(R.id.teacherCourseDate)
        private val previewBtn: Button = itemView.findViewById(R.id.previewCourseBtn)
        private val uploadBtn: Button = itemView.findViewById(R.id.uploadMaterialBtn)
        private val deleteBtn: Button = itemView.findViewById(R.id.deleteCourseBtn)

        fun bind(course: ApiCourse, position: Int) {
            courseName.text = course.name
            courseDesc.text = course.description
            courseTeacher.text = "教师：${course.teacherName ?: course.teacher}"
            courseDate.text = "创建时间：${course.createdAt?.take(10) ?: "未知"}"

            val teacherName = course.teacherName ?: course.teacher
            courseStudents.text = "授课教师：$teacherName"

            previewBtn.setOnClickListener { listener?.onPreview(course, position) }
            uploadBtn.setOnClickListener { listener?.onUploadMaterial(course, position) }
            deleteBtn.setOnClickListener { listener?.onDelete(course, position) }
        }
    }
}
