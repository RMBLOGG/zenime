package com.example.data.repository

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

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
class AuthRepository(
    private val chatRepository: ChatRepository = ChatRepository()
) {

    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow(firebaseAuth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    // Scope umur-panjang khusus buat ensureProfile di listener bawah --
    // addAuthStateListener BUKAN suspend function, jadi butuh scope sendiri
    // buat manggil suspend fun ensureProfile. AuthRepository sendiri
    // di-`remember` sekali di root NavGraph (praktis singleton seumur app),
    // jadi scope ini juga aman idup selama itu.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser
            val user = auth.currentUser
            if (user != null) {
                // Pastiin baris chat_profiles ADA dari saat ini juga (login),
                // BUKAN nunggu user buka Profil/Chat sendiri -- biar leaderboard
                // XP (yang basis-nya chat_profiles) nyakup SEMUA user yang
                // pernah login, gak cuma yang aktif di Profil/Chat.
                val defaultUsername = user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "User${user.uid.take(6)}"
                scope.launch {
                    chatRepository.ensureProfile(
                        firebaseUid = user.uid,
                        defaultUsername = defaultUsername,
                        defaultAvatarUrl = user.photoUrl?.toString()
                    )
                }
            }
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
            // Dibungkus withTimeout: TANPA ini, di koneksi lemot/putus-nyambung
            // getCredential() atau signInWithCredential().await() bisa nyangkut
            // tanpa batas waktu -- itu penyebab tombol "Menghubungkan..." macet
            // permanen yang dilaporkan user (reinstall gak ngaruh krn ini bukan
            // masalah state lokal, tapi network call yang gak pernah "nyerah").
            // 20 detik dipilih biar cukup toleran buat koneksi lambat tapi
            // gak bikin user nunggu kelamaan kalau memang gagal connect.
            withTimeout(20_000) {
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
                    ?: return@withTimeout Result.failure(IllegalStateException("Login berhasil tapi data user kosong"))
                Result.success(user)
            }
        } catch (e: TimeoutCancellationException) {
            // Koneksi kelamaan gak respons -- gagalin dengan pesan jelas
            // daripada biarin spinner nyangkut selamanya.
            Result.failure(
                Exception("Koneksi timeout, sinyal internet kamu kemungkinan lemah. Coba lagi di jaringan yang lebih stabil.")
            )
        } catch (e: NoCredentialException) {
            // Sistem gak nemu akun Google SAMA SEKALI di HP ini. Ini bukan
            // masalah config di sisi kita (SHA-1/OAuth client) -- itu bakal
            // muncul sebagai error lain. Ini murni: gak ada akun Google
            // ke-daftar di HP tsb, jadi gak ada yang bisa dipilih.
            Result.failure(
                Exception("Tidak ada akun Google di HP ini. Buka Pengaturan HP > Akun, tambahkan akun Google, lalu coba login lagi.")
            )
        } catch (e: GetCredentialProviderConfigurationException) {
            // Google Play Services gak ke-install / versinya kadaluarsa /
            // gak kompatibel di HP ini (umum di HP custom ROM/tanpa GMS).
            Result.failure(
                Exception("Google Play Services di HP ini bermasalah atau belum diperbarui. Update dulu lewat Play Store, lalu coba lagi.")
            )
        } catch (e: GetCredentialCancellationException) {
            // User nutup sheet pilih akun sendiri -- bukan error, jangan
            // ditampilin sebagai pesan error yang bikin bingung.
            Result.failure(Exception("Login dibatalkan."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}
