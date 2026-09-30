package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FriendDisplay
import com.example.data.model.PrivateMessage
import com.example.data.realtime.PrivateChatRealtimeClient
import com.example.data.repository.FriendRepository
import com.example.data.repository.PrivateChatRepository
import com.example.util.friendlyErrorMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant

private const val MAX_DM_LENGTH = 1000
private const val MAX_REPLY_SNAPSHOT_LENGTH = 200

data class ConversationItem(
    val friend: FriendDisplay,
    val lastMessage: PrivateMessage?,
    val unread: Int
)

data class PrivateChatUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val conversations: List<ConversationItem> = emptyList(),
    /** Teman yang lagi dibuka chat-nya (null = belum milih chat). */
    val selected: FriendDisplay? = null,
    val messages: List<PrivateMessage> = emptyList(),
    val isLoadingMessages: Boolean = false,
    val isSending: Boolean = false,
    val sendError: String? = null,
    /** Pesan yang lagi mau dibalas (null = gak lagi reply apa-apa). */
    val replyTarget: PrivateMessage? = null
)

/**
 * ViewModel tab "Teman" di layar Chat. Daftar percakapan = semua teman
 * (status accepted) diurutkan dari pesan terakhir; pesan masuk dateng lewat
 * Realtime. Baru nyambung ke server pas tab-nya pertama kali dibuka
 * ([ensureStarted]), bukan pas layar Chat kebuka.
 */
class PrivateChatViewModel(
    private val myUid: String,
    private val friendRepository: FriendRepository = FriendRepository(),
    private val repository: PrivateChatRepository = PrivateChatRepository(),
    private val realtimeClient: PrivateChatRealtimeClient = PrivateChatRealtimeClient(myUid)
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivateChatUiState())
    val uiState: StateFlow<PrivateChatUiState> = _uiState.asStateFlow()

    private var started = false
    private var friends: List<FriendDisplay> = emptyList()
    private var recentMessages: List<PrivateMessage> = emptyList()

    fun ensureStarted() {
        if (started) return
        started = true
        loadConversations(showLoading = true)
        realtimeClient.incoming(viewModelScope)
            .onEach { applyIncoming(it) }
            .launchIn(viewModelScope)
    }

    fun loadConversations(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val listsDeferred = async { friendRepository.getLists(myUid) }
            val recentDeferred = async { repository.getRecent(myUid) }
            val lists = listsDeferred.await()
            val recent = recentDeferred.await()

            lists.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = friendlyErrorMessage(e, "Gagal memuat daftar chat")
                )
                return@launch
            }
            friends = lists.getOrThrow().friends
            recentMessages = recent.getOrNull() ?: recentMessages
            _uiState.value = _uiState.value.copy(isLoading = false, error = null)
            rebuildConversations()
        }
    }

    private fun rebuildConversations() {
        val byOther = recentMessages.groupBy { if (it.senderUid == myUid) it.recipientUid else it.senderUid }
        val items = friends.map { f ->
            val msgs = byOther[f.firebaseUid].orEmpty()
            ConversationItem(
                friend = f,
                lastMessage = msgs.maxByOrNull { it.id },
                unread = msgs.count { it.recipientUid == myUid && it.readAt == null }
            )
        }.sortedWith(
            compareByDescending<ConversationItem> { it.lastMessage?.id ?: -1L }
                .thenBy { it.friend.username.lowercase() }
        )
        _uiState.value = _uiState.value.copy(conversations = items)
    }

    fun openChat(friend: FriendDisplay) {
        _uiState.value = _uiState.value.copy(
            selected = friend,
            messages = emptyList(),
            isLoadingMessages = true,
            sendError = null,
            replyTarget = null
        )
        viewModelScope.launch {
            repository.getConversation(myUid, friend.firebaseUid)
                .onSuccess { msgs ->
                    if (_uiState.value.selected?.firebaseUid != friend.firebaseUid) return@onSuccess
                    val hadUnread = msgs.any { it.recipientUid == myUid && it.readAt == null }
                    val now = Instant.now().toString()
                    val shown = msgs.map { if (it.recipientUid == myUid && it.readAt == null) it.copy(readAt = now) else it }
                    recentMessages = recentMessages.filterNot { it.involves(friend.firebaseUid) } + shown
                    _uiState.value = _uiState.value.copy(messages = shown, isLoadingMessages = false)
                    rebuildConversations()
                    if (hadUnread) repository.markRead(myUid, friend.firebaseUid)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMessages = false,
                        sendError = friendlyErrorMessage(e, "Gagal memuat pesan")
                    )
                }
        }
    }

    fun closeChat() {
        _uiState.value = _uiState.value.copy(
            selected = null,
            messages = emptyList(),
            sendError = null,
            replyTarget = null
        )
    }

    fun setReplyTarget(message: PrivateMessage) {
        _uiState.value = _uiState.value.copy(replyTarget = message)
    }

    fun clearReplyTarget() {
        _uiState.value = _uiState.value.copy(replyTarget = null)
    }

    fun sendMessage(text: String) {
        val friend = _uiState.value.selected ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isSending) return
        if (trimmed.length > MAX_DM_LENGTH) {
            _uiState.value = _uiState.value.copy(sendError = "Pesan maksimal $MAX_DM_LENGTH karakter")
            return
        }
        val target = _uiState.value.replyTarget
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, sendError = null)
            repository.send(
                myUid = myUid,
                otherUid = friend.firebaseUid,
                text = trimmed,
                replyToId = target?.id,
                replyToSenderUid = target?.senderUid,
                replyToMessage = target?.message?.take(MAX_REPLY_SNAPSHOT_LENGTH)
            )
                .onSuccess { msg ->
                    recentMessages = recentMessages + msg
                    val current = _uiState.value
                    val stillOpen = current.selected?.firebaseUid == friend.firebaseUid
                    _uiState.value = current.copy(
                        isSending = false,
                        replyTarget = if (stillOpen) null else current.replyTarget,
                        messages = if (stillOpen && current.messages.none { it.id == msg.id }) current.messages + msg else current.messages
                    )
                    rebuildConversations()
                }
                .onFailure { e ->
                    val message = if (e is HttpException && e.code() in listOf(401, 403)) {
                        "Gagal kirim. Kalian mungkin udah nggak berteman."
                    } else {
                        friendlyErrorMessage(e, "Gagal mengirim pesan")
                    }
                    _uiState.value = _uiState.value.copy(isSending = false, sendError = message)
                }
        }
    }

    fun clearSendError() {
        _uiState.value = _uiState.value.copy(sendError = null)
    }

    private fun applyIncoming(msg: PrivateMessage) {
        if (msg.recipientUid != myUid) return
        if (recentMessages.any { it.id == msg.id }) return

        val isOpen = _uiState.value.selected?.firebaseUid == msg.senderUid
        val stored = if (isOpen) msg.copy(readAt = Instant.now().toString()) else msg
        recentMessages = recentMessages + stored

        // Pengirim belum ada di daftar teman lokal (baru aja diterima) -> muat ulang daftar.
        if (friends.none { it.firebaseUid == msg.senderUid }) {
            loadConversations(showLoading = false)
            return
        }
        if (isOpen) {
            val current = _uiState.value
            _uiState.value = current.copy(messages = current.messages + stored)
            viewModelScope.launch { repository.markRead(myUid, msg.senderUid) }
        }
        rebuildConversations()
    }

    private fun PrivateMessage.involves(otherUid: String): Boolean =
        (senderUid == myUid && recipientUid == otherUid) || (senderUid == otherUid && recipientUid == myUid)
}
