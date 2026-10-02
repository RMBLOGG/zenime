package com.example.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.theme.ZenimeBackgroundDark
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Catatan desain
// - Warna di file ini SENGAJA hardcode (brand Zenime), bukan MaterialTheme.colorScheme,
//   biar tampilan gak berubah ikut Material You / dynamic color.
// - Tiap slide punya pasangan warna aksen sendiri; background, indikator, dan tombol
//   ikut berubah mulus (lerp) waktu di-swipe.
// - Semua ilustrasi digambar pakai Compose (tanpa aset gambar tambahan).
// ─────────────────────────────────────────────────────────────────────────────

private val Stage = 300.dp
private val BackOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val Gold = Color(0xFFFFB020)

private data class Slide(
    val title: String,
    val highlight: String,
    val desc: String,
    val accentA: Color,
    val accentB: Color
)

private val slides = listOf(
    Slide(
        title = "Nonton Anime Tanpa Batas",
        highlight = "Tanpa Batas",
        desc = "Ribuan judul favoritmu siap ditonton langsung dari HP Android, kapan aja.",
        accentA = Color(0xFFE4344A),
        accentB = Color(0xFF9333EA)
    ),
    Slide(
        title = "Download & Nonton Offline",
        highlight = "Offline",
        desc = "Simpan episode favorit, terus tonton tanpa kuota di mana pun kamu berada.",
        accentA = Color(0xFF3B82F6),
        accentB = Color(0xFF8B5CF6)
    ),
    Slide(
        title = "Ngobrol Bareng, Gabung Clan",
        highlight = "Clan",
        desc = "Chat Global, tambah teman, dan bikin Clan bareng sesama penonton.",
        accentA = Color(0xFFA855F7),
        accentB = Color(0xFFEC4899)
    ),
    Slide(
        title = "Naik Level, Jadi Legenda",
        highlight = "Legenda",
        desc = "Kumpulin XP tiap nonton dan panjat Leaderboard bareng yang lain.",
        accentA = Color(0xFFF59E0B),
        accentB = Color(0xFFE4344A)
    )
)

private val PagerState.position: Float
    get() = currentPage + currentPageOffsetFraction

private fun accentAt(pos: Float, pick: (Slide) -> Color): Color {
    val i = floor(pos).toInt().coerceIn(0, slides.lastIndex)
    val j = (i + 1).coerceAtMost(slides.lastIndex)
    return lerp(pick(slides[i]), pick(slides[j]), (pos - i).coerceIn(0f, 1f))
}

private fun Slide.titleText() = buildAnnotatedString {
    val i = title.indexOf(highlight)
    if (i < 0) {
        append(title)
    } else {
        append(title.substring(0, i))
        withStyle(SpanStyle(brush = Brush.linearGradient(listOf(accentA, accentB)))) {
            append(highlight)
        }
        append(title.substring(i + highlight.length))
    }
}

/**
 * Alur intro: slide onboarding -> SplashScreen yang udah ada -> [onFinished].
 * Panggil [onFinished] di sini buat nyimpen flag "sudah lihat intro".
 */
