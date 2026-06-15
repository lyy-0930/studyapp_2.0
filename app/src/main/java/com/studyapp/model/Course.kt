package com.studyapp.model

/**
 * 课程数据类
 * @param id 课程ID
 * @param name 课程名称
 * @param description 课程描述
 * @param teacher 授课教师
 * @param credit 学分
 * @param videoUrl 视频URL（可选，用于播放视频）
 * @param isSelected 是否已选课
 */
data class Course(
    val id: Int,
    val name: String,
    val description: String,
    val teacher: String,
    val credit: Int,
    val videoUrl: String? = null,
    var isSelected: Boolean = false,
    val imageUrl: String? = null,
    val categoryId: Int? = null,
    val categoryName: String? = null,
    val createdAt: String? = null,
    val progress: Int = 0
)