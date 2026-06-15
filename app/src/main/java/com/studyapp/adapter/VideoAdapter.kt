package com.studyapp.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.R
import com.studyapp.model.VideoItem

/**
 * 视频列表适配器
 * 用于在RecyclerView中显示视频列表
 */
class VideoAdapter(
    private val videoList: MutableList<VideoItem> = mutableListOf(),
    private val onItemClick: (VideoItem) -> Unit = {}
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    companion object {
        private const val TAG = "VideoAdapter"
    }

    /**
     * 更新整个视频列表
     */
    fun updateVideos(newVideos: List<VideoItem>) {
        videoList.clear()
        videoList.addAll(newVideos)
        notifyDataSetChanged() // 通知数据变化，刷新界面
        Log.d(TAG, "视频列表已更新，共 ${videoList.size} 个视频")
    }

    /**
     * 添加单个视频到列表开头
     */
    fun addVideo(video: VideoItem) {
        videoList.add(0, video) // 添加到列表开头
        notifyItemInserted(0) // 通知有新的项插入
        Log.d(TAG, "添加视频: ${video.getShortFileName()}")
    }

    /**
     * 获取所有视频
     */
    fun getVideos(): List<VideoItem> = videoList

    /**
     * 清空视频列表
     */
    fun clearVideos() {
        val size = videoList.size
        videoList.clear()
        notifyItemRangeRemoved(0, size) // 通知移除所有项
        Log.d(TAG, "清空视频列表，共移除 $size 个视频")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        // 加载列表项布局
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        // 绑定数据到ViewHolder
        val video = videoList[position]
        holder.bind(video)

        // 设置点击事件
        holder.itemView.setOnClickListener {
            Log.d(TAG, "点击视频: ${video.getShortFileName()}, URL: ${video.url}")
            onItemClick(video)
        }
    }

    override fun getItemCount(): Int = videoList.size

    /**
     * ViewHolder类，负责管理列表项的视图
     */
    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.videoTitleTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.videoTimeTextView)
        private val urlTextView: TextView = itemView.findViewById(R.id.videoUrlTextView)

        /**
         * 绑定视频数据到视图
         */
        fun bind(video: VideoItem) {
            // 设置视频标题
            titleTextView.text = video.title

            // 设置上传时间
            timeTextView.text = "上传时间: ${video.getFormattedTime()}"

            // 设置URL（简短显示）
            val shortUrl = if (video.url.length > 40) {
                "${video.url.take(40)}..."
            } else {
                video.url
            }
            urlTextView.text = "地址: $shortUrl"

            // 设置点击效果（可选）
            itemView.setOnClickListener {
                Log.d("VideoViewHolder", "点击视频: ${video.title}")
            }
        }
    }
}