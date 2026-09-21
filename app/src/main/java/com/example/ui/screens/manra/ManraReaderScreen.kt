package com.example.ui.screens.manra

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.ZenimePrimary
import kotlinx.coroutines.delay

/**
 * Pembaca Manra bergaya visual novel: latar gambar, karakter (NPC), kotak
 * dialog dengan efek ketik, dan pilihan. Ketuk layar: kalau teks belum habis
 * diketik -> langsung tampil penuh; kalau sudah -> lanjut ke baris berikutnya.
 */
@Composable
fun ManraReaderScreen(
    viewModel: ManraReaderViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val line = state.current

    // Latar = gambar latar terakhir yang muncul sampai baris ini.
    val background: String? = remember(state.lines, state.index) {
        (state.index downTo 0).firstNotNullOfOrNull { i -> state.lines.getOrNull(i)?.imageBg }
    }

    // Efek ketik: jumlah karakter yang sudah tampil untuk baris aktif.
    val fullText = line?.text.orEmpty()
    var revealed by remember(line?.id) { mutableIntStateOf(0) }
    LaunchedEffect(line?.id) {
        revealed = 0
        while (revealed < fullText.length) {
            delay(18)
            revealed += 1
        }
    }

    val onTap: () -> Unit = {
        if (line != null && !line.isChoice && !state.finished) {
            if (revealed < fullText.length) revealed = fullText.length else viewModel.next()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        // 1) Latar
        Crossfade(targetState = background, animationSpec = tween(350), label = "manra-bg") { bg ->
            if (bg != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(bg).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF10131A)))
            }
        }

        // 2) Karakter
        Crossfade(targetState = line?.npcImage, animationSpec = tween(250), label = "manra-npc") { url ->
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                    contentDescription = line?.npcName,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 120.dp)
                )
            }
        }

        // 3) Gradasi bawah supaya kotak dialog terbaca
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // 4) Dialog
        if (line != null && !line.isChoice && fullText.isNotBlank()) {
            DialogBox(
                name = line.npcName,
                text = fullText.take(revealed),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 5) Pilihan
        if (line != null && line.isChoice) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (fullText.isNotBlank()) {
                    Text(
                        text = fullText,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                line.choices.forEach { choice ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(enabled = !state.isBusy) { viewModel.choose(choice) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = choice.text,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                if (state.isBusy) {
                    CircularProgressIndicator(
                        color = ZenimePrimary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // 6) Bar atas: kembali + posisi
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.lines.isNotEmpty()) {
                Text(
                    text = "${state.index + 1} / ${state.lines.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // 7) Memuat / galat / selesai
        when {
            state.isLoading -> CircularProgressIndicator(
                color = ZenimePrimary,
                modifier = Modifier.align(Alignment.Center)
            )
            state.error != null -> CenterMessage(
                text = state.error.orEmpty(),
                actionLabel = "Coba lagi",
                onAction = { viewModel.load() },
                modifier = Modifier.align(Alignment.Center)
            )
            state.finished -> CenterMessage(
                text = "Chapter selesai",
                actionLabel = "Kembali",
                onAction = onBackClick,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun DialogBox(
    name: String?,
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        if (!name.isNullOrBlank()) {
            Text(
                text = name,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(ZenimePrimary)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = if (name.isNullOrBlank()) 14.dp else 0.dp,
                        topEnd = 14.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp
                    )
                )
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun CenterMessage(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = actionLabel,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ZenimePrimary)
                .clickable(onClick = onAction)
                .padding(horizontal = 22.dp, vertical = 10.dp)
        )
    }
}
