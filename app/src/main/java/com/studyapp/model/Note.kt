package com.studyapp.model

/**
 * 视频笔记（Note）数据模型
 * 用户在播放课程视频时做的笔记，绑定播放时间点（锚点）
 * 对应后端 notes 表；courseName / videoUrl 为课程快照，用于聚合列表与跳回播放
 */
data class Note(
    val id: Int = 0,
    val userId: Int = 0,
    val userName: String = "",
    val avatarUrl: String? = null,
    val courseId: Int = 0,
    val courseName: String = "",
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val timestampSeconds: Int = 0,
    val content: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null
)
