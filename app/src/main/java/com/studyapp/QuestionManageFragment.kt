package com.studyapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.studyapp.manager.ApiService
import com.studyapp.model.Question
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuestionManageFragment : Fragment() {

    private var courseId: Int = 0
    private var courseName: String = ""
    private var allQuestions: List<Question> = emptyList()
    private var currentFilter: String? = null // null=全部, "draft", "published"
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var apiService: ApiService

    // Views
    private lateinit var titleText: TextView
    private lateinit var backBtn: TextView
    private lateinit var aiGenerateBtn: TextView
    private lateinit var manualAddBtn: TextView
    private lateinit var filterAllBtn: TextView
    private lateinit var filterDraftBtn: TextView
    private lateinit var filterPublishedBtn: TextView
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView
    private lateinit var listContainer: LinearLayout

    companion object {
        private const val ARG_COURSE_ID = "courseId"
        private const val ARG_COURSE_NAME = "courseName"

        fun newInstance(courseId: Int, courseName: String): QuestionManageFragment {
            val args = Bundle().apply {
                putInt(ARG_COURSE_ID, courseId)
                putString(ARG_COURSE_NAME, courseName)
            }
            val fragment = QuestionManageFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            courseId = it.getInt(ARG_COURSE_ID, 0)
            courseName = it.getString(ARG_COURSE_NAME, "") ?: ""
        }
        apiService = ApiService.getInstance(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_question_manage, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        loadQuestions()
    }

    private fun initViews(view: View) {
        titleText = view.findViewById(R.id.questionManageTitle)
        backBtn = view.findViewById(R.id.questionManageBackBtn)
        aiGenerateBtn = view.findViewById(R.id.aiGenerateBtn)
        manualAddBtn = view.findViewById(R.id.manualAddBtn)
        filterAllBtn = view.findViewById(R.id.filterAllBtn)
        filterDraftBtn = view.findViewById(R.id.filterDraftBtn)
        filterPublishedBtn = view.findViewById(R.id.filterPublishedBtn)
        loadingText = view.findViewById(R.id.questionLoadingText)
        emptyText = view.findViewById(R.id.questionEmptyText)
        listContainer = view.findViewById(R.id.questionListContainer)

        titleText.text = "题目管理 - $courseName"

        backBtn.setOnClickListener {
            parentFragmentManager.popBackStack()
            // 尝试通过 Activity 返回上一个面板
            try {
                val activity = requireActivity() as? TeacherActivity
                activity?.showPanel(4) // 返回我的课程
            } catch (_: Exception) { }
        }

        aiGenerateBtn.setOnClickListener {
            showAIGenerateConfirm()
        }

        manualAddBtn.setOnClickListener {
            showEditDialog(null)
        }

        filterAllBtn.setOnClickListener {
            currentFilter = null
            updateFilterStyles()
            applyFilter()
        }
        filterDraftBtn.setOnClickListener {
            currentFilter = "draft"
            updateFilterStyles()
            applyFilter()
        }
        filterPublishedBtn.setOnClickListener {
            currentFilter = "published"
            updateFilterStyles()
            applyFilter()
        }
    }

    private fun updateFilterStyles() {
        fun setStyle(btn: TextView, active: Boolean) {
            if (active) {
                btn.background = ContextCompat.getDrawable(requireContext(), R.drawable.gradient_tsinghua_purple)
                btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            } else {
                btn.background = null
                btn.setBackgroundColor(0xFFE8E8E8.toInt())
                btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.tsinghua_purple_dark))
            }
        }
        setStyle(filterAllBtn, currentFilter == null)
        setStyle(filterDraftBtn, currentFilter == "draft")
        setStyle(filterPublishedBtn, currentFilter == "published")
    }

    private fun loadQuestions() {
        showLoading(true)
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.getQuestions(courseId) // 教师看全部题目
                }
                if (result.isSuccess) {
                    allQuestions = result.getOrThrow()
                    applyFilter()
                } else {
                    showError("加载失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                showError("加载失败: ${e.message}")
            }
            showLoading(false)
        }
    }

    private fun applyFilter() {
        val filtered = if (currentFilter == null) allQuestions
        else allQuestions.filter { it.status == currentFilter }

        listContainer.removeAllViews()
        if (filtered.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            return
        }
        emptyText.visibility = View.GONE

        filtered.forEachIndexed { index, question ->
            val card = createQuestionCard(question, index + 1)
            listContainer.addView(card)
        }
    }

    private fun createQuestionCard(question: Question, index: Int): View {
        val inflater = LayoutInflater.from(requireContext())
        val card = inflater.inflate(R.layout.item_question_card, listContainer, false)

        card.findViewById<TextView>(R.id.questionNumber).text = "第 $index 题"

        val statusBadge = card.findViewById<TextView>(R.id.questionStatusBadge)
        if (question.status == "draft") {
            statusBadge.text = "草稿"
            statusBadge.setBackgroundColor(0xFFFF9800.toInt()) // orange
        } else {
            statusBadge.text = "已发布"
            statusBadge.setBackgroundColor(0xFF4CAF50.toInt()) // green
        }

        val sourceLabel = card.findViewById<TextView>(R.id.questionSourceLabel)
        sourceLabel.text = if (question.source == "ai") "AI" else "手动"

        card.findViewById<TextView>(R.id.questionText).text = question.questionText

        val optionsText = question.options.joinToString("\n")
        card.findViewById<TextView>(R.id.questionOptions).text = optionsText
        card.findViewById<TextView>(R.id.questionCorrectAnswer).text = "正确答案：${question.correctAnswer}"

        val publishBtn = card.findViewById<TextView>(R.id.questionPublishBtn)
        if (question.status == "draft") {
            publishBtn.text = "发布"
            publishBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.tech_green_data))
        } else {
            publishBtn.text = "下架"
            publishBtn.setTextColor(ContextCompat.getColor(requireContext(), R.color.tech_orange_warning))
        }
        publishBtn.setOnClickListener {
            val newStatus = if (question.status == "draft") "published" else "draft"
            toggleQuestionStatus(question, newStatus)
        }

        card.findViewById<TextView>(R.id.questionEditBtn).setOnClickListener {
            showEditDialog(question)
        }

        card.findViewById<TextView>(R.id.questionDeleteBtn).setOnClickListener {
            showDeleteConfirm(question)
        }

        return card
    }

    private fun showAIGenerateConfirm() {
        AlertDialog.Builder(requireContext())
            .setTitle("AI生成题目")
            .setMessage("将调用DeepSeek AI根据PPT内容生成选择题（每次生成10道），生成的题目默认为草稿状态，需审核发布。\n\n是否继续？")
            .setPositiveButton("生成") { _, _ -> doAIGenerate() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doAIGenerate() {
        showLoading(true)
        loadingText.text = "AI正在生成题目，请稍候..."
        loadingText.visibility = View.VISIBLE

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.aiGenerateQuestions(courseId)
                }
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "AI题目生成成功！请在下方审核发布", Toast.LENGTH_LONG).show()
                    loadQuestions()
                } else {
                    Toast.makeText(requireContext(), "生成失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    showLoading(false)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "生成失败: ${e.message}", Toast.LENGTH_LONG).show()
                showLoading(false)
            }
        }
    }

    private fun showEditDialog(question: Question?) {
        val isEdit = question != null
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_question_edit, null)

        val editQuestionText = dialogView.findViewById<TextView>(R.id.editQuestionText) as? android.widget.EditText
        val editOptionA = dialogView.findViewById<TextView>(R.id.editOptionA) as? android.widget.EditText
        val editOptionB = dialogView.findViewById<TextView>(R.id.editOptionB) as? android.widget.EditText
        val editOptionC = dialogView.findViewById<TextView>(R.id.editOptionC) as? android.widget.EditText
        val editOptionD = dialogView.findViewById<TextView>(R.id.editOptionD) as? android.widget.EditText
        val editCorrectAnswer = dialogView.findViewById<TextView>(R.id.editCorrectAnswer) as? android.widget.EditText

        if (isEdit && question != null) {
            editQuestionText?.setText(question.questionText)
            if (question.options.size >= 4) {
                editOptionA?.setText(question.options[0])
                editOptionB?.setText(question.options[1])
                editOptionC?.setText(question.options[2])
                editOptionD?.setText(question.options[3])
            }
            editCorrectAnswer?.setText(question.correctAnswer)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (isEdit) "编辑题目" else "手动添加题目")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val text = editQuestionText?.text?.toString()?.trim() ?: ""
                val optA = editOptionA?.text?.toString()?.trim() ?: ""
                val optB = editOptionB?.text?.toString()?.trim() ?: ""
                val optC = editOptionC?.text?.toString()?.trim() ?: ""
                val optD = editOptionD?.text?.toString()?.trim() ?: ""
                val answer = editCorrectAnswer?.text?.toString()?.trim() ?: ""

                if (text.isEmpty() || optA.isEmpty() || optB.isEmpty() || optC.isEmpty() || optD.isEmpty() || answer.isEmpty()) {
                    Toast.makeText(requireContext(), "请填写完整信息", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val options = listOf(optA, optB, optC, optD)

                if (isEdit && question != null) {
                    val updated = question.copy(
                        questionText = text,
                        options = options,
                        correctAnswer = answer
                    )
                    updateQuestion(updated)
                } else {
                    addManualQuestion(text, options, answer)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateQuestion(question: Question) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.updateQuestion(courseId, question)
                }
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "题目已更新", Toast.LENGTH_SHORT).show()
                    loadQuestions()
                } else {
                    Toast.makeText(requireContext(), "更新失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "更新失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addManualQuestion(text: String, options: List<String>, answer: String) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.addManualQuestion(courseId, text, options, answer)
                }
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "题目已添加", Toast.LENGTH_SHORT).show()
                    loadQuestions()
                } else {
                    Toast.makeText(requireContext(), "添加失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "添加失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleQuestionStatus(question: Question, newStatus: String) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.updateQuestionStatus(courseId, question.id, newStatus)
                }
                if (result.isSuccess) {
                    val msg = if (newStatus == "published") "题目已发布" else "题目已下架"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    loadQuestions()
                } else {
                    Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "操作失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDeleteConfirm(question: Question) {
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除这道题目吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                doDelete(question)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doDelete(question: Question) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiService.deleteQuestion(courseId, question.id)
                }
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "题目已删除", Toast.LENGTH_SHORT).show()
                    loadQuestions()
                } else {
                    Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingText.visibility = if (show) View.VISIBLE else View.GONE
        if (show) emptyText.visibility = View.GONE
    }

    private fun showError(msg: String) {
        emptyText.text = msg
        emptyText.visibility = View.VISIBLE
    }
}
