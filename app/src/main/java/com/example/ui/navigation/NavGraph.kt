package com.example.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.PremiumPackage
import kotlinx.coroutines.launch
import com.example.data.repository.AnimeRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ComicRepository
import com.example.data.repository.PremiumRepository
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.comic.ComicDetailScreen
import com.example.ui.screens.comic.ComicDetailViewModel
import com.example.ui.screens.comic.ComicPremiumGate
import com.example.ui.screens.comic.ComicReaderScreen
import com.example.ui.screens.comic.ComicReaderViewModel
import com.example.ui.screens.comic.ComicScreen
import com.example.ui.screens.comic.ComicViewModel
import com.example.ui.screens.detail.DetailScreen
import com.example.ui.screens.detail.DetailViewModel
import com.example.ui.screens.favorites.FavoritesHistoryScreen
import com.example.ui.screens.favorites.FavoritesHistoryViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.login.LoginScreen
import com.example.ui.screens.login.LoginViewModel
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.player.PlayerViewModel
import com.example.ui.screens.player.PremiumGate
import com.example.ui.screens.premium.PremiumPromoDialog
import com.example.ui.screens.premium.PremiumScreen
import com.example.ui.screens.premium.PremiumViewModel
import com.example.ui.screens.schedule.ScheduleScreen
import com.example.ui.screens.schedule.ScheduleViewModel
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.search.SearchViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeBackgroundDark
import com.example.ui.theme.ZenimeSurfaceDark
import com.example.ui.theme.ZenimePrimary
import com.example.util.findActivity

sealed class Screen(
    val route: String,
    val title: String? = null,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    data object Login : Screen("login")
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Search : Screen("search?status={status}", "Cari", Icons.Filled.Search, Icons.Outlined.Search) {
        fun createRoute(status: String? = null): String =
            if (status != null) "search?status=$status" else "search"
    }
    data object Schedule : Screen("schedule", "Jadwal", Icons.Filled.DateRange, Icons.Outlined.DateRange)
    data object Favorites : Screen("favorites", "Koleksi", Icons.Filled.Bookmark, Icons.Outlined.Bookmark)
    data object Settings : Screen("settings", "Pengaturan", Icons.Filled.Settings, Icons.Outlined.Settings)
    data object Comic : Screen("comic", "Komik", Icons.Filled.AutoStories, Icons.Outlined.AutoStories)

    data object Premium : Screen("premium")

    data object Chat : Screen("chat")

    data object Detail : Screen("detail/{animeId}") {
        fun createRoute(animeId: String) = "detail/$animeId"
    }

    data object Player : Screen("player/{episodeId}/{animeId}") {
        fun createRoute(episodeId: String, animeId: String) = "player/$episodeId/$animeId"
    }

    data object ComicDetail : Screen("comic-detail/{slug}") {
        fun createRoute(slug: String) = "comic-detail/$slug"
    }

    // Slug chapter (mis. "nano-machine-chapter-1") dilewatin apa adanya --
    // isinya cuma huruf/angka/strip, aman lewat NavType.StringType biasa
    // tanpa perlu encode/decode URL kayak query pencarian.
    data object ComicReader : Screen("comic-reader/{chapterSlug}") {
        fun createRoute(chapterSlug: String) = "comic-reader/$chapterSlug"
    }
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Schedule,
    Screen.Favorites,
    Screen.Settings,
    Screen.Comic
)

