package com.studyapp

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * PPTX 文件解析器
 * .pptx 本质上是 ZIP 压缩包，幻灯片内容在 ppt/slides/slideN.xml 中
 * 文本内容在 <a:t> 标签内
 */
object PPTXParser {

    /**
     * 解析 PPTX 文件，提取所有幻灯片文本
     * @param inputStream PPTX 文件的输入流
     * @return 每张幻灯片的文本列表
     */
    fun parse(inputStream: InputStream): List<String> {
        val slides = mutableListOf<String>()

        try {
            val zip = ZipInputStream(inputStream)

            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    // 安全地读取当前 ZIP 条目的全部内容
                    val xmlBytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (zip.read(buffer).also { len = it } != -1) {
                        xmlBytes.write(buffer, 0, len)
                    }
                    val xml = xmlBytes.toString(Charsets.UTF_8.name())
                    val text = extractTextFromSlideXml(xml)
                    if (text.isNotBlank()) {
                        slides.add(text.trim())
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()

        } catch (e: Exception) {
            throw PPTXParseException("PPT解析失败: ${e.message}", e)
        }

        return slides
    }

    /**
     * 从 slide XML 中提取所有文本标签内的内容
     * 支持多种命名空间格式：<a:t>、<a:t xml:space="preserve">
     */
    private fun extractTextFromSlideXml(xml: String): String {
        val result = StringBuilder()

        // 按 <a:t 或 <a:r 或 <p: 提取文本
        var searchFrom = 0
        while (true) {
            // 查找 <a:t 或 <a:t xml:space
            val tagStart = xml.indexOf("<a:t", searchFrom)
            if (tagStart == -1) break

            val contentStart = xml.indexOf('>', tagStart)
            if (contentStart == -1) break

            val contentEnd = xml.indexOf("</a:t>", contentStart + 1)
            if (contentEnd == -1) break

            val content = xml.substring(contentStart + 1, contentEnd)
            if (content.isNotBlank()) {
                // 添加空格分隔不同文本块
                if (result.isNotEmpty() && !result.endsWith(" ")) result.append(' ')
                result.append(content.trim())
            }
            searchFrom = contentEnd + 6
        }

        return result.toString()
    }
}

class PPTXParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
