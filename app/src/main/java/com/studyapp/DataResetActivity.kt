package com.studyapp

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.studyapp.manager.CourseManager
import com.studyapp.manager.VideoStorageManager
import com.studyapp.manager.CourseSelectionManager
import android.util.Log

/**
 * 数据重置界面
 * 用于清除所有本地存储数据，将应用恢复到初始状态
 * 警告：此操作不可逆，会删除所有用户数据
 */
class DataResetActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DataResetActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_reset)

        val titleTextView = findViewById<TextView>(R.id.titleTextView)
        val descriptionTextView = findViewById<TextView>(R.id.descriptionTextView)
        val resetButton = findViewById<Button>(R.id.resetButton)
        val backButton = findViewById<Button>(R.id.backButton)
        val statusTextView = findViewById<TextView>(R.id.statusTextView)

        // 更新状态显示
        updateStatus(statusTextView)

        // 返回按钮
        backButton.setOnClickListener {
            finish()
        }

        // 重置按钮
        resetButton.setOnClickListener {
            showResetConfirmationDialog(statusTextView)
        }
    }

    /**
     * 显示重置确认对话框
     */
    private fun showResetConfirmationDialog(statusTextView: TextView) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ 数据重置警告")
            .setMessage("此操作将删除所有本地数据，包括：\n\n" +
                    "• 所有登录用户信息\n" +
                    "• 所有课程数据\n" +
                    "• 所有视频记录\n" +
                    "• 所有选课记录\n" +
                    "• 所有学习进度\n\n" +
                    "此操作不可恢复！\n\n" +
                    "确定要继续吗？")
            .setPositiveButton("确定重置") { dialog, which ->
                performDataReset(statusTextView)
            }
            .setNegativeButton("取消") { dialog, which ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 执行数据重置
     */
    private fun performDataReset(statusTextView: TextView) {
        try {
            Log.d(TAG, "开始执行数据重置...")

            // 1. 清除所有SharedPreferences
            clearAllSharedPreferences()

            // 2. 清除课程管理器数据
            CourseManager.clearAllCourses(this)
            Log.d(TAG, "课程数据已清除")

            // 3. 清除视频存储数据
            VideoStorageManager.clearAllVideos(this)
            Log.d(TAG, "视频数据已清除")

            // 4. 清除所有用户的选课记录（需要获取所有可能的用户）
            clearAllUserCourseSelections()

            // 5. 清除其他可能的存储
            clearOtherStorage()

            // 更新状态显示
            updateStatus(statusTextView)

            Toast.makeText(this, "✅ 数据重置完成！应用已恢复到初始状态", Toast.LENGTH_LONG).show()
            Log.d(TAG, "数据重置完成")

        } catch (e: Exception) {
            Log.e(TAG, "数据重置失败: ${e.message}", e)
            Toast.makeText(this, "❌ 数据重置失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 清除所有SharedPreferences文件
     */
    private fun clearAllSharedPreferences() {
        val sharedPrefs = listOf(
            "login_prefs",                    // 登录信息
            "course_manager_prefs",          // 课程管理器
            "video_storage",                 // 视频存储
            "course_selection_prefs",        // 选课状态
            "teacher_statistics_prefs",      // 教师统计
            "study_progress_prefs"           // 学习进度（如果存在）
        )

        for (prefName in sharedPrefs) {
            try {
                val sharedPref = getSharedPreferences(prefName, Context.MODE_PRIVATE)
                sharedPref.edit().clear().commit()
                Log.d(TAG, "SharedPreferences '$prefName' 已清除")
            } catch (e: Exception) {
                Log.w(TAG, "清除SharedPreferences '$prefName' 失败: ${e.message}")
            }
        }

        // 也尝试清除默认的SharedPreferences
        try {
            val defaultPref = getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
            defaultPref.edit().clear().commit()
            Log.d(TAG, "默认SharedPreferences已清除")
        } catch (e: Exception) {
            Log.w(TAG, "清除默认SharedPreferences失败: ${e.message}")
        }
    }

    /**
     * 清除所有用户的选课记录
     * 注意：这需要知道可能的用户名，这里使用通用方法
     */
    private fun clearAllUserCourseSelections() {
        try {
            // 获取课程选择SharedPreferences
            val selectionPrefs = getSharedPreferences("course_selection_prefs", Context.MODE_PRIVATE)

            // 获取所有键
            val allKeys = selectionPrefs.all.keys

            if (allKeys.isNotEmpty()) {
                Log.d(TAG, "找到 ${allKeys.size} 个选课记录键")

                // 清除所有键
                selectionPrefs.edit().clear().apply()
                Log.d(TAG, "所有选课记录已清除")
            } else {
                Log.d(TAG, "没有找到选课记录")
            }

        } catch (e: Exception) {
            Log.w(TAG, "清除选课记录失败: ${e.message}")
        }
    }

    /**
     * 清除其他可能的存储
     */
    private fun clearOtherStorage() {
        try {
            // 清除内部存储中的应用私有文件
            val files = fileList()
            if (files.isNotEmpty()) {
                for (file in files) {
                    try {
                        deleteFile(file)
                        Log.d(TAG, "删除文件: $file")
                    } catch (e: Exception) {
                        Log.w(TAG, "删除文件失败 '$file': ${e.message}")
                    }
                }
            }

            // 清除缓存
            cacheDir.deleteRecursively()
            Log.d(TAG, "缓存目录已清除")

        } catch (e: Exception) {
            Log.w(TAG, "清除其他存储失败: ${e.message}")
        }
    }

    /**
     * 更新状态显示
     */
    private fun updateStatus(statusTextView: TextView) {
        val courseCount = CourseManager.getCourseCount(this)
        val videoCount = VideoStorageManager.getVideoCount(this)

        val statusText = """
            当前数据状态：

            本地课程数量：$courseCount 门
            本地视频记录：$videoCount 个

            点击"重置所有数据"按钮将清除以上所有数据。

            ⚠️ 警告：此操作不可恢复！
        """.trimIndent()

        statusTextView.text = statusText
    }
}