@Composable
fun ZenimeAppNavHost(
    repository: AnimeRepository,
    comicRepository: ComicRepository,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavScreens.map { it.route }

    // Satu instance AuthRepository dipakai bareng sama LoginScreen di bawah,
    // biar status login yang dicek buat mutusin navigasi (Splash -> Login
    // atau Home) sama persis sama yang di-observe screen lain.
    val authRepository = remember { AuthRepository() }
    val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()

    // Promo Premium full-screen -- muncul TIAP kali app dibuka (cold start
    // ATAUPUN balik dari background, keduanya kehitung "buka app" versi
    // Android lewat ON_START), TAPI cuma kalau user udah login dan
    // ternyata belum premium. Sengaja BUKAN gated "sekali doang seumur
    // proses" -- makanya triggernya pakai Lifecycle observer di ON_START,
    // bukan cuma LaunchedEffect(currentUser) yang cuma nyala sekali pas
    // status login berubah.
    var showPremiumPromo by remember { mutableStateOf(false) }
    var promoPackages by remember { mutableStateOf<List<PremiumPackage>>(emptyList()) }
    var promoLoading by remember { mutableStateOf(true) }
    val premiumRepositoryForPromo = remember { PremiumRepository() }
    val promoCoroutineScope = rememberCoroutineScope()

    suspend fun checkAndShowPremiumPromo(uid: String) {
        promoLoading = true
        showPremiumPromo = true
        val isPremium = premiumRepositoryForPromo.checkPremiumStatus(uid).getOrNull()?.isPremium ?: false
        if (isPremium) {
            // Udah premium -- gak usah nawarin apa-apa.
            showPremiumPromo = false
            return
        }
        val packages = premiumRepositoryForPromo.getPackages().getOrNull().orEmpty()
        promoPackages = packages
        promoLoading = false
        // Kalau ternyata gak ada paket sama sekali, gak usah paksain
        // nongolin promo kosong.
        showPremiumPromo = packages.isNotEmpty()
    }

    // Trigger #1: begitu status login berubah dari belum login -> login
    // (misal abis LoginScreen sukses) SELAMA app udah kebuka.
    LaunchedEffect(currentUser) {
        currentUser?.uid?.let { uid -> checkAndShowPremiumPromo(uid) }
    }

    // Trigger #2: tiap Activity-nya ON_START -- ini yang nangkep skenario
    // "user minimize app terus buka lagi" atau "cold start pas sesi login
    // lama masih kesimpen", yang gak selalu bikin currentUser BERUBAH
    // (dari awal udah non-null), jadi Trigger #1 doang gak bakal nyala lagi.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                authRepository.currentUser.value?.uid?.let { uid ->
                    promoCoroutineScope.launch { checkAndShowPremiumPromo(uid) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Kalau user logout (misal dari tombol Logout di Settings) pas lagi
    // gak di Login, lempar balik ke Login dan bersihin backstack --
    // supaya gak ada cara masuk balik ke Home tanpa login ulang.
    LaunchedEffect(currentUser) {
        if (currentUser == null && currentRoute != null &&
            currentRoute != Screen.Login.route
        ) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Lock orientasi landscape berdasarkan ROUTE saat ini (bukan lifecycle
    // masing-masing composable PlayerScreen). Ini penting pas pindah dari
    // satu episode ke episode selanjutnya: dua instance PlayerScreen (lama &
    // baru) bisa sempet hidup bersamaan selama animasi transisi navigasi.
    // Kalau lock/restore-nya ditaruh di dalam PlayerScreen (DisposableEffect
    // per-composable), instance lama bisa aja ke-dispose belakangan dan
    // nimpa balik ke portrait padahal instance baru udah nge-set landscape.
    // Dengan nge-cek di sini (satu sumber kebenaran = current route), race
    // itu nggak mungkin kejadian lagi.
    val context = LocalContext.current
    LaunchedEffect(currentRoute) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        activity.requestedOrientation = if (currentRoute == Screen.Player.route) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Immersive mode (sembunyiin status bar & navigation bar) + keep-screen-on
    // buat PlayerScreen -- di-drive dari BACKSTACK ENTRY (bukan cuma string
    // route pattern-nya kayak effect orientasi di atas). Ini penting: route
    // PATTERN "player/{episodeId}/{animeId}" nilainya SAMA PERSIS pas pindah
    // dari episode 6 ke episode 7 (baik lewat tombol "Episode Selanjutnya"
    // maupun sidebar Daftar Episode), jadi kalau di-key ke currentRoute
    // (String) doang, effect ini gak bakal ke-trigger ulang pas ganti
    // episode -- status bar yang sempet muncul (misal abis di-swipe) gak
    // ke-hide lagi. navBackStackEntry beda identitas tiap kali navigate(),
    // walau route pattern-nya sama, jadi ini kunci yang lebih aman.
    LaunchedEffect(navBackStackEntry) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        val window = activity.window
        val isPlayerRoute = currentRoute == Screen.Player.route

        if (isPlayerRoute) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // AuthRepository nyimpen currentUser secara synchronous dari
        // firebaseAuth.currentUser pas di-init (lihat AuthRepository.kt),
        // jadi kita bisa langsung mutusin start destination di sini tanpa
        // splash screen buat nunggu status login kebaca dulu.
        val startDestination = if (authRepository.currentUser.value != null) {
            Screen.Home.route
        } else {
            Screen.Login.route
        }

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            // Login Screen (wajib sebelum masuk app)
            composable(Screen.Login.route) {
                val loginViewModel: LoginViewModel = viewModel(
                    factory = viewModelFactory { initializer { LoginViewModel(authRepository) } }
                )
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // Home Screen
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = viewModelFactory { initializer { HomeViewModel(repository, comicRepository) } }
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.createRoute())
                    },
                    onSeeAllOngoingClick = {
                        navController.navigate(Screen.Search.createRoute(status = "ONGOING"))
                    },
                    onChatClick = {
                        navController.navigate(Screen.Chat.route)
                    },
                    onPlayEpisodeClick = { episodeId, animeId ->
                        navController.navigate(Screen.Player.createRoute(episodeId, animeId))
                    },
                    onComicClick = { slug ->
                        navController.navigate(Screen.ComicDetail.createRoute(slug))
                    },
                    onSeeAllComicClick = {
                        navController.navigate(Screen.Comic.route)
                    }
                )
            }

            // Komik -- daftar terbaru/populer, pencarian, filter genre
            composable(Screen.Comic.route) {
                val comicViewModel: ComicViewModel = viewModel(
                    factory = viewModelFactory { initializer { ComicViewModel(comicRepository) } }
                )
                ComicScreen(
                    viewModel = comicViewModel,
                    onComicClick = { slug ->
                        navController.navigate(Screen.ComicDetail.createRoute(slug))
                    }
                )
            }

            // Detail Komik
            composable(
                route = Screen.ComicDetail.route,
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) { backStackEntry ->
                val slug = backStackEntry.arguments?.getString("slug") ?: ""
                val comicDetailViewModel = remember(slug) { ComicDetailViewModel(comicRepository, slug) }
                ComicDetailScreen(
                    viewModel = comicDetailViewModel,
                    onBackClick = { navController.popBackStack() },
                    onChapterClick = { chapterSlug ->
                        navController.navigate(Screen.ComicReader.createRoute(chapterSlug))
                    }
                )
            }

            // Reader Komik -- baca gambar chapter, bisa lanjut/mundur chapter
            // tanpa balik ke halaman detail (langsung ganti state di ViewModel).
            composable(
                route = Screen.ComicReader.route,
                arguments = listOf(navArgument("chapterSlug") { type = NavType.StringType })
            ) { backStackEntry ->
                val chapterSlug = backStackEntry.arguments?.getString("chapterSlug") ?: ""
                val comicReaderViewModel = remember(chapterSlug) { ComicReaderViewModel(comicRepository, chapterSlug) }
                ComicPremiumGate(
                    firebaseUid = currentUser?.uid,
                    onBackClick = { navController.popBackStack() },
                    onUpgradeClick = { navController.navigate(Screen.Premium.route) }
                ) {
                    ComicReaderScreen(
                        viewModel = comicReaderViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            // Search Screen
            composable(
                route = Screen.Search.route,
                arguments = listOf(navArgument("status") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val initialStatus = backStackEntry.arguments?.getString("status")
                val searchViewModel: SearchViewModel = viewModel(
                    factory = viewModelFactory { initializer { SearchViewModel(repository, initialStatus) } }
                )
                SearchScreen(
                    viewModel = searchViewModel,
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    }
                )
            }

            // Schedule Screen
            composable(Screen.Schedule.route) {
                val scheduleViewModel: ScheduleViewModel = viewModel(
                    factory = viewModelFactory { initializer { ScheduleViewModel(repository) } }
                )
                ScheduleScreen(
                    viewModel = scheduleViewModel,
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    }
                )
            }

            // Favorites & Watch History Screen
            composable(Screen.Favorites.route) {
                val favViewModel: FavoritesHistoryViewModel = viewModel(
                    factory = viewModelFactory { initializer { FavoritesHistoryViewModel(repository) } }
                )
                FavoritesHistoryScreen(
                    viewModel = favViewModel,
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    },
                    onPlayEpisodeClick = { episodeId, animeId ->
                        navController.navigate(Screen.Player.createRoute(episodeId, animeId))
                    }
                )
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = viewModelFactory { initializer { SettingsViewModel(repository) } }
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onPremiumClick = { navController.navigate(Screen.Premium.route) }
                )
            }

            // Premium Screen -- daftar paket, kode akun buat checkout di storefront
            composable(Screen.Premium.route) {
                val uid = currentUser?.uid
                if (uid != null) {
                    val premiumViewModel: PremiumViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { PremiumViewModel(PremiumRepository(), uid) }
                        }
                    )
                    PremiumScreen(viewModel = premiumViewModel)
                }
            }

            // Chat Global -- pesan publik antar semua pengguna, polling tiap
            // beberapa detik, ada cooldown 5 detik antar kirim (dihandle di ChatViewModel).
            composable(Screen.Chat.route) {
                val uid = currentUser?.uid
                if (uid != null) {
                    val chatViewModel: ChatViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                ChatViewModel(
                                    repository = ChatRepository(),
                                    premiumRepository = PremiumRepository(),
                                    firebaseUid = uid,
                                    fallbackUsername = currentUser?.displayName ?: "Pengguna"
                                )
                            }
                        }
                    )
                    ChatScreen(
                        viewModel = chatViewModel,
                        currentFirebaseUid = uid,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            // Detail Screen
            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("animeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
                val detailViewModel = remember(animeId, currentUser?.uid) {
                    DetailViewModel(repository, animeId, currentUser?.uid)
                }
                DetailScreen(
                    viewModel = detailViewModel,
                    onBackClick = { navController.popBackStack() },
                    onEpisodeClick = { episodeId, _ ->
                        navController.navigate(Screen.Player.createRoute(episodeId, animeId))
                    },
                    onUpgradeClick = {
                        navController.navigate(Screen.Premium.route)
                    }
                )
            }

            // Video Player Screen
            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("episodeId") { type = NavType.StringType },
                    navArgument("animeId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val episodeId = backStackEntry.arguments?.getString("episodeId") ?: ""
                val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
                val playerViewModel = remember(episodeId, animeId) { PlayerViewModel(repository, episodeId, animeId) }

                PremiumGate(
                    firebaseUid = currentUser?.uid,
                    episodeId = episodeId,
                    animeId = animeId,
                    repository = repository,
                    onBackClick = { navController.popBackStack() },
                    onUpgradeClick = {
                        navController.navigate(Screen.Premium.route) {
                            popUpTo(Screen.Player.route) { inclusive = true }
                        }
                    }
                ) { isPremium ->
                    PlayerScreen(
                        viewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() },
                        onNextEpisodeClick = { nextEpId ->
                            navController.navigate(Screen.Player.createRoute(nextEpId, animeId)) {
                                popUpTo(Screen.Player.route) { inclusive = true }
                            }
                        },
                        isPremium = isPremium,
                        onUpgradeClick = {
                            navController.navigate(Screen.Premium.route)
                        }
                    )
                }
            }
        }

        if (showBottomBar) {
            FloatingPillBottomBar(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    if (currentRoute != screen.route) {
                        val destinationRoute = if (screen is Screen.Search) {
                            Screen.Search.createRoute()
                        } else {
                            screen.route
                        }
                        navController.navigate(destinationRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            )
        }

        if (showPremiumPromo) {
            PremiumPromoDialog(
                isLoading = promoLoading,
                packages = promoPackages,
                onDismiss = { showPremiumPromo = false },
                onSubscribeClick = {
                    showPremiumPromo = false
                    navController.navigate(Screen.Premium.route)
                }
            )
        }
    }
}

