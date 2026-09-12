package com.example.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.realtime.ChatRealtimeClient
import com.example.data.realtime.ChatRealtimeEvent
import com.example.data.repository.ChatRepository
import com.example.data.repository.ClanRepository
import com.example.data.repository.PremiumRepository
import com.example.util.AvatarUploader
import com.example.util.VoiceNoteUploader
import com.example.util.VoiceRecorder
import com.example.util.friendlyErrorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

private const val COOLDOWN_SECONDS = 5
// Resync penuh cuma dipakai sesekali sebagai jaring pengaman (bukan lagi
// mekanisme utama) -- buat nutup celah kecil kalau ada event Realtime yang
// kelewat pas koneksi lagi putus-nyambung.
private const val RESYNC_INTERVAL_MS = 45_000L
private const val MAX_MESSAGE_LENGTH = 300
private const val MAX_USERNAME_LENGTH = 24

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val cooldownSeconds: Int = 0,
    val errorMessage: String? = null,

    // Profil (nama & avatar) yang lagi dipakai buat kirim pesan.
    val displayUsername: String = "",
    val displayAvatarUrl: String? = null,
    // Banner profil (buat ProfileScreen) -- disimpen di sini juga biar tiap
    // saveProfile() dari dialog Chat gak nge-null-in banner yang udah diupload
    // lewat ProfileScreen. Gak dipakai buat tampilan di dalam Chat sendiri.
    val displayBannerUrl: String? = null,
    val isPremium: Boolean = false,

    // State buat dialog "Edit Profil".
    val isProfileDialogOpen: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val profileError: String? = null,

    // Pesan yang lagi mau di-reply (null = gak lagi reply apa-apa).
    val replyTarget: ChatMessage? = null,
    // id pesan yang lagi diproses hapus, buat nampilin loading kecil di bubble-nya.
    val deletingMessageId: Long? = null,

    // Kumpulan firebase_uid pengirim yang statusnya premium -- dipakai buat
    // nampilin badge Premium di samping username di bubble chat.
    val premiumUids: Set<String> = emptySet(),

    // Tag clan per firebase_uid pengirim (uid -> "ANK") -- dipakai buat
    // nampilin badge singkat clan di samping username di bubble chat.
    // Cuma keisi buat pengirim yang emang lagi gabung clan.
    val clanTagsByUid: Map<String, String> = emptyMap(),

    // --- Pesan Suara (VN) -- kirim khusus Premium, dengerin/play terbuka
    // buat semua user (lihat catatan di ChatRepository.sendVoiceMessage).
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    // Rekaman yang udah selesai & lagi nunggu dikonfirmasi kirim (preview
    // bar di atas ChatInputBar) -- null = gak ada rekaman pending.
    val pendingVoiceFile: File? = null,
    val pendingVoiceDurationSeconds: Int = 0,
    val isSendingVoice: Boolean = false
)