@Composable
fun OnboardingFlow(onFinished: () -> Unit) {
    var showSplash by rememberSaveable { mutableStateOf(false) }
    Crossfade(
        targetState = showSplash,
        animationSpec = tween(500),
        label = "onboardingToSplash"
    ) { splash ->
        if (splash) {
            SplashScreen(onSplashFinished = onFinished)
        } else {
            OnboardingPager(onDone = { showSplash = true })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout utama
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingPager(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == slides.lastIndex

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZenimeBackgroundDark)
    ) {
        AuroraBackground(pagerState)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            TopBar(isLast = isLast, onSkip = onDone)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPage(page = page, pagerState = pagerState)
            }

            PageIndicator(pagerState)
            Spacer(Modifier.height(20.dp))
            CtaButton(
                pagerState = pagerState,
                isLast = isLast,
                onClick = {
                    if (isLast) onDone()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TopBar(isLast: Boolean, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.zenime_wordmark),
            contentDescription = "Zenime",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(40.dp)
                .aspectRatio(3f)
        )
        AnimatedVisibility(
            visible = !isLast,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Lewati",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: Int, pagerState: PagerState) {
    val slide = slides[page]
    val active = pagerState.currentPage == page

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Skala turun otomatis di layar kecil biar ilustrasi gak kepotong.
            val fit = minOf(1f, maxWidth / Stage, maxHeight / Stage)
            Box(
                modifier = Modifier
                    .requiredSize(Stage)
                    .graphicsLayer {
                        // Parallax: ilustrasi gerak lebih pelan dari teks.
                        val off = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val k = 1f - abs(off).coerceIn(0f, 1f)
                        translationX = off * size.width * 0.35f
                        alpha = k
                        val s = fit * (0.85f + 0.15f * k)
                        scaleX = s
                        scaleY = s
                    }
            ) {
                when (page) {
                    0 -> PosterStackIllustration(active, slide.accentA, slide.accentB)
                    1 -> OfflineIllustration(active, slide.accentA, slide.accentB)
                    2 -> ChatIllustration(active, slide.accentA, slide.accentB)
                    else -> LevelIllustration(active, slide.accentA, slide.accentB)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = slide.titleText(),
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.slideIn(active, 0)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = slide.desc,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.slideIn(active, 120)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PageIndicator(pagerState: PagerState) {
    val pos = pagerState.position
    val accent = accentAt(pos) { it.accentA }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(slides.size) { i ->
            val near = (1f - abs(pos - i)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(8.dp)
                    .width(8.dp + 22.dp * near)
                    .clip(CircleShape)
                    .background(lerp(Color.White.copy(alpha = 0.2f), accent, near))
            )
        }
    }
}

@Composable
private fun CtaButton(pagerState: PagerState, isLast: Boolean, onClick: () -> Unit) {
    val pos = pagerState.position
    val a = accentAt(pos) { it.accentA }
    val b = accentAt(pos) { it.accentB }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ctaPress"
    )
    val inf = rememberInfiniteTransition(label = "cta")
    val sheen = inf.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "sheen"
    )
    val nudge = inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nudge"
    )
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(60.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = 20.dp,
                shape = shape,
                ambientColor = a.copy(alpha = 0.6f),
                spotColor = a.copy(alpha = 0.6f)
            )
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(a, b)))
            .drawWithContent {
                drawContent()
                val x = sheen.value * size.width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        start = Offset(x - size.width * 0.25f, 0f),
                        end = Offset(x + size.width * 0.25f, size.height)
                    )
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = isLast,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 90)) + slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(90)) + slideOutVertically { -it / 2 })
                },
                label = "ctaText"
            ) { last ->
                Text(
                    text = if (last) "Mulai Sekarang" else "Lanjut",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { translationX = nudge.value * 4.dp.toPx() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Background: aurora glow + bintang berkedip
// ─────────────────────────────────────────────────────────────────────────────

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val alpha: Float)

@Composable
private fun AuroraBackground(pagerState: PagerState) {
    val stars = remember {
        val rnd = Random(11)
        List(36) {
            Star(
                x = rnd.nextFloat(),
                y = rnd.nextFloat(),
                r = 0.7f + rnd.nextFloat() * 1.6f,
                phase = rnd.nextFloat() * 6.2832f,
                alpha = 0.35f + rnd.nextFloat() * 0.6f
            )
        }
    }
    val inf = rememberInfiniteTransition(label = "bg")
    val drift = inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing)),
        label = "drift"
    )
    val twinkle = inf.animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "twinkle"
    )
    val breathe = inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Canvas(Modifier.fillMaxSize()) {
        val pos = pagerState.position
        val a = accentAt(pos) { it.accentA }
        val b = accentAt(pos) { it.accentB }
        val w = size.width
        val h = size.height

        drawRect(ZenimeBackgroundDark)

        val c1 = Offset(w * (0.30f + 0.08f * sin(pos * 1.7f)), h * 0.20f)
        val r1 = w * (0.95f + 0.10f * breathe.value)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(a.copy(alpha = 0.40f), Color.Transparent),
                center = c1,
                radius = r1
            ),
            radius = r1,
            center = c1
        )

        val c2 = Offset(w * (0.85f - 0.06f * sin(pos * 1.3f)), h * 0.72f)
        val r2 = w * (0.85f + 0.10f * (1f - breathe.value))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(b.copy(alpha = 0.32f), Color.Transparent),
                center = c2,
                radius = r2
            ),
            radius = r2,
            center = c2
        )

        stars.forEach { s ->
            val y = ((s.y - drift.value + 1f) % 1f) * h
            val tw = 0.5f + 0.5f * sin(twinkle.value + s.phase)
            drawCircle(
                color = Color.White,
                radius = s.r * density,
                center = Offset(s.x * w, y),
                alpha = s.alpha * (0.25f + 0.75f * tw)
            )
        }

        // Redam bagian bawah biar tombol & indikator tetap kebaca jelas.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, ZenimeBackgroundDark.copy(alpha = 0.85f)),
                startY = h * 0.6f,
                endY = h
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper modifier & komponen kecil
// ─────────────────────────────────────────────────────────────────────────────

