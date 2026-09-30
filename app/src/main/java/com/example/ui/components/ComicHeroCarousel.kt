package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.model.BacakomikListItem
import com.example.ui.theme.StarYellow
import com.example.ui.theme.ZenimePrimary
import com.example.util.comicImageRequest
import kotlinx.coroutines.delay

/**
 * Carousel "Unggulan" di paling atas daftar komik: cover besar + judul +
 * chapter terbaru, geser otomatis tiap beberapa detik (berhenti kalau
 * lagi digeser manual).
 */
@Composable
fun ComicHeroCarousel(
    items: List<BacakomikListItem>,
    onComicClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(items.first().slug, items.size) {
        while (true) {
            delay(4500)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(end = 28.dp), // ngintip kartu berikutnya
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val comic = items[page]
            HeroCard(comic = comic, onClick = { onComicClick(comic.slug) })
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { index ->
                val selected = pagerState.currentPage == index
                val dotWidth by animateDpAsState(
                    targetValue = if (selected) 18.dp else 6.dp,
                    label = "heroDot"
                )
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(if (selected) ZenimePrimary else Color.White.copy(alpha = 0.25f))
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    comic: BacakomikListItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .testTag("comic_hero_${comic.slug}")
    ) {
        AsyncImage(
            model = comicImageRequest(LocalContext.current, comic.cover),
            contentDescription = comic.title,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color(0xFF0B0E14).copy(alpha = 0.25f),
                        1f to Color(0xFF0B0E14).copy(alpha = 0.95f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                comic.type?.takeIf { it.isNotBlank() }?.let { type ->
                    Surface(shape = RoundedCornerShape(6.dp), color = comicTypeColor(type)) {
                        Text(
                            text = type.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                comic.rating?.takeIf { it.isNotBlank() }?.let { rating ->
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = StarYellow,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = comic.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = listOfNotNull(
                        comic.chapter?.takeIf { it.isNotBlank() }?.let { compactChapterLabel(it) },
                        comic.date?.takeIf { it.isNotBlank() }
                    ).joinToString("  •  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = CircleShape, color = ZenimePrimary) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Text(
                            text = "Baca",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