/**
 * Cooldown 5 detik dihitung MURNI di client (gak ada tabel/kolom rate-limit
 * di server) -- cukup buat nyegah spam kasual dari UI normal. Kalau nanti
 * mau lebih ketat (misal cegah orang yang modif APK/panggil API langsung),
 * itu butuh pengecekan tambahan di server (contoh: Edge Function yang cek
 * timestamp pesan terakhir per firebase_uid sebelum insert).
 *
 * Username & avatar chat: username bisa diganti SEMUA user (disimpan di
 * tabel `chat_profiles`, override nama dari akun Google). Avatar DEFAULT
 * di chat adalah avatar auto-generate Zenime sendiri (warna + inisial,
 * lihat GeneratedAvatar.kt) -- BUKAN foto akun Google, biar gak "kebawa"
 * foto asli user yang belum tentu mau dipajang di chat publik. Avatar
 * foto asli (upload dari galeri) DIBATASI khusus user Premium.
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val premiumRepository: PremiumRepository,
    private val firebaseUid: String,
    fallbackUsername: String,
    private val realtimeClient: ChatRealtimeClient = ChatRealtimeClient(),
    private val clanRepository: ClanRepository = ClanRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            displayUsername = fallbackUsername,
            // null = belum ada foto custom -> UI nampilin GeneratedAvatar
            // (avatar warna + inisial), bukan foto Google.
            displayAvatarUrl = null
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var realtimeJob: Job? = null
    private var resyncJob: Job? = null
    private var cooldownJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var voiceRecorder: VoiceRecorder? = null

    // Cache status premium per firebase_uid biar gak nge-hit zenime-check-premium
    // berkali-kali buat pengirim yang sama. Sekali dicek, hasilnya dipakai
    // terus selama sesi chat ini kebuka.
    private val premiumStatusCache = mutableMapOf<String, Boolean>()
    private val checkedUids = mutableSetOf<String>()

    // Sama pola kayak cache premium di atas, tapi buat tag clan. `null` di
    // value artinya "udah dicek, ternyata gak gabung clan manapun" -- beda
    // sama "belum pernah dicek sama sekali" (uid gak ada di map ini).
    private val clanTagCache = mutableMapOf<String, String?>()
    private val clanCheckedUids = mutableSetOf<String>()

    init {
        loadProfileAndPremiumStatus(fallbackUsername)
        // Full load sekali di awal (isi riwayat pesan), abis itu pesan baru
        // masuk lewat Realtime -- bukan polling ulang tiap beberapa detik.
        viewModelScope.launch { refreshMessages() }
        startRealtime()
        startPeriodicResync()
    }

    private fun loadProfileAndPremiumStatus(fallbackUsername: String) {
        viewModelScope.launch {
            val profile = try {
                repository.getProfile(firebaseUid)
            } catch (e: Exception) {
                null
            }

            val premiumResult = premiumRepository.checkPremiumStatus(firebaseUid)
            val isPremium = premiumResult.getOrNull()?.isPremium ?: false

            // Foto custom (hasil upload) cuma dipasang kalau user-nya masih
            // premium. Kalau enggak (baik belum pernah upload, maupun udah
            // expired), avatar dibiarkan null -> tampil avatar generate.
            val resolvedAvatarUrl = if (isPremium) profile?.avatarUrl else null

            // Simpan status premium diri sendiri ke cache juga, biar bubble
            // pesan sendiri (kalau suatu saat ditampilin ke user lain) konsisten
            // dan gak perlu ngecek ulang lewat checkPremiumForNewSenders().
            checkedUids += firebaseUid
            premiumStatusCache[firebaseUid] = isPremium

            _uiState.value = _uiState.value.copy(
                displayUsername = profile?.username?.ifBlank { fallbackUsername } ?: fallbackUsername,
                displayAvatarUrl = resolvedAvatarUrl,
                displayBannerUrl = profile?.bannerUrl,
                isPremium = isPremium,
                premiumUids = premiumUidsSnapshot()
            )
        }
    }

    private fun premiumUidsSnapshot(): Set<String> =
        premiumStatusCache.filterValues { it }.keys.toSet()

    /**
     * Cek tag clan buat pengirim-pengirim baru yang muncul di daftar pesan
     * (pola persis sama kayak [checkPremiumForNewSenders], cuma buat data
     * clan). Fetch dilakuin batch sekali jalan (satu request `in.(...)`
     * buat semua uid baru), bukan satu-satu per uid.
     */
    private fun checkClanTagsForNewSenders(messages: List<ChatMessage>) {
        val newUids = messages
            .map { it.firebaseUid }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { clanCheckedUids.contains(it) }
        if (newUids.isEmpty()) return

        clanCheckedUids += newUids
        viewModelScope.launch {
            val tags = clanRepository.getClanTagsForUids(newUids).getOrDefault(emptyMap())
            newUids.forEach { uid -> clanTagCache[uid] = tags[uid] }
            _uiState.value = _uiState.value.copy(
                clanTagsByUid = clanTagCache.filterValues { it != null }.mapValues { it!! }
            )
        }
    }

    /**
     * Cek status premium buat pengirim-pengirim baru yang muncul di daftar
     * pesan (belum pernah dicek sebelumnya di sesi ini), lalu update
     * `premiumUids` di uiState biar badge Premium muncul di samping
     * username mereka. Dijalankan tiap habis refreshMessages().
     */
    private fun checkPremiumForNewSenders(messages: List<ChatMessage>) {
        val newUids = messages
            .map { it.firebaseUid }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { checkedUids.contains(it) }
        if (newUids.isEmpty()) return

        checkedUids += newUids
        viewModelScope.launch {
            newUids.forEach { uid ->
                val result = premiumRepository.checkPremiumStatus(uid)
                premiumStatusCache[uid] = result.getOrNull()?.isPremium ?: false
            }
            _uiState.value = _uiState.value.copy(premiumUids = premiumUidsSnapshot())
        }
    }

    /** Subscribe ke Supabase Realtime -- gantiin polling PostgREST tiap 3 detik. */
    private fun startRealtime() {
        realtimeJob?.cancel()
        realtimeJob = realtimeClient.events(viewModelScope)
            .onEach { event -> applyRealtimeEvent(event) }
            .launchIn(viewModelScope)
    }

    private fun applyRealtimeEvent(event: ChatRealtimeEvent) {
        when (event) {
            is ChatRealtimeEvent.Inserted -> {
                val current = _uiState.value.messages
                if (current.any { it.id == event.message.id }) return
                val updated = (current + event.message).takeLast(200)
                _uiState.value = _uiState.value.copy(messages = updated, isLoading = false)
                checkPremiumForNewSenders(listOf(event.message))
                checkClanTagsForNewSenders(listOf(event.message))
            }
            is ChatRealtimeEvent.Deleted -> {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.filterNot { it.id == event.id }
                )
            }
        }
    }

    /**
     * Jaring pengaman: full refresh jarang-jarang (bukan tiap beberapa detik
     * kayak sebelumnya) buat nutup celah kalau ada event Realtime yang
     * kelewat pas koneksi lagi putus-nyambung.
     */
    private fun startPeriodicResync() {
        resyncJob?.cancel()
        resyncJob = viewModelScope.launch {
            while (true) {
                delay(RESYNC_INTERVAL_MS)
                refreshMessages()
            }
        }
    }

    private suspend fun refreshMessages() {
        try {
            val messages = repository.getMessages()
            _uiState.value = _uiState.value.copy(
                messages = messages,
                isLoading = false,
                errorMessage = null
            )
            checkPremiumForNewSenders(messages)
            checkClanTagsForNewSenders(messages)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = _uiState.value.errorMessage ?: (friendlyErrorMessage(e, "Gagal memuat chat"))
            )
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.cooldownSeconds > 0 || _uiState.value.isSending) return

        val safeText = trimmed.take(MAX_MESSAGE_LENGTH)
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
            try {
                repository.sendMessage(
                    firebaseUid = firebaseUid,
                    username = state.displayUsername,
                    avatarUrl = state.displayAvatarUrl,
                    message = safeText,
                    replyToId = state.replyTarget?.id,
                    replyToUsername = state.replyTarget?.username,
                    replyToMessage = state.replyTarget?.message
                )
                _uiState.value = _uiState.value.copy(isSending = false, replyTarget = null)
                refreshMessages()
                startCooldown()
            } catch (e: Exception) {
                // Gagal kirim -- gak usah kena cooldown, biar user bisa langsung coba lagi.
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = friendlyErrorMessage(e, "Gagal mengirim pesan")
                )
            }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in COOLDOWN_SECONDS downTo 1) {
                _uiState.value = _uiState.value.copy(cooldownSeconds = remaining)
                delay(1000L)
            }
            _uiState.value = _uiState.value.copy(cooldownSeconds = 0)
        }
    }

    // --- Edit Profil ---

    fun openProfileDialog() {
        _uiState.value = _uiState.value.copy(isProfileDialogOpen = true, profileError = null)
    }

    fun closeProfileDialog() {
        _uiState.value = _uiState.value.copy(isProfileDialogOpen = false, profileError = null)
    }

    /** Dipanggil pas user non-premium coba tap avatar buat ganti foto. */
    fun notifyAvatarRequiresPremium() {
        _uiState.value = _uiState.value.copy(
            profileError = "Upload foto profil khusus buat member Premium"
        )
    }

    /** Simpan username baru (dibuka semua user, gak peduli premium). */
    fun saveUsername(newUsername: String) {
        val trimmed = newUsername.trim().take(MAX_USERNAME_LENGTH)
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(profileError = "Username gak boleh kosong")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingProfile = true, profileError = null)
            try {
                val saved = repository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = trimmed,
                    avatarUrl = _uiState.value.displayAvatarUrl,
                    bannerUrl = _uiState.value.displayBannerUrl
                )
                _uiState.value = _uiState.value.copy(
                    isSavingProfile = false,
                    displayUsername = saved.username,
                    isProfileDialogOpen = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingProfile = false,
                    profileError = friendlyErrorMessage(e, "Gagal menyimpan username")
                )
            }
        }
    }

    /**
     * Upload foto profil baru buat Chat Global. Dipanggil setelah user milih
     * gambar dari galeri -- pengecekan premium tetap diulang di sini (bukan
     * cuma di UI) biar gak bisa dilewatin dengan manggil fungsi ini langsung.
     */
    fun uploadAvatar(context: Context, imageUri: Uri) {
        if (!_uiState.value.isPremium) {
            _uiState.value = _uiState.value.copy(
                profileError = "Upload foto profil khusus buat member Premium"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAvatar = true, profileError = null)
            try {
                val url = AvatarUploader.uploadAvatar(context, imageUri, firebaseUid)
                val saved = repository.saveProfile(
                    firebaseUid = firebaseUid,
                    username = _uiState.value.displayUsername,
                    avatarUrl = url,
                    bannerUrl = _uiState.value.displayBannerUrl
                )
                _uiState.value = _uiState.value.copy(
                    isUploadingAvatar = false,
                    displayAvatarUrl = saved.avatarUrl
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploadingAvatar = false,
                    profileError = friendlyErrorMessage(e, "Gagal upload foto profil")
                )
            }
        }
    }

    // --- Reply ---

    fun setReplyTarget(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(replyTarget = message)
    }

    fun clearReplyTarget() {
        _uiState.value = _uiState.value.copy(replyTarget = null)
    }

    // --- Hapus pesan ---

    /** Cuma bisa hapus pesan sendiri -- dicek dua kali (UI cuma nampilin tombol di pesan sendiri, dan di sini juga). */
    fun deleteMessage(message: ChatMessage) {
        if (message.firebaseUid != firebaseUid) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingMessageId = message.id, errorMessage = null)
            try {
                repository.deleteMessage(id = message.id, firebaseUid = firebaseUid)
                _uiState.value = _uiState.value.copy(
                    deletingMessageId = null,
                    messages = _uiState.value.messages.filterNot { it.id == message.id }
                )
                if (_uiState.value.replyTarget?.id == message.id) {
                    clearReplyTarget()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    deletingMessageId = null,
                    errorMessage = friendlyErrorMessage(e, "Gagal menghapus pesan")
                )
            }
        }
    }

    // --- Pesan Suara (VN) ---

    /** Dipanggil pas user non-premium coba pencet tombol mic. */
    fun notifyVoiceRequiresPremium() {
        _uiState.value = _uiState.value.copy(
            errorMessage = "Kirim pesan suara khusus buat member Premium"
        )
    }

    /**
     * Mulai rekam. Pengecekan premium diulang di sini (bukan cuma di UI)
     * biar gak bisa dilewatin dengan manggil fungsi ini langsung. Izin
     * RECORD_AUDIO wajib udah di-grant sebelum fungsi ini dipanggil dari UI.
     */
    fun startVoiceRecording(context: Context) {
        if (!_uiState.value.isPremium) {
            notifyVoiceRequiresPremium()
            return
        }
        if (_uiState.value.isRecording || _uiState.value.isSending || _uiState.value.isSendingVoice) return

        try {
            val recorder = VoiceRecorder(context.applicationContext)
            recorder.start()
            voiceRecorder = recorder
            _uiState.value = _uiState.value.copy(
                isRecording = true,
                recordingSeconds = 0,
                errorMessage = null
            )
            recordingTimerJob?.cancel()
            recordingTimerJob = viewModelScope.launch {
                while (true) {
                    delay(1000L)
                    _uiState.value = _uiState.value.copy(
                        recordingSeconds = _uiState.value.recordingSeconds + 1
                    )
                }
            }
        } catch (e: Exception) {
            voiceRecorder = null
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                errorMessage = "Gagal mulai rekam. Cek izin mikrofon, ya."
            )
        }
    }

    /** Batalin rekaman yang lagi jalan, gak jadi disimpen. */
    fun cancelVoiceRecording() {
        recordingTimerJob?.cancel()
        voiceRecorder?.cancel()
        voiceRecorder = null
        _uiState.value = _uiState.value.copy(isRecording = false, recordingSeconds = 0)
    }

    /** Berhentiin rekaman & taruh hasilnya sebagai "pending" -- nunggu user konfirmasi kirim lewat sendVoiceNote(). */
    fun stopVoiceRecording() {
        recordingTimerJob?.cancel()
        val file = voiceRecorder?.currentFile()
        val duration = voiceRecorder?.stop()
        voiceRecorder = null

        if (duration == null || file == null) {
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                recordingSeconds = 0,
                errorMessage = "Rekaman kependekan, coba lagi ya"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isRecording = false,
            recordingSeconds = 0,
            pendingVoiceFile = file,
            pendingVoiceDurationSeconds = duration
        )
    }

    /** Buang rekaman pending (user pencet tombol hapus di preview bar). */
    fun discardPendingVoice() {
        _uiState.value.pendingVoiceFile?.delete()
        _uiState.value = _uiState.value.copy(pendingVoiceFile = null, pendingVoiceDurationSeconds = 0)
    }

    /** Upload & kirim rekaman pending sebagai pesan voice di Chat Global. */
    fun sendVoiceNote() {
        val state = _uiState.value
        val file = state.pendingVoiceFile ?: return
        if (!state.isPremium) {
            notifyVoiceRequiresPremium()
            return
        }
        if (state.cooldownSeconds > 0 || state.isSendingVoice) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingVoice = true, errorMessage = null)
            try {
                val audioUrl = VoiceNoteUploader.uploadVoiceNote(file, firebaseUid)
                repository.sendVoiceMessage(
                    firebaseUid = firebaseUid,
                    username = state.displayUsername,
                    avatarUrl = state.displayAvatarUrl,
                    audioUrl = audioUrl,
                    durationSeconds = state.pendingVoiceDurationSeconds,
                    replyToId = state.replyTarget?.id,
                    replyToUsername = state.replyTarget?.username,
                    replyToMessage = state.replyTarget?.message
                )
                file.delete()
                _uiState.value = _uiState.value.copy(
                    isSendingVoice = false,
                    pendingVoiceFile = null,
                    pendingVoiceDurationSeconds = 0,
                    replyTarget = null
                )
                refreshMessages()
                startCooldown()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSendingVoice = false,
                    errorMessage = friendlyErrorMessage(e, "Gagal mengirim pesan suara")
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        resyncJob?.cancel()
        cooldownJob?.cancel()
        recordingTimerJob?.cancel()
        voiceRecorder?.cancel()
        _uiState.value.pendingVoiceFile?.delete()
    }
}