private fun Modifier.glass(shape: Shape): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.05f))
        )
    )
    .border(1.dp, Color.White.copy(alpha = 0.15f), shape)

@Composable
private fun Modifier.floating(
    periodMs: Int,
    startOffsetMs: Int = 0,
    amplitude: Dp = 8.dp
): Modifier {
    val t = rememberInfiniteTransition(label = "float")
    val v = t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(startOffsetMs)
        ),
        label = "floatY"
    )
    return this.graphicsLayer { translationY = v.value * amplitude.toPx() }
}

@Composable
private fun Modifier.slideIn(active: Boolean, delayMs: Int): Modifier {
    val p = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = if (active) delayMs else 0,
            easing = FastOutSlowInEasing
        ),
        label = "slideIn"
    )
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 28.dp.toPx()
    }
}

@Composable
private fun Modifier.popIn(active: Boolean, delayMs: Int, originX: Float): Modifier {
    val p = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = 500,
            delayMillis = if (active) delayMs else 0,
            easing = BackOut
        ),
        label = "pop"
    )
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        val s = 0.6f + 0.4f * v
        scaleX = s
        scaleY = s
        translationY = (1f - v) * 18.dp.toPx()
        transformOrigin = TransformOrigin(originX, 1f)
    }
}

@Composable
private fun GlowOrb(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent)),
                CircleShape
            )
    )
}

@Composable
private fun FloatChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    periodMs: Int = 3200,
    startOffsetMs: Int = 0
) {
    Row(
        modifier = modifier
            .floating(periodMs, startOffsetMs, 7.dp)
            .glass(RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 1 — tumpukan poster anime
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PosterStackIllustration(active: Boolean, a: Color, b: Color) {
    val spread by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "spread"
    )
    Box(Modifier.size(Stage)) {
        GlowOrb(a, 280.dp, Modifier.align(Alignment.Center))

        PosterCard(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-84).dp * spread, y = 22.dp * spread)
                .graphicsLayer {
                    rotationZ = -13f * spread
                    scaleX = 0.86f
                    scaleY = 0.86f
                },
            sky = Color(0xFF1B1030),
            glow = b,
            seed = 1,
            showPlay = false,
            tint = b
        )
        PosterCard(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 84.dp * spread, y = 22.dp * spread)
                .graphicsLayer {
                    rotationZ = 13f * spread
                    scaleX = 0.86f
                    scaleY = 0.86f
                },
            sky = Color(0xFF2A0F18),
            glow = a,
            seed = 2,
            showPlay = false,
            tint = a
        )
        PosterCard(
            modifier = Modifier
                .align(Alignment.Center)
                .floating(3600, 0, 6.dp),
            sky = Color(0xFF140A2A),
            glow = lerp(a, b, 0.5f),
            seed = 3,
            showPlay = true,
            tint = a
        )
    }
}

