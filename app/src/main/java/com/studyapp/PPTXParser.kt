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
        val slidesXml = mutableListOf<String>()

        try {
            val zip = ZipInputStream(inputStream)

            // 第一遍：读取所有 slide XML 文件
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    // 注意：不能使用 .use {}，否则会关闭底层的 ZipInputStream
                    val xml = zip.bufferedReader().let { it.readText() }
                    slidesXml.add(xml)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            zip.close()

            // 第二遍：从每个 slide XML 中提取文本
            for (xml in slidesXml) {
                val text = extractTextFromSlideXml(xml)
                if (text.isNotBlank()) {
                    slides.add(text.trim())
                }
            }
        } catch (e: Exception) {
            throw PPTXParseException("PPT解析失败: ${e.message}", e)
        }

        return slides
    }

    /**
     * 从 slide XML 中提取 <a:t> 标签内的文本
     */
    private fun extractTextFromSlideXml(xml: String): String {
        val result = StringBuilder()
        var searchFrom = 0

        while (true) {
            // 匹配 <a:t> 或 <a:t xml:space="preserve">
            val tagStart = xml.indexOf("<a:t", searchFrom)
            if (tagStart == -1) break

            val contentStart = xml.indexOf('>', tagStart) + 1
            if (contentStart == 0) break

            val tagEnd = xml.indexOf("</a:t>", contentStart)
            if (tagEnd == -1) break

            val content = xml.substring(contentStart, tagEnd)
            result.append(content)
            searchFrom = tagEnd + 6
        }

        return result.toString()
    }
}

class PPTXParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
