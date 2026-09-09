package com.example.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AnimeRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.PremiumRepository
import com.example.data.repository.PremiumStatus
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: AnimeRepository,
    private val authRepository: AuthRepository = AuthRepository(),
    private val premiumRepository: PremiumRepository = PremiumRepository(),
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = authRepository.currentUser.value
        )

    // Username & avatar custom (diset lewat "Edit Profil" di Chat Global) --
    // null berarti belum pernah diganti, jadi Settings tetap nampilin nama
    // & foto akun Google apa adanya. Ikut ke-refresh tiap ganti akun.
    private val _displayName = MutableStateFlow<String?>(null)
    val displayName: StateFlow<String?> = _displayName

    private val _displayAvatarUrl = MutableStateFlow<String?>(null)
    val displayAvatarUrl: StateFlow<String?> = _displayAvatarUrl

    // Status premium akun yang lagi login -- null berarti belum dicek/lagi
    // dicek/user belum login. Otomatis diambil ulang tiap currentUser
    // berubah (login/logout/ganti akun).
    private val _premiumStatus = MutableStateFlow<PremiumStatus?>(null)
    val premiumStatus: StateFlow<PremiumStatus?> = _premiumStatus

    init {
        viewModelScope.launch {
            currentUser.collectLatest { user ->
                _premiumStatus.value = null
                _displayName.value = null
                _displayAvatarUrl.value = null
                if (user != null) {
                    premiumRepository.checkPremiumStatus(user.uid)
                        .onSuccess { status -> _premiumStatus.value = status }
                    loadProfileOverride(user.uid)
                }
            }
        }
    }

    private suspend fun loadProfileOverride(uid: String) {
        val profile = try {
            chatRepository.getProfile(uid)
        } catch (e: Exception) {
            null
        }
        if (profile != null) {
            _displayName.value = profile.username.ifBlank { null }
            _displayAvatarUrl.value = profile.avatarUrl
        }
    }

    /** Dipanggil pas Settings resume, buat nangkep perubahan username/avatar yang barusan diedit di Chat Global. */
    fun refreshProfileOverride() {
        val uid = currentUser.value?.uid ?: return
        viewModelScope.launch { loadProfileOverride(uid) }
    }

    // Status login lagi diproses -- dipakai buat nampilin loading & nyegah
    // dobel-tap tombol login pas request masih jalan.
    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn

    // Pesan error login (misal user nutup sheet pemilihan akun, atau
    // google-services.json belum di-setup) -- null kalau gak ada error.
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError

    fun signInWithGoogle(context: Context) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _isSigningIn.value = true
            _loginError.value = null
            val result = authRepository.signInWithGoogle(context)
            result.onFailure { error ->
                _loginError.value = error.message ?: "Login Google gagal, coba lagi."
            }
            _isSigningIn.value = false
        }
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    val themeMode: StateFlow<String> = repository.userPrefs.themeModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "DARK"
        )

    val dynamicColor: StateFlow<Boolean> = repository.userPrefs.dynamicColorFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val defaultQuality: StateFlow<String> = repository.userPrefs.defaultQualityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "720p"
        )

    val autoSkipIntro: StateFlow<Boolean> = repository.userPrefs.autoSkipIntroFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val autoSkipOutro: StateFlow<Boolean> = repository.userPrefs.autoSkipOutroFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val heroStyle: StateFlow<String> = repository.userPrefs.heroStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "FULL_BLEED"
        )

    val heroAutoplay: StateFlow<Boolean> = repository.userPrefs.heroAutoplayFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val heroIntervalMs: StateFlow<Int> = repository.userPrefs.heroIntervalMsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 4500
        )

    val heroItemCount: StateFlow<Int> = repository.userPrefs.heroItemCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 6
        )

    val heroSource: StateFlow<String> = repository.userPrefs.heroSourceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "AUTO"
        )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.userPrefs.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.userPrefs.setDynamicColor(enabled)
        }
    }

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch {
            repository.userPrefs.setDefaultQuality(quality)
        }
    }

    fun setAutoSkipIntro(enabled: Boolean) {
        viewModelScope.launch {
            repository.userPrefs.setAutoSkipIntro(enabled)
        }
    }

    fun setAutoSkipOutro(enabled: Boolean) {
        viewModelScope.launch {
            repository.userPrefs.setAutoSkipOutro(enabled)
        }
    }

    fun setHeroStyle(style: String) {
        viewModelScope.launch {
            repository.userPrefs.setHeroStyle(style)
        }
    }

    fun setHeroAutoplay(enabled: Boolean) {
        viewModelScope.launch {
            repository.userPrefs.setHeroAutoplay(enabled)
        }
    }

    fun setHeroIntervalMs(intervalMs: Int) {
        viewModelScope.launch {
            repository.userPrefs.setHeroIntervalMs(intervalMs)
        }
    }

    fun setHeroItemCount(count: Int) {
        viewModelScope.launch {
            repository.userPrefs.setHeroItemCount(count)
        }
    }

    fun setHeroSource(source: String) {
        viewModelScope.launch {
            repository.userPrefs.setHeroSource(source)
        }
    }
}

/** Format "Aktif hingga dd MMM yyyy (sisa X hari)" dari expires_at ISO string. */
fun formatPremiumRemaining(expiresAtIso: String): String {
    return try {
        val expiresAt = java.time.Instant.parse(expiresAtIso)
        val now = java.time.Instant.now()
        val daysLeft = java.time.Duration.between(now, expiresAt).toDays()
        val formatter = java.time.format.DateTimeFormatter
            .ofPattern("d MMM yyyy", java.util.Locale("id", "ID"))
            .withZone(java.time.ZoneId.systemDefault())
        val dateText = formatter.format(expiresAt)
        when {
            daysLeft < 0 -> "Sudah berakhir"
            daysLeft == 0L -> "Aktif hingga $dateText (berakhir hari ini)"
            else -> "Aktif hingga $dateText (sisa $daysLeft hari)"
        }
    } catch (e: Exception) {
        "Premium aktif"
    }
}
