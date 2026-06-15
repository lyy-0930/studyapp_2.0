package com.studyapp.manager

import android.content.Context
import android.content.SharedPreferences
import com.studyapp.model.Course

/**
 * 课程管理器
 * 管理所有课程列表，使用SharedPreferences进行持久化存储
 * 课程由教师上传视频时创建
 */
object CourseManager {

    private const val PREFS_NAME = "course_manager_prefs"
    private const val KEY_COURSE_DATA = "course_data" // 存储课程数据的键
    private const val FIELD_SEPARATOR = "|||" // 字段分隔符
    private const val ITEM_SEPARATOR = "###"  // 项目分隔符

    /**
     * 获取SharedPreferences实例
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存课程
     * @param context Context
     * @param course 课程对象
     */
    fun saveCourse(context: Context, course: Course) {
        val sharedPref = getSharedPreferences(context)
        val existingCourses = getCourses(context).toMutableList()

        // 如果课程ID已存在，更新该课程
        val existingIndex = existingCourses.indexOfFirst { it.id == course.id }
        if (existingIndex != -1) {
            existingCourses[existingIndex] = course
        } else {
            // 添加到列表
            existingCourses.add(course)
        }

        // 保存更新后的课程列表
        saveCourses(context, existingCourses)
    }

    /**
     * 获取所有课程
     * @return Course列表
     */
    fun getCourses(context: Context): List<Course> {
        val sharedPref = getSharedPreferences(context)
        val dataString = sharedPref.getString(KEY_COURSE_DATA, "")

        return if (dataString.isNullOrEmpty()) {
            emptyList()
        } else {
            parseCourses(dataString)
        }
    }

    /**
     * 根据ID获取课程
     */
    fun getCourseById(context: Context, courseId: Int): Course? {
        return getCourses(context).find { it.id == courseId }
    }

    /**
     * 删除课程
     */
    fun deleteCourse(context: Context, courseId: Int) {
        val sharedPref = getSharedPreferences(context)
        val existingCourses = getCourses(context).toMutableList()

        // 移除指定ID的课程
        existingCourses.removeAll { it.id == courseId }

        // 保存更新后的列表
        saveCourses(context, existingCourses)
    }

    /**
     * 清空所有课程
     */
    fun clearAllCourses(context: Context) {
        val sharedPref = getSharedPreferences(context)
        sharedPref.edit()
            .remove(KEY_COURSE_DATA)
            .commit()
    }

    /**
     * 获取课程数量
     */
    fun getCourseCount(context: Context): Int {
        return getCourses(context).size
    }

    /**
     * 将课程列表保存到SharedPreferences
     */
    private fun saveCourses(context: Context, courses: List<Course>) {
        val sharedPref = getSharedPreferences(context)
        val dataString = serializeCourses(courses)
        sharedPref.edit()
            .putString(KEY_COURSE_DATA, dataString)
            .apply()
    }

    /**
     * 序列化课程列表为字符串
     */
    private fun serializeCourses(courses: List<Course>): String {
        return courses.joinToString(ITEM_SEPARATOR) { course ->
            listOf(
                course.id.toString(),
                course.name,
                course.description,
                course.teacher,
                course.credit.toString(),
                course.videoUrl ?: "",
                course.isSelected.toString(),
                course.imageUrl ?: "",
                course.categoryId?.toString() ?: "",
                course.categoryName ?: ""
            ).joinToString(FIELD_SEPARATOR)
        }
    }

    /**
     * 解析字符串为课程列表
     * 兼容旧数据（6个字段）、旧数据（7个字段）和新数据（8个字段）
     */
    private fun parseCourses(dataString: String): List<Course> {
        return dataString.split(ITEM_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { itemString ->
                try {
                    val fields = itemString.split(FIELD_SEPARATOR)
                    when (fields.size) {
                        6 -> {
                            // 旧数据格式：没有videoUrl字段
                            Course(
                                id = fields[0].toIntOrNull() ?: 0,
                                name = fields[1],
                                description = fields[2],
                                teacher = fields[3],
                                credit = fields[4].toIntOrNull() ?: 2,
                                videoUrl = null,
                                isSelected = fields[5].toBoolean()
                            )
                        }
                        7 -> {
                            // 旧数据格式：有videoUrl但没有imageUrl
                            Course(
                                id = fields[0].toIntOrNull() ?: 0,
                                name = fields[1],
                                description = fields[2],
                                teacher = fields[3],
                                credit = fields[4].toIntOrNull() ?: 2,
                                videoUrl = if (fields[5].isNotEmpty()) fields[5] else null,
                                isSelected = fields[6].toBoolean()
                            )
                        }
                        in 8..Int.MAX_VALUE -> {
                            // 新数据格式：包含videoUrl, imageUrl, categoryId, categoryName
                            Course(
                                id = fields[0].toIntOrNull() ?: 0,
                                name = fields[1],
                                description = fields[2],
                                teacher = fields[3],
                                credit = fields[4].toIntOrNull() ?: 2,
                                videoUrl = if (fields[5].isNotEmpty()) fields[5] else null,
                                isSelected = fields[6].toBoolean(),
                                imageUrl = if (fields.size > 7 && fields[7].isNotEmpty()) fields[7] else null,
                                categoryId = if (fields.size > 8 && fields[8].isNotEmpty()) fields[8].toIntOrNull() else null,
                                categoryName = if (fields.size > 9 && fields[9].isNotEmpty()) fields[9] else null
                            )
                        }
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * 从VideoItem创建课程
     * @param videoItem 视频项
     * @param credit 学分（默认2）
     */
    fun createCourseFromVideoItem(videoItem: com.studyapp.model.VideoItem, credit: Int = 2): Course {
        // 使用视频ID的哈希值作为课程ID，确保为正数
        val courseId = Math.abs(videoItem.id.hashCode())

        return Course(
            id = courseId,
            name = videoItem.title,
            description = videoItem.description,
            teacher = videoItem.teacher,
            credit = credit,
            videoUrl = videoItem.url, // 设置视频URL
            isSelected = false
        )
    }
}