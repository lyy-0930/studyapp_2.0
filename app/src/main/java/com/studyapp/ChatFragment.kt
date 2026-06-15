package com.studyapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.studyapp.util.ImageLoaderUtil
import com.studyapp.manager.ApiService
import com.studyapp.model.ChatMessage
import com.studyapp.model.Conversation
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ChatFragment : Fragment() {

    private lateinit var apiService: ApiService
    private val scope = CoroutineScope(Dispatchers.Main)

    // 用户信息
    private var currentUserId: Int = 0
    private var currentUsername: String = ""

    // 视图
    private lateinit var conversationListView: LinearLayout
    private lateinit var chatDetailView: LinearLayout
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var emptyConversations: LinearLayout
    private lateinit var refreshConversationsBtn: Button
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendMessageBtn: Button
    private lateinit var chatBackBtn: TextView
    private lateinit var chatOtherName: TextView

    // 数据
    private var conversations = listOf<Conversation>()
    private var messages = listOf<ChatMessage>()
    private var currentConversation: Conversation? = null

    // 适配器
    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var messageAdapter: MessageAdapter

    // 轮询
    private var pollingJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentUserId = arguments?.getInt("user_id") ?: 0
        currentUsername = arguments?.getString("username") ?: ""
        apiService = ApiService.getInstance(requireContext())

        initViews(view)
        setupAdapters()
        setupListeners()
        loadConversations()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollingJob?.cancel()
        scope.cancel()
    }

    private fun initViews(view: View) {
        conversationListView = view.findViewById(R.id.conversationListView)
        chatDetailView = view.findViewById(R.id.chatDetailView)
        conversationsRecyclerView = view.findViewById(R.id.conversationsRecyclerView)
        emptyConversations = view.findViewById(R.id.emptyConversations)
        refreshConversationsBtn = view.findViewById(R.id.refreshConversationsBtn)
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
        messageInput = view.findViewById(R.id.messageInput)
        sendMessageBtn = view.findViewById(R.id.sendMessageBtn)
        chatBackBtn = view.findViewById(R.id.chatBackBtn)
        chatOtherName = view.findViewById(R.id.chatOtherName)
    }

    private fun setupAdapters() {
        conversationAdapter = ConversationAdapter { conv -> openConversation(conv) }
        conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        conversationsRecyclerView.adapter = conversationAdapter

        messageAdapter = MessageAdapter(currentUserId)
        messagesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        messagesRecyclerView.adapter = messageAdapter
    }

    private fun setupListeners() {
        chatBackBtn.setOnClickListener {
            showConversationList()
        }

        sendMessageBtn.setOnClickListener { sendMessage() }

        refreshConversationsBtn.setOnClickListener {
            refreshConversationsBtn.visibility = View.GONE
            emptyConversations.visibility = View.VISIBLE
            loadConversations()
        }
    }

    // ==================== 会话列表 ====================

    private fun loadConversations() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                apiService.getConversations(currentUserId)
            }
            result.onSuccess { list ->
                conversations = list
                refreshConversationsBtn.visibility = View.GONE
                if (list.isEmpty()) {
                    emptyConversations.visibility = View.VISIBLE
                    conversationsRecyclerView.visibility = View.GONE
                } else {
                    emptyConversations.visibility = View.GONE
                    conversationsRecyclerView.visibility = View.VISIBLE
                    conversationAdapter.updateData(list)
                }
            }.onFailure {
                emptyConversations.visibility = View.VISIBLE
                conversationsRecyclerView.visibility = View.GONE
                refreshConversationsBtn.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "加载失败，请检查网络后刷新", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openConversation(conv: Conversation) {
        currentConversation = conv
        chatOtherName.text = conv.otherUsername
        showChatDetail()
        loadMessages(conv.id)
    }

    // ==================== 聊天详情 ====================

    private fun showChatDetail() {
        conversationListView.visibility = View.GONE
        chatDetailView.visibility = View.VISIBLE
        loadMessages(currentConversation?.id ?: return)
        startPolling()
    }

    private fun showConversationList() {
        pollingJob?.cancel()
        chatDetailView.visibility = View.GONE
        conversationListView.visibility = View.VISIBLE
        currentConversation = null
        loadConversations()
    }

    private fun loadMessages(conversationId: Int) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                apiService.getMessages(conversationId, currentUserId)
            }
            result.onSuccess { list ->
                messages = list
                messageAdapter.updateData(list)
                if (list.isNotEmpty()) {
                    messagesRecyclerView.post {
                        messagesRecyclerView.smoothScrollToPosition(list.size - 1)
                    }
                }
            }.onFailure { e ->
                Log.e("ChatFragment", "加载消息失败: ${e.message}", e)
                if (!isAdded) return@launch
                Toast.makeText(requireContext(), "加载消息失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(5000)
                currentConversation?.let { conv ->
                    val result = withContext(Dispatchers.IO) {
                        apiService.getMessages(conv.id, currentUserId)
                    }
                    result.onSuccess { list ->
                        if (list.size != messages.size) {
                            messages = list
                            messageAdapter.updateData(list)
                            messagesRecyclerView.post {
                                messagesRecyclerView.smoothScrollToPosition(list.size - 1)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val content = messageInput.text.toString().trim()
        if (content.isEmpty()) return
        val convId = currentConversation?.id ?: return

        messageInput.text.clear()
        sendMessageBtn.isEnabled = false

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                apiService.sendMessage(convId, currentUserId, content)
            }
            sendMessageBtn.isEnabled = true
            result.onSuccess {
                loadMessages(convId)
            }.onFailure {
                Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== 适配器 ====================

    class ConversationAdapter(
        private val onClick: (Conversation) -> Unit
    ) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

        private var data = listOf<Conversation>()

        fun updateData(list: List<Conversation>) {
            data = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(data[position])
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val avatarText = itemView.findViewById<TextView>(R.id.conversationAvatarText)
            private val avatarImage = itemView.findViewById<ImageView>(R.id.conversationAvatarImage)
            private val name = itemView.findViewById<TextView>(R.id.conversationName)
            private val lastMessage = itemView.findViewById<TextView>(R.id.conversationLastMessage)
            private val time = itemView.findViewById<TextView>(R.id.conversationTime)
            private val badge = itemView.findViewById<TextView>(R.id.conversationBadge)

            fun bind(conv: Conversation) {
                name.text = conv.otherUsername
                lastMessage.text = if (conv.lastMessage.isNotEmpty()) conv.lastMessage else "[暂无消息]"

                // 头像：优先加载自定义头像，否则显示文字首字
                if (!conv.otherUserAvatarUrl.isNullOrEmpty()) {
                    val fullUrl = "${ApiService.BASE_URL}${conv.otherUserAvatarUrl}"
                    avatarImage.visibility = View.VISIBLE
                    avatarText.visibility = View.GONE
                    avatarImage.setImageResource(R.drawable.circle_avatar)
                    ImageLoaderUtil.load(avatarImage, fullUrl, crossfade = true, circleCrop = true)
                } else {
                    avatarImage.visibility = View.GONE
                    avatarText.visibility = View.VISIBLE
                    avatarText.text = conv.otherUsername.first().toString()
                }

                // 格式化时间
                time.text = try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val date = sdf.parse(conv.lastMessageAt.replace("Z", "").substringBefore("."))
                    val out = SimpleDateFormat("HH:mm", Locale.getDefault())
                    out.format(date!!)
                } catch (e: Exception) { "" }

                if (conv.unreadCount > 0) {
                    badge.visibility = View.VISIBLE
                    badge.text = if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString()
                } else {
                    badge.visibility = View.GONE
                }

                itemView.setOnClickListener { onClick(conv) }
            }
        }
    }

    class MessageAdapter(
        private val currentUserId: Int
    ) : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

        private var data = listOf<ChatMessage>()

        fun updateData(list: List<ChatMessage>) {
            data = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(data[position])
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val bubble = itemView.findViewById<TextView>(R.id.messageBubble)

            fun bind(msg: ChatMessage) {
                val isSent = msg.senderId == currentUserId
                bubble.text = msg.content

                val parent = bubble.parent as? LinearLayout
                if (parent != null) {
                    parent.gravity = if (isSent) Gravity.END else Gravity.START
                }

                if (isSent) {
                    bubble.setBackgroundResource(R.drawable.edit_text_background)
                    bubble.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        bubble.context.getColor(R.color.tsinghua_purple)
                    )
                    bubble.setTextColor(bubble.context.getColor(R.color.white))
                } else {
                    bubble.setBackgroundResource(R.drawable.edit_text_background)
                    bubble.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        bubble.context.getColor(R.color.white)
                    )
                    bubble.setTextColor(bubble.context.getColor(R.color.darker_gray))
                }
            }
        }
    }
}
