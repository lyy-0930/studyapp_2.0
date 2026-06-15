package com.studyapp.manager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日学习记录管理器
 * 按日期存储学生的学习时间记录，支持本地持久化
 */
class StudyRecordManager(context: Context) {

    private val sharedPref: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取指定日期的学习记录
     * @param date 日期字符串 (yyyy-MM-dd)
     */
    fun getRecordsForDate(date: String): List<StudyRecord> {
        val json = sharedPref.getString(getDateKey(date), null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<StudyRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(StudyRecord(
                    courseId = obj.optInt("courseId", 0),
                    courseName = obj.optString("courseName", ""),
                    minutes = obj.optInt("minutes", 0)
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 添加学习记录
     * @param date 日期字符串 (yyyy-MM-dd)
     * @param courseId 课程ID
     * @param courseName 课程名称
     * @param minutes 学习分钟数
     */
    fun addRecord(date: String, courseId: Int, courseName: String, minutes: Int) {
        val records = getRecordsForDate(date).toMutableList()
        // 检查是否已有同一课程的记录，有则累加
        val existingIndex = records.indexOfFirst { it.courseId == courseId }
        if (existingIndex >= 0) {
            val existing = records[existingIndex]
            records[existingIndex] = existing.copy(minutes = existing.minutes + minutes)
        } else {
            records.add(StudyRecord(courseId = courseId, courseName = courseName, minutes = minutes))
        }
        saveRecords(date, records)
    }

    /**
     * 删除指定日期的某课程记录
     */
    fun removeRecord(date: String, courseId: Int) {
        val records = getRecordsForDate(date).toMutableList()
        records.removeAll { it.courseId == courseId }
        saveRecords(date, records)
    }

    /**
     * 获取指定日期的总学习分钟数
     */
    fun getTotalMinutesForDate(date: String): Int {
        return getRecordsForDate(date).sumOf { it.minutes }
    }

    /**
     * 获取今日日期字符串
     */
    fun getTodayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * 获取指定日期的学习课程数
     */
    fun getCourseCountForDate(date: String): Int {
        return getRecordsForDate(date).size
    }

    /**
     * 格式化显示分钟数
     */
    fun formatMinutes(minutes: Int): String {
        return if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            "${hours}小时${mins}分钟"
        } else {
            "${minutes}分钟"
        }
    }

    private fun saveRecords(date: String, records: List<StudyRecord>) {
        val arr = JSONArray()
        for (r in records) {
            arr.put(JSONObject().apply {
                put("courseId", r.courseId)
                put("courseName", r.courseName)
                put("minutes", r.minutes)
            })
        }
        sharedPref.edit().putString(getDateKey(date), arr.toString()).apply()
    }

    private fun getDateKey(date: String): String {
        return "${KEY_PREFIX}$date"
    }

    companion object {
        private const val PREFS_NAME = "study_record_prefs"
        private const val KEY_PREFIX = "records_"
    }

    /**
     * 学习记录数据类
     */
    data class StudyRecord(
        val courseId: Int,
        val courseName: String,
        val minutes: Int
    )
}
