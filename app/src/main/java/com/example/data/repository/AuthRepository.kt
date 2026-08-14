package com.example.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Bungkus login Google lewat Credential Manager (API resmi Google yang
 * gantiin GoogleSignInClient lama yang udah deprecated) + Firebase Auth buat
 * nyimpen sesi login-nya.
 *
 * PENTING: ini BUTUH file google-services.json dari project Firebase kamu
 * sendiri di folder app/, plus Web Client ID dari situ (otomatis diisi ke
 * R.string.default_web_client_id pas build kalau file-nya ada). Tanpa itu,
 * kode ini compile tapi signInWithGoogle() bakal selalu gagal di runtime.
 * Lihat catatan setup di README / pesan chat.
 */
class AuthRepository {

    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow(firebaseAuth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
        }
    }

    /**
     * Munculin sheet pilih akun Google bawaan sistem, tukar token-nya ke
     * Firebase Auth. Harus dipanggil dari coroutine yang scope-nya ngikutin
     * lifecycle Activity/Composable (misal viewModelScope), soalnya
     * CredentialManager butuh Activity context yang hidup.
     */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        val webClientId = context.getString(R.string.google_web_client_id)
        if (webClientId == "REPLACE_WITH_YOUR_WEB_CLIENT_ID") {
            return Result.failure(
                IllegalStateException(
                    "Google Sign-In belum di-setup: isi google_web_client_id di strings.xml dan " +
                        "tambahin google-services.json dari project Firebase kamu."
                )
            )
        }

        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                // false = tampilin SEMUA akun Google di device buat dipilih,
                // bukan cuma yang pernah dipakai login ke app ini sebelumnya.
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()

            val user = authResult.user
                ?: return Result.failure(IllegalStateException("Login berhasil tapi data user kosong"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}