@Composable
private fun PosterCard(
    modifier: Modifier,
    sky: Color,
    glow: Color,
    seed: Int,
    showPlay: Boolean,
    tint: Color
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .size(width = 148.dp, height = 210.dp)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = glow,
                spotColor = glow
            )
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) { drawPoster(sky, glow, seed) }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Box(
                Modifier
                    .height(8.dp)
                    .fillMaxWidth(0.7f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f))
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .height(6.dp)
                    .fillMaxWidth(0.45f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }
        if (showPlay) {
            PlayPulse(tint = tint, modifier = Modifier.align(Alignment.Center))
        }
    }
}

private fun DrawScope.drawPoster(sky: Color, glow: Color, seed: Int) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.verticalGradient(
            0f to sky,
            0.55f to glow,
            1f to Color(0xFF0B0E14)
        )
    )
    val sun = Offset(w * (0.36f + 0.28f * (seed % 2)), h * 0.34f)
    drawCircle(Color.White.copy(alpha = 0.18f), radius = w * 0.27f, center = sun)
    drawCircle(Color.White.copy(alpha = 0.92f), radius = w * 0.17f, center = sun)

    val rnd = Random(seed)
    repeat(7) {
        drawCircle(
            Color.White.copy(alpha = 0.7f),
            radius = 1.6f * density,
            center = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.4f)
        )
    }

    val back = Path().apply {
        moveTo(0f, h * 0.78f)
        lineTo(w * 0.28f, h * 0.52f)
        lineTo(w * 0.5f, h * 0.72f)
        lineTo(w * 0.74f, h * 0.46f)
        lineTo(w, h * 0.74f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(back, Color(0xFF0B0E14).copy(alpha = 0.55f))
    val front = Path().apply {
        moveTo(0f, h * 0.88f)
        lineTo(w * 0.22f, h * 0.68f)
        lineTo(w * 0.46f, h * 0.86f)
        lineTo(w * 0.7f, h * 0.64f)
        lineTo(w, h * 0.86f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(front, Color(0xFF0B0E14).copy(alpha = 0.85f))
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
            startY = h * 0.55f,
            endY = h
        )
    )
}

@Composable
private fun PlayPulse(tint: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p = t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing)),
        label = "pulseP"
    )
    Box(modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(54.dp)
                .graphicsLayer {
                    val s = 1f + p.value * 0.5f
                    scaleX = s
                    scaleY = s
                    alpha = (1f - p.value) * 0.6f
                }
                .border(2.dp, Color.White, CircleShape)
        )
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 2 — download offline (ring progress)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OfflineIllustration(active: Boolean, a: Color, b: Color) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            delay(450)
            progress.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
        }
    }
    val done = progress.value >= 1f

    Box(Modifier.size(Stage)) {
        GlowOrb(a, 280.dp, Modifier.align(Alignment.Center))

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(210.dp)
                .glass(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                val stroke = 14.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                rotate(-90f) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(a, b, a)),
                        startAngle = 0f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }

            Crossfade(targetState = done, label = "doneFade") { isDone ->
                if (isDone) {
                    val pop by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                        label = "checkPop"
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .graphicsLayer {
                                    scaleX = pop
                                    scaleY = pop
                                }
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(a, b))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tersimpan",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(progress.value * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Mengunduh",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        FloatChip(
            icon = Icons.Rounded.HighQuality,
            label = "1080p",
            tint = b,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 22.dp),
            periodMs = 3000
        )
        FloatChip(
            icon = Icons.Rounded.Movie,
            label = "Episode 12",
            tint = a,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 0.dp, y = 56.dp),
            periodMs = 3600,
            startOffsetMs = 500
        )
        FloatChip(
            icon = Icons.Rounded.WifiOff,
            label = "Tanpa Kuota",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-14).dp, y = (-18).dp),
            periodMs = 3400,
            startOffsetMs = 900
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 3 — chat & clan
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatIllustration(active: Boolean, a: Color, b: Color) {
    Box(Modifier.size(Stage)) {
        GlowOrb(b, 280.dp, Modifier.align(Alignment.Center))

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .offset(y = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChatBubble(0, active, "A", "Episode terbaru gila banget! 🔥", false, a, b)
            ChatBubble(1, active, "R", "Gabung Clan kita yuk!", false, a, b)
            ChatBubble(2, active, "", "Gas, join sekarang! ⚔️", true, a, b)
            Row(
                modifier = Modifier
                    .padding(start = 44.dp)
                    .popIn(active, 350 + 3 * 450, 0f)
            ) {
                TypingDots()
            }
        }

        FloatChip(
            icon = Icons.Rounded.Groups,
            label = "Clan Sakura",
            tint = a,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 6.dp),
            periodMs = 3200
        )
    }
}

