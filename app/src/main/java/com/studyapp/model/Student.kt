package com.studyapp.model

/**
 * 优秀学生数据模型
 * @param name 学生姓名
 * @param progress 学习进度（百分比）
 */
data class Student(
    val name: String,
    val progress: Int
) {
    /**
     * 获取格式化的进度文本
     */
    fun getFormattedProgress(): String = "$progress%"
}