package com.example.ui.screens.login

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel buat LoginScreen (gerbang wajib sebelum masuk app).
 *
 * Sengaja terima instance AuthRepository yang sama dari luar (bukan bikin
 * baru), soalnya ZenimeAppNavHost juga observe currentUser dari instance itu
 * buat mutusin navigasi. Kalau tiap screen bikin AuthRepository sendiri2,
 * masing-masing tetap nyambung ke FirebaseAuth.getInstance() yang sama kok,
 * tapi mending satu sumber biar konsisten & gampang di-test.
 *
 * Poster backdrop-nya TIDAK ambil dari AnimeRepository/getHome() lagi --
 * lihat LoginBackdropPosters buat alasannya (intinya: biar user yang baru
 * install pun langsung lihat poster asli tanpa nunggu network sama sekali).
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val adminRepository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError

    val posterUrls: List<String> = LoginBackdropPosters.urls

    // --- State khusus form email (Daftar / Masuk manual) ---
    private val _emailFormMode = MutableStateFlow(EmailFormMode.HIDDEN)
    val emailFormMode: StateFlow<EmailFormMode> = _emailFormMode

    private val _emailFormMessage = MutableStateFlow<String?>(null)
    val emailFormMessage: StateFlow<String?> = _emailFormMessage

    private val _isEmailFormLoading = MutableStateFlow(false)
    val isEmailFormLoading: StateFlow<Boolean> = _isEmailFormLoading

    // Nyimpen email/password terakhir yang dicoba, dipake buat tombol
    // "Kirim ulang email verifikasi" tanpa user perlu ngetik ulang.
    private var lastAttemptedEmail: String = ""
    private var lastAttemptedPassword: String = ""

    // Nyala cuma pas signInWithEmail gagal spesifik karena belum verified.
    private val _showResendVerification = MutableStateFlow(false)
    val showResendVerification: StateFlow<Boolean> = _showResendVerification

    fun setEmailFormMode(mode: EmailFormMode) {
        _emailFormMode.value = mode
        _emailFormMessage.value = null
        _showResendVerification.value = false
    }

    fun signUpWithEmail(username: String, email: String, password: String) {
        if (_isEmailFormLoading.value) return
        viewModelScope.launch {
            _isEmailFormLoading.value = true
            _emailFormMessage.value = null
            _showResendVerification.value = false
            val result = authRepository.signUpWithEmail(username, email, password)
            result.onSuccess {
                _emailFormMessage.value =
                    "Berhasil daftar! Cek email $email buat verifikasi akun, baru bisa masuk."
                _emailFormMode.value = EmailFormMode.SIGN_IN
            }
            result.onFailure { error ->
                _emailFormMessage.value = error.message ?: "Daftar gagal, coba lagi."
            }
            _isEmailFormLoading.value = false
        }
    }

    fun signInWithEmail(context: Context, email: String, password: String, onSuccess: () -> Unit) {
        if (_isEmailFormLoading.value) return
        lastAttemptedEmail = email
        lastAttemptedPassword = password
        viewModelScope.launch {
            _isEmailFormLoading.value = true
            _emailFormMessage.value = null
            _showResendVerification.value = false
            val result = authRepository.signInWithEmail(email, password)
            result.onSuccess {
                @SuppressLint("HardwareIds")
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: ""
                val banResult = adminRepository.checkBan(deviceId).getOrNull()
                if (banResult?.banned == true) {
                    authRepository.signOut()
                    _emailFormMessage.value = banResult.reason ?: "Akun/perangkat ini diblokir."
                } else {
                    onSuccess()
                }
            }
            result.onFailure { error ->
                _emailFormMessage.value = error.message ?: "Login gagal, coba lagi."
                if (error.message?.contains("belum diverifikasi") == true) {
                    _showResendVerification.value = true
                }
            }
            _isEmailFormLoading.value = false
        }
    }

    fun resendVerificationEmail() {
        if (_isEmailFormLoading.value || lastAttemptedEmail.isEmpty()) return
        viewModelScope.launch {
            _isEmailFormLoading.value = true
            val result = authRepository.resendVerificationEmail(lastAttemptedEmail, lastAttemptedPassword)
            result.onSuccess {
                _emailFormMessage.value = "Email verifikasi udah dikirim ulang, cek inbox kamu."
                _showResendVerification.value = false
            }
            result.onFailure { error ->
                _emailFormMessage.value = error.message ?: "Gagal kirim ulang, coba lagi."
            }
            _isEmailFormLoading.value = false
        }
    }

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _isSigningIn.value = true
            _loginError.value = null
            val result = authRepository.signInWithGoogle(context)
            result.onSuccess {
                // Cek akun/device lagi diban atau nggak SEBELUM masuk Home --
                // Android ID dipakai sebagai device_id (nempel walau app
                // di-uninstall/install ulang, beda sama Firebase Installation
                // ID yang reset tiap install ulang -- lihat catatan di
                // zenime-check-ban).
                @SuppressLint("HardwareIds")
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: ""
                val banResult = adminRepository.checkBan(deviceId).getOrNull()
                if (banResult?.banned == true) {
                    authRepository.signOut()
                    _loginError.value = banResult.reason ?: "Akun/perangkat ini diblokir."
                } else {
                    onSuccess()
                }
            }
            result.onFailure { error ->
                _loginError.value = error.message ?: "Login Google gagal, coba lagi."
            }
            _isSigningIn.value = false
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }
}

enum class EmailFormMode { HIDDEN, SIGN_IN, SIGN_UP }