/**
 * Satu pill utuh, semua item (termasuk Jadwal) sejajar rata di dalamnya --
 * gak ada lagi tombol tengah yang dinaikkan/nongol keluar pill kayak versi
 * sebelumnya (FAB merah ngambang yang nutupin konten di atasnya). Item aktif
 * dibedain cuma lewat warna icon + titik indikator, konsisten sama gaya
 * NavIconButton yang lain -- gak butuh warna solid/gradient terpisah buat
 * nandain "yang paling penting".
 */
@Composable
fun FloatingPillBottomBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillHeight = 64.dp
    val pillShape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Pill dasar -- efek "glass": permukaan gelap semi-transparan dengan
        // gradient halus (lebih terang dikit di atas, lebih gelap di bawah)
        // buat kesan kedalaman, plus border tipis gradient yang mensimulasikan
        // highlight kaca di tepi atas.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillHeight)
                .shadow(
                    elevation = 20.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(pillShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ZenimeSurfaceDark.copy(alpha = 0.98f),
                            ZenimeBackgroundDark.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            CardOutlineBorder.copy(alpha = 0.4f)
                        )
                    ),
                    shape = pillShape
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavScreens.forEach { screen ->
                    NavIconButton(screen = screen, selected = currentRoute == screen.route, onClick = { onNavigate(screen) })
                }
            }
        }
    }
}

@Composable
private fun NavIconButton(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val icon = if (selected) screen.selectedIcon!! else screen.unselectedIcon!!
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .testTag("nav_item_${screen.route}")
        ) {
            Icon(
                imageVector = icon,
                contentDescription = screen.title,
                tint = if (selected) ZenimePrimary else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }
        // Titik indikator kecil di bawah icon aktif -- gaya modern minimalis,
        // gantiin lingkaran block penuh yang kesannya lebih flat/lama.
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    color = if (selected) ZenimePrimary else Color.Transparent,
                    shape = CircleShape
                )
        )
    }
}


