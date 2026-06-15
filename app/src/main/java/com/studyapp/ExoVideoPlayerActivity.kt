package com.studyapp

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.studyapp.manager.ApiService
import kotlinx.coroutines.launch

/**
 * 视频播放Activity（使用 Android 原生 MediaPlayer）
 * 替代原有的 ExoPlayer 实现
 */
class ExoVideoPlayerActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var loadingTextView: TextView
    private lateinit var errorTextView: TextView
    private lateinit var mediaController: LinearLayout
    private lateinit var playPauseButton: ImageButton
    private lateinit var progressSeekBar: SeekBar
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView

    private var player: MediaPlayer? = null
    private var isPrepared = false
    private var isControllerVisible = false
    private val handler = Handler(Looper.getMainLooper())
    private var controllerHideRunnable: Runnable? = null
    private var progressUpdateRunnable: Runnable? = null

    // 学习记录相关
    private var courseId: Int = -1
    private var currentUserId: Int = 0
    private lateinit var apiService: ApiService
    private val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    private var studyRecordRunnable: Runnable? = null
    private var totalWatchTimeMs: Long = 0L
    private var lastRecordTimeMs: Long = 0L
    private var isRecordingStudy: Boolean = false

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_COURSE_NAME = "extra_course_name"
        const val EXTRA_COURSE_ID = "course_id"
        private const val TAG = "VideoPlayer"
        private const val STUDY_RECORD_INTERVAL_MS = 30000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exo_video_player)

        initViews()

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "课程视频"
        courseId = intent.getIntExtra(EXTRA_COURSE_ID, -1)

        apiService = ApiService.getInstance(this)
        val sharedPref = getSharedPreferences("login_prefs", MODE_PRIVATE)
        currentUserId = sharedPref.getInt("user_id", 0)
        supportActionBar?.title = courseName

        if (videoUrl.isNullOrEmpty()) {
            showErrorMessage("视频地址无效")
            finish(); return
        }

        Log.d(TAG, "播放视频: $videoUrl")
        setupMediaPlayer(videoUrl)
    }

    private fun initViews() {
        surfaceView = findViewById(R.id.surfaceView)
        loadingTextView = findViewById(R.id.loadingTextView)
        errorTextView = findViewById(R.id.errorTextView)
        mediaController = findViewById(R.id.mediaController)
        playPauseButton = findViewById(R.id.playPauseButton)
        progressSeekBar = findViewById(R.id.progressSeekBar)
        currentTimeText = findViewById(R.id.currentTimeText)
        totalTimeText = findViewById(R.id.totalTimeText)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun setupMediaPlayer(videoUrl: String) {
        showLoading()
        try {
            player = MediaPlayer().apply {
                setOnPreparedListener { mp ->
                    isPrepared = true
                    showPlaying()
                    totalTimeText.text = formatTime(mp.duration)
                    progressSeekBar.max = mp.duration
                    mp.start()
                    startStudyRecording()
                    startProgressUpdate()
                    showController()
                }
                setOnCompletionListener {
                    stopStudyRecording()
                    recordStudyProgressImmediate()
                    recordFinalStudyProgress()
                    Toast.makeText(this@ExoVideoPlayerActivity, "播放完成", Toast.LENGTH_SHORT).show()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "播放错误: what=$what, extra=$extra")
                    showError()
                    showErrorMessage("视频播放失败 ($what)")
                    true
                }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) showLoading()
                    else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) showPlaying()
                    false
                }

                val holder = surfaceView.holder
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        setDisplay(holder)
                        try {
                            setDataSource(this@ExoVideoPlayerActivity, Uri.parse(videoUrl))
                            prepareAsync()
                        } catch (e: Exception) {
                            Log.e(TAG, "设置数据源失败: ${e.message}")
                            showError()
                            showErrorMessage("无法加载视频: ${e.message}")
                        }
                    }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {}
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化播放器失败", e)
            showError()
            showErrorMessage("播放器初始化失败: ${e.message}")
        }

        surfaceView.setOnClickListener { toggleController() }
        playPauseButton.setOnClickListener { togglePlayPause() }
        progressSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar) { stopProgressUpdate() }
            override fun onStopTrackingTouch(sb: SeekBar) {
                player?.seekTo(sb.progress)
                startProgressUpdate()
            }
        })
    }

    private fun togglePlayPause() {
        val mp = player ?: return
        if (mp.isPlaying) {
            mp.pause()
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            stopStudyRecording(); recordStudyProgressImmediate()
        } else {
            mp.start()
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            startStudyRecording()
        }
    }

    private fun showController() {
        isControllerVisible = true
        mediaController.visibility = View.VISIBLE
        controllerHideRunnable?.let { handler.removeCallbacks(it) }
        controllerHideRunnable = Runnable { hideController() }
        handler.postDelayed(controllerHideRunnable!!, 4000)
    }

    private fun hideController() { isControllerVisible = false; mediaController.visibility = View.GONE }

    private fun toggleController() { if (isControllerVisible) hideController() else showController() }

    private fun startProgressUpdate() {
        progressUpdateRunnable?.let { handler.removeCallbacks(it) }
        progressUpdateRunnable = Runnable {
            val mp = player ?: return@Runnable
            if (mp.isPlaying) {
                currentTimeText.text = formatTime(mp.currentPosition)
                progressSeekBar.progress = mp.currentPosition
                handler.postDelayed(progressUpdateRunnable!!, 500)
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun stopProgressUpdate() { progressUpdateRunnable?.let { handler.removeCallbacks(it) } }

    private fun formatTime(ms: Int): String {
        if (ms <= 0) return "00:00"
        val sec = ms / 1000
        return String.format("%02d:%02d", sec / 60, sec % 60)
    }

    private fun showLoading() { loadingTextView.visibility = View.VISIBLE; errorTextView.visibility = View.GONE }
    private fun showError() { loadingTextView.visibility = View.GONE; errorTextView.visibility = View.VISIBLE }
    private fun showPlaying() { loadingTextView.visibility = View.GONE; errorTextView.visibility = View.GONE }

    private fun showErrorMessage(msg: String) {
        showError()
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        val url = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("播放错误")
            .setMessage(msg)
            .setPositiveButton("确定") { d, _ -> d.dismiss(); finish() }
            .setNegativeButton("重试") { d, _ ->
                d.dismiss()
                if (url.isNotEmpty()) setupMediaPlayer(url)
            }
            .setNeutralButton("切换到VideoView") { d, _ ->
                d.dismiss()
                startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
                    putExtra(EXTRA_VIDEO_URL, url)
                    putExtra(EXTRA_COURSE_NAME, intent.getStringExtra(EXTRA_COURSE_NAME))
                })
                finish()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        player?.let { if (it.isPlaying) { it.pause(); playPauseButton.setImageResource(android.R.drawable.ic_media_play) } }
    }

    override fun onResume() {
        super.onResume()
        player?.let { if (!it.isPlaying && isPrepared) { it.start(); playPauseButton.setImageResource(android.R.drawable.ic_media_pause) } }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ==================== 学习记录 ====================

    private fun startStudyRecording() {
        if (isRecordingStudy || courseId <= 0 || currentUserId <= 0) return
        isRecordingStudy = true; totalWatchTimeMs = 0L; lastRecordTimeMs = System.currentTimeMillis()
        studyRecordRunnable = Runnable {
            if (isRecordingStudy && player?.isPlaying == true) { recordStudyProgress()
                handler.postDelayed(studyRecordRunnable!!, STUDY_RECORD_INTERVAL_MS) }
        }
        handler.postDelayed(studyRecordRunnable!!, STUDY_RECORD_INTERVAL_MS)
    }

    private fun stopStudyRecording() {
        if (!isRecordingStudy) return
        isRecordingStudy = false
        studyRecordRunnable?.let { handler.removeCallbacks(it); studyRecordRunnable = null }
    }

    private fun recordStudyProgress() {
        if (courseId <= 0 || currentUserId <= 0) return
        val now = System.currentTimeMillis()
        totalWatchTimeMs += (now - lastRecordTimeMs); lastRecordTimeMs = now
        val mp = player ?: return
        if (mp.duration > 0) {
            val progress = (mp.currentPosition * 100 / mp.duration).toInt()
            val watchMin = (totalWatchTimeMs / 60000).toInt()
            coroutineScope.launch {
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        apiService.recordStudyProgress(courseId, currentUserId, watchMin, progress)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun recordStudyProgressImmediate() {
        if (courseId <= 0 || currentUserId <= 0) return
        val mp = player ?: return
        if (mp.duration > 0) {
            val progress = (mp.currentPosition * 100 / mp.duration).toInt()
            val watchMin = (totalWatchTimeMs / 60000).toInt()
            coroutineScope.launch {
                try { apiService.recordStudyProgress(courseId, currentUserId, watchMin, progress) }
                catch (_: Exception) {}
            }
        }
    }

    private fun recordFinalStudyProgress() {
        if (courseId <= 0 || currentUserId <= 0) return
        coroutineScope.launch {
            try { apiService.recordStudyProgress(courseId, currentUserId, (totalWatchTimeMs / 60000).toInt(), 100) }
            catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        stopProgressUpdate()
        player?.release(); player = null
    }
}
