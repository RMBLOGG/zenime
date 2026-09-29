package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.AnichinNetwork
import com.example.data.model.AnichinCard

private val RankGold = Color(0xFFFFB300)

/**
 * Section "Donghua" di Beranda gaya bento: #1 besar di kiri, #2 & #3
 * bertumpuk di kanan. Menggantikan DonghuaEntryBanner.
 * Data: 3 kartu pertama dari Anichin (belum ada rating/views di API-nya).
 */
@Composable
fun DonghuaHotSection(
    cards: List<AnichinCard>,
    onCardClick: (String) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val top = cards.take(3)
    if (top.isEmpty()) return

    Column(modifier = modifier.padding(vertical = 10.dp).testTag("donghua_hot_section")) {
        SectionHeader(title = "Donghua", onSeeAllClick = onSeeAllClick)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HotDonghuaCard(
                rank = 1,
                card = top[0],
                onClick = { top[0].slug?.let(onCardClick) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                top.drop(1).forEachIndexed { i, card ->
                    HotDonghuaCard(
                        rank = i + 2,
                        card = card,
                        onClick = { card.slug?.let(onCardClick) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                // Kalau data cuma 2, sisakan ruang biar kartu #2 tidak melar
                if (top.size == 2) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HotDonghuaCard(
    rank: Int,
    card: AnichinCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = ImageRequest.Builder(context)
        .data(AnichinNetwork.imageUrl(card.thumbnail))
        .addHeader("User-Agent", AnichinNetwork.USER_AGENT)
        .addHeader("Referer", AnichinNetwork.sourceBase + "/")
        .crossfade(true)
        .build()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .testTag("donghua_hot_card_$rank")
            .clickable { onClick() }
    ) {
        // Backdrop: poster yang sama, diburamkan (Android 12+) dan digelapkan
        val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Modifier.blur(18.dp) else Modifier
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(blurModifier).alpha(0.55f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp, top = 40.dp, bottom = 12.dp)
        ) {
            // Poster
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
                card.eps?.let {
                    Text(
                        text = "Eps $it",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                card.type?.takeIf { it.isNotBlank() && it != "Unknown" }?.let { type ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) {
                        Text(
                            text = type,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp, fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = card.title ?: card.headline ?: "Tanpa Judul",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = if (rank == 1) 14.sp else 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Badge peringkat
        Surface(
            shape = RoundedCornerShape(bottomEnd = 16.dp),
            color = RankGold,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF1A1200),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}
