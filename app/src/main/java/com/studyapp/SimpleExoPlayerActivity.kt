package com.studyapp

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 简单的视频播放Activity（使用 MediaPlayer 替代 ExoPlayer）
 */
class SimpleExoPlayerActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var loadingTextView: TextView
    private lateinit var errorTextView: TextView
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_COURSE_NAME = "extra_course_name"
        private const val TAG = "SimplePlayer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exo_video_player)

        surfaceView = findViewById(R.id.surfaceView)
        loadingTextView = findViewById(R.id.loadingTextView)
        errorTextView = findViewById(R.id.errorTextView)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "课程视频"
        supportActionBar?.title = courseName

        if (videoUrl.isNullOrEmpty()) {
            showErrorMessage("视频地址无效")
            finish(); return
        }

        Log.d(TAG, "播放视频: $videoUrl")
        setupPlayer(videoUrl)
    }

    private fun setupPlayer(videoUrl: String) {
        showLoading()
        try {
            player = MediaPlayer().apply {
                setOnPreparedListener {
                    showPlaying()
                    Toast.makeText(this@SimpleExoPlayerActivity, "开始播放", Toast.LENGTH_SHORT).show()
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "错误: what=$what, extra=$extra")
                    showError()
                    showErrorMessage("播放失败 ($what)")
                    true
                }
                setOnCompletionListener {
                    Toast.makeText(this@SimpleExoPlayerActivity, "播放完成", Toast.LENGTH_SHORT).show()
                }

                val holder = surfaceView.holder
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) {
                        setDisplay(h)
                        try {
                            setDataSource(this@SimpleExoPlayerActivity, Uri.parse(videoUrl))
                            prepareAsync()
                        } catch (e: Exception) {
                            Log.e(TAG, "数据源错误: ${e.message}")
                            showError()
                            showErrorMessage("无法加载视频: ${e.message}")
                        }
                    }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {}
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            showError()
            showErrorMessage("初始化失败: ${e.message}")
        }
    }

    private fun showLoading() { loadingTextView.visibility = android.view.View.VISIBLE; errorTextView.visibility = android.view.View.GONE }
    private fun showError() { loadingTextView.visibility = android.view.View.GONE; errorTextView.visibility = android.view.View.VISIBLE }
    private fun showPlaying() { loadingTextView.visibility = android.view.View.GONE; errorTextView.visibility = android.view.View.GONE }

    private fun showErrorMessage(msg: String) {
        showError()
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        val url = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("播放错误").setMessage(msg)
            .setPositiveButton("确定") { d, _ -> d.dismiss(); finish() }
            .setNegativeButton("重试") { d, _ ->
                d.dismiss()
                if (url.isNotEmpty()) setupPlayer(url)
            }.show()
    }

    override fun onPause() { super.onPause(); player?.pause() }
    override fun onResume() { super.onResume() }
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
    }
}
