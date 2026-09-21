package com.example.ui.screens.donation

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.ZenimeHeader
import com.example.ui.components.ZenimeHeaderActionButton
import com.example.ui.components.ZenimeScreenTitle
import com.example.ui.theme.ZenimePrimary

/**
 * Halaman SociaBuzz di dalam aplikasi (WebView) -- alasannya satu: kolom pesan
 * bisa DIISI OTOMATIS dengan kode Zenime user, jadi donasinya nyambung ke akun
 * tanpa user perlu tempel manual.
 *
 * SociaBuzz gak punya parameter URL resmi buat isi pesan, jadi pengisiannya
 * lewat script kecil yang jalan setelah halaman kebuka (lihat [buildFillScript]).
 * Sifatnya best-effort: kalau SociaBuzz ngubah halamannya dan kolom pesan gak
 * kebaca, user tetap bisa tempel manual (kode juga sudah disalin ke clipboard
 * oleh DonationScreen) atau buka lewat browser lewat tombol di pojok kanan atas.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SociaBuzzDonateScreen(
    url: String,
    zenimeCode: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }

    // Back: mundur di riwayat WebView dulu (mis. dari halaman pembayaran), baru keluar.
    BackHandler {
        val current = webView
        if (current != null && current.canGoBack()) current.goBack() else onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.let {
                it.stopLoading()
                it.destroy()
            }
            webView = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
        topBar = {
            ZenimeHeader(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        ZenimeScreenTitle(title = "Donasi via SociaBuzz")
                    }
                },
                actions = {
                    ZenimeHeaderActionButton(
                        icon = Icons.Filled.OpenInBrowser,
                        contentDescription = "Buka di browser",
                        onClick = {
                            val target = webView?.url ?: url
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak ada browser untuk membuka link ini", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = ZenimePrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            // Link non-http (deep link e-wallet: gojek://, shopeepay://, intent://, dst)
                            // dibuka lewat aplikasinya, bukan di WebView.
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val target = request?.url ?: return false
                                val scheme = target.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") return false
                                try {
                                    val intent = if (scheme == "intent") {
                                        Intent.parseUri(target.toString(), Intent.URI_INTENT_SCHEME)
                                    } else {
                                        Intent(Intent.ACTION_VIEW, target)
                                    }
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        ctx,
                                        "Aplikasi untuk membuka link ini tidak ditemukan",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                val code = zenimeCode
                                if (view == null || code.isNullOrBlank() || pageUrl == null) return
                                // Script cuma jalan di halaman SociaBuzz, bukan di halaman gateway pembayaran.
                                val host = Uri.parse(pageUrl).host?.lowercase() ?: return
                                if (host == "sociabuzz.com" || host.endsWith(".sociabuzz.com")) {
                                    view.evaluateJavascript(buildFillScript(code), null)
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }

                        webView = this
                        loadUrl(url)
                    }
                }
            )
        }
    }
}

/**
 * Script yang ngisi kolom pesan (textarea) dengan kode Zenime.
 * - Nunggu textarea muncul (MutationObserver + cek berkala), karena form-nya dirender dinamis.
 * - Cuma ngisi kalau kolomnya masih kosong dan cuma SEKALI per kolom, jadi kalau
 *   user hapus/ubah isinya, gak diisi ulang.
 * - Textarea milik reCAPTCHA dan yang tersembunyi dilewati.
 * - Isi lewat setter bawaan browser + event input/change/keyup, supaya framework
 *   halaman (Vue/React) ikut sadar dan penghitung "0 / 250" ikut update.
 */
private fun buildFillScript(code: String): String {
    val quoted = org.json.JSONObject.quote(code)
    return """
        (function () {
          var CODE = $quoted;
          if (window.__zenimeFillInstalled) { return; }
          window.__zenimeFillInstalled = true;

          function setValue(el, value) {
            var desc = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
            if (desc && desc.set) { desc.set.call(el, value); } else { el.value = value; }
          }

          function fillMessage() {
            var areas = document.querySelectorAll('textarea');
            for (var i = 0; i < areas.length; i++) {
              var ta = areas[i];
              var tag = ((ta.name || '') + ' ' + (ta.id || '') + ' ' + (ta.className || '')).toLowerCase();
              if (tag.indexOf('recaptcha') !== -1) { continue; }
              if (ta.dataset.zenimeFilled === '1') { continue; }
              if (ta.getClientRects().length === 0) { continue; }
              ta.dataset.zenimeFilled = '1';
              if (ta.value && ta.value.trim().length > 0) { continue; }
              setValue(ta, CODE);
              ta.dispatchEvent(new Event('input', { bubbles: true }));
              ta.dispatchEvent(new Event('change', { bubbles: true }));
              ta.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
            }
          }

          fillMessage();
          new MutationObserver(fillMessage).observe(document.documentElement, { childList: true, subtree: true });
          setInterval(fillMessage, 1500);
        })();
    """.trimIndent()
}
