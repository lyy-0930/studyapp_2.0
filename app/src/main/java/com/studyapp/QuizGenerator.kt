package com.studyapp

import com.studyapp.model.Question
import kotlin.math.min

/**
 * 基于规则的自动选择题生成器
 * 不使用任何 AI 模型，纯规则实现
 */
object QuizGenerator {

    // 中文停用词
    private val stopWords = setOf(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
        "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
        "自己", "这", "他", "她", "它", "们", "那", "些", "为", "所以", "因为", "但是",
        "可以", "这个", "那个", "什么", "怎么", "如何", "我们", "他们", "她们", "它们",
        "或者", "还是", "如果", "虽然", "然而", "而且", "以及", "等等", "等", "之", "与",
        "及", "但", "并", "而", "或", "不是", "就是", "只是", "但是", "却是", "也是",
        "中的", "之一", "一种", "通过", "进行", "使用", "用于", "包括", "以及", "其中",
        "可以", "能够", "需要", "具有", "拥有", "成为", "作为", "关于", "对于", "按照",
        "根据", "经过", "从", "对", "被", "把", "向", "以", "到", "在", "于", "比", "按"
    )

    // 中文标点符号
    private val sentenceDelimiter = Regex("[。！？；.!?;\\n]+")

    // 词分隔符
    private val wordDelimiter = Regex("[，,、；;：: \\s()（）「」【】《》\"'\\[\\]{}]+")

    /**
     * 从幻灯片文本生成选择题
     * @param slideTexts 每张幻灯片的文本列表
     * @param maxQuestions 最多生成多少题（默认10）
     * @return 生成的选择题列表
     */
    fun generate(
        slideTexts: List<String>,
        maxQuestions: Int = 10
    ): List<Question> {
        if (slideTexts.isEmpty()) return emptyList()

        // 1. 合并所有文本
        val allText = slideTexts.joinToString("\n")
        if (allText.isBlank()) return emptyList()

        // 2. 提取所有关键词
        val allKeywords = extractKeywords(allText)
        if (allKeywords.isEmpty()) return emptyList()

        // 3. 按长度排序（关键词越长越有信息量，优先使用）
        val sortedKeywords = allKeywords.sortedByDescending { it.length }

        // 4. 拆分句子
        val sentences = splitSentences(allText)
            .filter { it.length >= 10 }
            .distinct()

        if (sentences.isEmpty()) return emptyList()

        // 5. 生成题目
        val questions = mutableListOf<Question>()
        val usedKeywords = mutableSetOf<String>()
        val usedQuestionTexts = mutableSetOf<String>()

        for (sentence in sentences) {
            if (questions.size >= maxQuestions) break

            // 找到该句子中尚未使用的关键词（按长度优先）
            val keyword = sortedKeywords.firstOrNull { kw ->
                kw !in usedKeywords && sentence.contains(kw)
            } ?: continue

            // 将关键词替换为____
            val questionText = sentence.replaceFirst(keyword, "____")
            if (questionText.length < 10) continue
            if (questionText in usedQuestionTexts) continue
            if (questionText.count { it == '_' } < 4) continue // 确保有下划线占位

            // 从其他关键词中选择干扰项
            val distractors = allKeywords
                .filter { it != keyword }
                .distinct()
                .shuffled()
                .take(3)

            if (distractors.size < 2) continue // 至少需要2个干扰项（总共3-4个选项）

            // 补充分散词作为干扰项
            val extraDistractors = if (distractors.size < 3) {
                extractKeywords(allText, minLength = 1)
                    .filter { it != keyword && it !in distractors }
                    .shuffled()
                    .take(3 - distractors.size)
            } else emptyList()

            val allDistractors = (distractors + extraDistractors).take(3)

            // 合并正确选项和干扰项，打乱顺序
            val allOptions = (allDistractors + keyword).shuffled()

            questions.add(
                Question(
                    questionText = questionText,
                    options = allOptions,
                    correctAnswer = keyword
                )
            )

            usedKeywords.add(keyword)
            usedQuestionTexts.add(questionText)
        }

        return questions.take(maxQuestions)
    }

    /**
     * 将文本拆分为句子
     */
    private fun splitSentences(text: String): List<String> {
        return text.split(sentenceDelimiter)
            .map { it.trim() }
            .filter { it.length >= 8 }
    }

    /**
     * 从文本中提取关键词
     * @param text 文本
     * @param minLength 关键词最短长度（默认2）
     * @param maxLength 关键词最长长度（默认20）
     */
    private fun extractKeywords(
        text: String,
        minLength: Int = 2,
        maxLength: Int = 20
    ): Set<String> {
        // 按分隔符切分
        val candidates = text.split(wordDelimiter)
            .map { it.trim() }
            .filter { it.length in minLength..maxLength }
            .filter { it !in stopWords }
            .filterNot { it.any { c -> c in "0123456789" && c.isDigit() } }
            .filter { token ->
                // 至少包含一个中文字符或英文字母
                token.any { it.isLetter() }
            }
            .filterNot { token ->
                // 过滤纯数字和符号
                token.all { !it.isLetter() }
            }

        // 按出现频率排序，取高频词
        val freqMap = mutableMapOf<String, Int>()
        for (token in candidates) {
            freqMap[token] = (freqMap[token] ?: 0) + 1
        }

        // 额外提取长短语（连续的2-3个词组成的短语）
        val bigrams = extractBigrams(text)

        return candidates.toSet() + bigrams
    }

    /**
     * 提取连续的双词短语（增加高质量关键词候选）
     */
    private fun extractBigrams(text: String): Set<String> {
        val tokens = text.split(wordDelimiter)
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
            .filter { it.any { c -> c.isLetter() } }

        val bigrams = mutableSetOf<String>()
        for (i in 0 until tokens.size - 1) {
            val bigram = tokens[i] + tokens[i + 1]
            if (bigram.length in 3..20 && bigram.any { it.isLetter() }) {
                bigrams.add(bigram)
            }
        }
        return bigrams
    }
}