@Composable
private fun ChatBubble(
    index: Int,
    active: Boolean,
    initial: String,
    text: String,
    mine: Boolean,
    a: Color,
    b: Color
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .popIn(active, 350 + index * 450, if (mine) 1f else 0f),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(a, b))),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 210.dp)
                .then(
                    if (mine) Modifier
                        .clip(bubbleShape)
                        .background(Brush.horizontalGradient(listOf(a, b)))
                    else Modifier.glass(bubbleShape)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .glass(RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(3) { i ->
            val v = t.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 160)
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        alpha = v.value
                        scaleX = 0.7f + 0.3f * v.value
                        scaleY = 0.7f + 0.3f * v.value
                    }
                    .background(Color.White, CircleShape)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Slide 4 — level & XP
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LevelIllustration(active: Boolean, a: Color, b: Color) {
    val bar = remember { Animatable(0.12f) }
    val burst = remember { Animatable(0f) }
    var leveledUp by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (active) {
            leveledUp = false
            burst.snapTo(0f)
            bar.snapTo(0.12f)
            delay(500)
            bar.animateTo(1f, tween(1700, easing = FastOutSlowInEasing))
            leveledUp = true
            burst.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
        }
    }

    val inf = rememberInfiniteTransition(label = "level")
    val ringRot = inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "ringRot"
    )
    val trophyScale by animateFloatAsState(
        targetValue = if (leveledUp) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow),
        label = "trophy"
    )

    Box(Modifier.size(Stage)) {
        GlowOrb(a, 280.dp, Modifier.align(Alignment.Center))

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    rotate(ringRot.value) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(a, Color.Transparent, b, Color.Transparent, a)
                            ),
                            radius = size.minDimension / 2f - 4.dp.toPx(),
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .glass(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(a.copy(alpha = 0.25f), b.copy(alpha = 0.25f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "LEVEL",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        AnimatedContent(
                            targetState = if (leveledUp) 2 else 1,
                            transitionSpec = {
                                (slideInVertically { it } + fadeIn()) togetherWith
                                    (slideOutVertically { -it } + fadeOut())
                            },
                            label = "levelNumber"
                        ) { n ->
                            Text(
                                text = "$n",
                                color = Color.White,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .graphicsLayer {
                            scaleX = trophyScale
                            scaleY = trophyScale
                        }
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Gold, Color(0xFFFF7A00)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = ZenimeBackgroundDark,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Percikan pas naik level.
                Canvas(Modifier.requiredSize(280.dp)) {
                    val colors = listOf(a, b, Gold, Color.White)
                    val count = 16
                    for (i in 0 until count) {
                        val angle = i * (6.2832f / count)
                        val dist = 70.dp.toPx() + burst.value * 75.dp.toPx()
                        drawCircle(
                            color = colors[i % colors.size],
                            radius = 5.dp.toPx() * (1f - burst.value) + 1f,
                            center = Offset(
                                center.x + cos(angle) * dist,
                                center.y + sin(angle) * dist
                            ),
                            alpha = (1f - burst.value).coerceIn(0f, 1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Column(Modifier.width(240.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "XP",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (leveledUp) "LEVEL UP!" else "${(bar.value * 200).toInt()} / 200 XP",
                        color = if (leveledUp) Gold else Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(bar.value)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(a, b)))
                    )
                }
            }
        }

        FloatChip(
            icon = Icons.Rounded.Bolt,
            label = "+20 XP",
            tint = Gold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 0.dp, y = 4.dp),
            periodMs = 3000
        )
        FloatChip(
            icon = Icons.Rounded.EmojiEvents,
            label = "Leaderboard",
            tint = Gold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 0.dp, y = 30.dp),
            periodMs = 3500,
            startOffsetMs = 600
        )
    }
}
