package com.example.ui.screens.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.repository.AnimeRepository
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
 * repository dipakai buat narik poster anime (dari data homepage yang udah
 * ke-cache) yang ditampilin sebagai backdrop grid di belakang tombol login.
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val repository: AnimeRepository
) : ViewModel() {

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError

    private val _posterUrls = MutableStateFlow<List<String>>(emptyList())
    val posterUrls: StateFlow<List<String>> = _posterUrls

    init {
        loadPosters()
    }

    private fun loadPosters() {
        viewModelScope.launch {
            repository.getHome().collect { result ->
                if (result is Result.Success) {
                    val urls = buildList {
                        result.data.popular?.forEach { it.image_poster?.let(::add) }
                        result.data.hot?.forEach { it.image_poster?.let(::add) }
                        result.data.new?.forEach { it.image_poster?.let(::add) }
                        result.data.random?.forEach { it.image_poster?.let(::add) }
                    }.distinct()

                    if (urls.isNotEmpty()) {
                        // Diulang biar grid tetep kepenuhan walau data awalnya dikit.
                        _posterUrls.value = (urls + urls + urls).take(24)
                    }
                }
            }
        }
    }

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit) {
        if (_isSigningIn.value) return
        viewModelScope.launch {
            _isSigningIn.value = true
            _loginError.value = null
            val result = authRepository.signInWithGoogle(context)
            result.onSuccess { onSuccess() }
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
