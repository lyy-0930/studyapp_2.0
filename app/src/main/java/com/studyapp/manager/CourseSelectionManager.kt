package com.studyapp.manager

import android.content.Context
import android.content.SharedPreferences
import com.studyapp.model.Course

/**
 * 课程选择管理器
 * 负责管理学生的选课状态，使用SharedPreferences进行持久化存储
 * 支持按用户隔离选课数据
 *
 * 注意：这里使用简单的方法存储课程ID列表，而不是完整的课程对象
 * 实际课程信息可以从固定的课程列表中获取
 */
class CourseSelectionManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "course_selection_prefs"
        private const val KEY_SELECTED_COURSE_IDS = "selected_course_ids_"
        private const val SEPARATOR = ","

        /**
         * 获取指定用户的课程选择管理器实例
         */
        fun forUser(context: Context, username: String): CourseSelectionManager {
            return CourseSelectionManager(context).apply {
                this.username = username
            }
        }
    }

    private lateinit var username: String
    private val sharedPref: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取当前用户的已选课程ID列表
     */
    fun getSelectedCourseIds(): Set<Int> {
        if (!::username.isInitialized) {
            return emptySet()
        }

        val idsString = sharedPref.getString(getKey(), null) ?: return emptySet()
        return if (idsString.isNotEmpty()) {
            idsString.split(SEPARATOR).mapNotNull { it.toIntOrNull() }.toSet()
        } else {
            emptySet()
        }
    }

    /**
     * 添加课程ID到已选列表
     * @param courseId 要添加的课程ID
     * @return true表示添加成功，false表示已存在
     */
    fun addCourseId(courseId: Int): Boolean {
        if (!::username.isInitialized) {
            return false
        }

        val currentIds = getSelectedCourseIds().toMutableSet()
        if (currentIds.contains(courseId)) {
            return false // 课程已存在
        }

        currentIds.add(courseId)
        return saveCourseIds(currentIds)
    }

    /**
     * 从已选列表中移除课程ID
     * @param courseId 要移除的课程ID
     * @return true表示移除成功，false表示不存在
     */
    fun removeCourseId(courseId: Int): Boolean {
        if (!::username.isInitialized) {
            return false
        }

        val currentIds = getSelectedCourseIds().toMutableSet()
        if (currentIds.remove(courseId)) {
            return saveCourseIds(currentIds)
        }
        return false
    }

    /**
     * 检查课程是否已被选择
     * @param courseId 课程ID
     * @return true表示已选择
     */
    fun isCourseSelected(courseId: Int): Boolean {
        if (!::username.isInitialized) {
            return false
        }

        return getSelectedCourseIds().contains(courseId)
    }

    /**
     * 清空当前用户的所有已选课程
     */
    fun clearAllCourses(): Boolean {
        if (!::username.isInitialized) {
            return false
        }

        return sharedPref.edit().remove(getKey()).commit()
    }

    /**
     * 获取已选课程数量
     */
    fun getSelectedCourseCount(): Int {
        return getSelectedCourseIds().size
    }

    /**
     * 更新课程选择状态
     * @param courseId 课程ID
     * @param isSelected 是否选择
     */
    fun updateCourseSelection(courseId: Int, isSelected: Boolean) {
        if (!::username.isInitialized) {
            return
        }

        if (isSelected) {
            addCourseId(courseId)
        } else {
            removeCourseId(courseId)
        }
    }

    /**
     * 获取用户的选课状态键
     */
    private fun getKey(): String {
        return KEY_SELECTED_COURSE_IDS + username
    }

    /**
     * 保存课程ID列表到SharedPreferences
     */
    private fun saveCourseIds(courseIds: Set<Int>): Boolean {
        return try {
            val idsString = courseIds.joinToString(SEPARATOR)
            sharedPref.edit().putString(getKey(), idsString).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}