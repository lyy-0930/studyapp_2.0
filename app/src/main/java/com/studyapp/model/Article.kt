package com.studyapp.model

/**
 * 志愿推文正文块（公众号图文结构化块）
 * - text 块：普通段落文字
 * - image 块：正文插图（url 为服务器相对路径，拼 BASE_URL 后加载；
 *   width 为占正文列宽的 0.2~1 比例；x 为独占一行内的水平位置，
 *   0~1 表示图右留白/空闲区的比例（0 贴左、0.5 居中、1 贴右，Word 式自由摆放）
 *   —— 旧数据的 align(left/center/right) 兼容读入推导；全缺省回退满宽居中）
 */
data class ArticleBlock(
    val type: String = "text",       // "text" | "image"
    val text: String = "",           // text 块内容
    val url: String? = null,         // image 块相对地址 /uploads/articles/xxx
    val width: Float? = null,        // image 排版：宽度比例 0.2~1
    val x: Float? = null,            // image 排版：水平位置 0~1（空闲区比例）
    val align: String? = null        // image 旧排版字段：left | center | right（兼容读入）
) {
    val isText: Boolean get() = type == "text"
    val isImage: Boolean get() = type == "image"

    /** 排版用宽度比例（缺省满宽） */
    val widthRatio: Float get() = (width ?: 1f).coerceIn(0.2f, 1f)

    /** 排版用水平位置 0~1（新字段 x 优先；旧数据据 align 推导；缺省居中） */
    val xRatio: Float get() {
        if (x != null) return x!!.coerceIn(0f, 1f)
        return when (align) {
            "left" -> 0f
            "right" -> 1f
            else -> 0.5f
        }
    }
}

/**
 * 志愿推文数据模型（对应后端 articles 表）
 * 教师/管理员发布，学生只读；展示在学生端/教师端首页轮播下方
 */
data class Article(
    val id: Int = 0,
    val title: String = "",
    val contentBlocks: List<ArticleBlock> = emptyList(), // 详情页正文块（图文混排）
    val excerpt: String = "",      // 列表摘要（后端取文本块前 90 字）
    val coverUrl: String? = null,  // 封面相对路径 /uploads/articles/xxx
    val authorId: Int = 0,
    val authorName: String = "",
    val authorRole: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    /** 详情页纯文本标题下的正文是否为空 */
    val hasNoText: Boolean
        get() = contentBlocks.none { it.isText && it.text.isNotBlank() }

    companion object {
        /**
         * 把后端返回的时间转成本地展示文本，如 2026-09-06 17:18
         * 兼容 "2026-09-06T09:18:29.000Z" 或 "2026-09-06 09:18:29" 两种形态
         */
        fun displayTime(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            return try {
                val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val dt = inFmt.parse(iso)
                if (dt != null) {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(dt)
                } else {
                    iso.take(16).replace('T', ' ')
                }
            } catch (e: Exception) {
                iso.take(16).replace('T', ' ')
            }
        }
    }
}
