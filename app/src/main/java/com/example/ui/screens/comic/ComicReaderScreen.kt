package com.example.ui.screens.comic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.common.Result
import com.example.data.model.extractChapterLabel
import com.example.ui.components.ErrorStateView
import com.example.ui.theme.ZenimePrimary

@Composable
fun ComicReaderScreen(
    viewModel: ComicReaderViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.chapterState.collectAsStateWithLifecycle()
    val currentSlug by viewModel.currentSlug.collectAsStateWithLifecycle()
    var uiVisible by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val s = state) {
            is Result.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ZenimePrimary)
                }
            }
            is Result.Error -> {
                ErrorStateView(
                    message = s.message,
                    onRetry = { viewModel.loadChapter(currentSlug) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is Result.Success -> {
                val chapter = s.data
                val images = chapter.images.orEmpty()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { uiVisible = !uiVisible },
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(images) { imageUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Navigasi bawah -- selalu muncul di akhir list, biar user gak
                    // perlu balik ke atas buat pindah chapter.
                    item {
                        ChapterNavFooter(
                            hasNext = !chapter.navigation?.next.isNullOrBlank(),
                            hasPrev = !chapter.navigation?.prev.isNullOrBlank(),
                            onNext = { chapter.navigation?.next?.let { viewModel.loadChapter(it) } },
                            onPrev = { chapter.navigation?.prev?.let { viewModel.loadChapter(it) } }
                        )
                    }
                }

                // Top bar overlay -- fade in/out mengikuti tap di area baca.
                AnimatedVisibility(
                    visible = uiVisible,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                        Text(
                            text = chapter.title?.takeIf { it.isNotBlank() } ?: extractChapterLabel(currentSlug),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // Prev/Next mengambang -- juga fade sesuai uiVisible.
                AnimatedVisibility(
                    visible = uiVisible,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it },
                    exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { chapter.navigation?.prev?.let { viewModel.loadChapter(it) } },
                            enabled = !chapter.navigation?.prev.isNullOrBlank()
                        ) {
                            Icon(
                                Icons.Filled.NavigateBefore,
                                contentDescription = "Chapter Sebelumnya",
                                tint = if (chapter.navigation?.prev.isNullOrBlank()) Color.White.copy(alpha = 0.3f) else Color.White
                            )
                        }
                        Text(
                            text = chapter.title?.takeIf { it.isNotBlank() } ?: extractChapterLabel(currentSlug),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                        IconButton(
                            onClick = { chapter.navigation?.next?.let { viewModel.loadChapter(it) } },
                            enabled = !chapter.navigation?.next.isNullOrBlank()
                        ) {
                            Icon(
                                Icons.Filled.NavigateNext,
                                contentDescription = "Chapter Berikutnya",
                                tint = if (chapter.navigation?.next.isNullOrBlank()) Color.White.copy(alpha = 0.3f) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterNavFooter(
    hasNext: Boolean,
    hasPrev: Boolean,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (hasPrev) {
            Button(
                onClick = onPrev,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.NavigateBefore, contentDescription = null)
                Text("Sebelumnya")
            }
        }
        if (hasNext) {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Selanjutnya")
                Icon(Icons.Filled.NavigateNext, contentDescription = null)
            }
        }
    }
}
