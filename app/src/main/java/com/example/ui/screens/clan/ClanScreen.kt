package com.example.ui.screens.clan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ClanDonationEntry
import com.example.data.model.ClanMemberDisplay
import com.example.data.model.ClanRoles
import com.example.ui.components.ClanRainbowBadge
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.GeneratedAvatar
import com.example.ui.components.LevelBadge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Palet layar Clan: gelap pekat + aksen merah-koral (senada tombol utama app) + header ungu ---
private val ClanBg = Color(0xFF0B0D14)
private val ClanSurface = Color(0xFF141826)
private val ClanBorder = Color(0xFF262C42)
private val ClanMuted = Color(0xFF8E97AD)
private val ClanAccent = Color(0xFFE63950)
private val ClanAccentAlt = Color(0xFFFF7A59)
private val GemBlue = Color(0xFF4FC3F7)
private val VerifiedBlue = Color(0xFF3897F0)
private val ClanAccentBrush get() = Brush.horizontalGradient(listOf(ClanAccent, ClanAccentAlt))

private val RoleLeaderColor = Color(0xFFFFC107)      // kuning
private val RoleViceColor = Color(0xFF9F7AEA)        // ungu
private val RoleAdmiralColor = Color(0xFFE0912F)     // oranye
private val RoleOfficerColor = Color(0xFF26A69A)     // teal
private val RoleMemberColor = Color(0xFF607D8B)      // abu kebiruan

@Composable
fun ClanScreen(
    viewModel: ClanViewModel,
    onBackClick: () -> Unit,
    onManageClanClick: (clanId: String) -> Unit = {},
    onMemberClick: (uid: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Setelah berhasil keluar clan, gak ada lagi yang bisa ditampilin di screen ini -- balik ke layar sebelumnya.
    LaunchedEffect(uiState.leftClan) {
        if (uiState.leftClan) onBackClick()
    }

    val gradientEnd = with(LocalDensity.current) { 380.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClanBg)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF2A1F52), Color(0xFF15122B), ClanBg),
                    startY = 0f,
                    endY = gradientEnd
                )
            )
    ) {
    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                ClanTopBar(onBackClick = onBackClick)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        uiState.isLoading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ClanAccent)
                            }
                        }
                        uiState.error != null -> {
                            ErrorStateView(message = uiState.error!!, onRetry = viewModel::retry)
                        }
                        uiState.clan != null -> {
                            ClanContent(
                                uiState = uiState,
                                onTabSelected = viewModel::onTabSelected,
                                onSearchQueryChange = viewModel::onSearchQueryChange,
                                onToggleSort = viewModel::onToggleSort,
                                onRequestJoinClick = viewModel::requestJoin,
                                onManageClanClick = { onManageClanClick(uiState.clan!!.id) },
                                onDonateClick = { viewModel.onDonateDialogToggle(true) },
                                onLeaveClick = { viewModel.onLeaveDialogToggle(true) },
                                onMemberClick = onMemberClick,
                                onMemberActionClick = viewModel::onMemberActionClick
                            )
                        }
                    }
                }
            }

            if (uiState.showDonateDialog) {
                DonateDialog(
                    amountInput = uiState.donateAmountInput,
                    isDonating = uiState.isDonating,
                    feedback = uiState.donateFeedback,
                    onAmountChange = viewModel::onDonateAmountChange,
                    onDismiss = { viewModel.onDonateDialogToggle(false) },
                    onSubmit = viewModel::submitDonation
                )
            }

            uiState.actionTarget?.let { target ->
                MemberActionDialog(
                    target = target,
                    actorRole = uiState.myRole,
                    isBusy = uiState.isActionLoading,
                    feedback = uiState.actionFeedback,
                    onDismiss = viewModel::dismissMemberAction,
                    onSetRole = viewModel::changeMemberRole,
                    onKick = viewModel::kickMember
                )
            }

            if (uiState.showLeaveDialog) {
                LeaveClanDialog(
                    isLeaving = uiState.isLeaving,
                    feedback = uiState.leaveFeedback,
                    onDismiss = { viewModel.onLeaveDialogToggle(false) },
                    onConfirm = viewModel::confirmLeaveClan
                )
            }
        }
    }
    }
}

@Composable
private fun ClanTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
    }
}

@Composable
private fun ClanContent(
    uiState: ClanUiState,
    onTabSelected: (ClanTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSort: () -> Unit,
    onRequestJoinClick: () -> Unit,
    onManageClanClick: () -> Unit,
    onDonateClick: () -> Unit,
    onLeaveClick: () -> Unit,
    onMemberClick: (uid: String) -> Unit,
    onMemberActionClick: (ClanMemberDisplay) -> Unit
) {
    val clan = uiState.clan ?: return
    val isMembersTab = uiState.selectedTab == ClanTab.MEMBERS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            ClanHeader(
                uiState = uiState,
                onRequestJoinClick = onRequestJoinClick,
                onManageClanClick = onManageClanClick,
                onDonateClick = onDonateClick,
                onLeaveClick = onLeaveClick
            )
            Spacer(Modifier.height(20.dp))
            ClanTabs(selected = uiState.selectedTab, onSelected = onTabSelected)
            Spacer(Modifier.height(14.dp))
            if (!isMembersTab) {
                DonationSummaryPill(uiState)
                Spacer(Modifier.height(12.dp))
            }
            SearchAndFilterRow(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChange,
                sortAscending = uiState.sortAscending,
                onToggleSort = onToggleSort
            )
            Spacer(Modifier.height(8.dp))
        }

        if (isMembersTab) {
            val list = uiState.filteredMembers
            if (list.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "Member Tidak Ditemukan",
                        description = "Coba ganti kata kunci atau filter role."
                    )
                }
            } else {
                items(list, key = { it.firebaseUid }) { member ->
                    MemberRow(
                        member = member,
                        clanTag = clan.tag,
                        level = uiState.memberLevels[member.firebaseUid],
                        isPremium = uiState.premiumUids.contains(member.firebaseUid),
                        globalRole = uiState.rolesByUid[member.firebaseUid],
                        globalRoleBadgeColor = uiState.roleBadgeColorsByUid[member.firebaseUid],
                        onClick = { onMemberClick(member.firebaseUid) },
                        onActionClick = if (
                            member.firebaseUid != uiState.myUid &&
                            ClanRoles.canActOn(uiState.myRole, member.role)
                        ) {
                            { onMemberActionClick(member) }
                        } else null
                    )
                }
            }
        } else {
            val list = uiState.filteredDonations
            if (list.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "Belum Ada Donasi Hari Ini",
                        description = "Member yang donasi ZCoin hari ini bakal muncul di sini."
                    )
                }
            } else {
                items(list, key = { it.firebaseUid }) { entry ->
                    val rank = uiState.donationsToday.indexOfFirst { it.firebaseUid == entry.firebaseUid } + 1
                    val member = uiState.members.firstOrNull { it.firebaseUid == entry.firebaseUid }
                    DonationRow(rank = rank, entry = entry, member = member)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun ClanHeader(
    uiState: ClanUiState,
    onRequestJoinClick: () -> Unit,
    onManageClanClick: () -> Unit,
    onDonateClick: () -> Unit,
    onLeaveClick: () -> Unit
) {
    val clan = uiState.clan ?: return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clan.name,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClanRainbowBadge(text = clan.tag)
                    Spacer(Modifier.width(8.dp))
                    LevelBadge(level = clan.level)
                }
            }
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${clan.memberCount}/${clan.memberLimit}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        XpProgress(level = clan.level, totalXp = clan.totalXp)
        Spacer(Modifier.height(18.dp))

        ClanActions(
            uiState = uiState,
            onRequestJoinClick = onRequestJoinClick,
            onManageClanClick = onManageClanClick,
            onDonateClick = onDonateClick,
            onLeaveClick = onLeaveClick
        )

        uiState.joinFeedback?.let { feedback ->
            Spacer(Modifier.height(8.dp))
            Text(feedback, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

/**
 * Batas XP per level clan.
 *
 * PERKIRAAN -- rumus asli level clan ada di server (fungsi donate_to_clan),
 * gak ada di project Android. Rumus di bawah cuma nyocokin data yang ada
 * (level 19 ~ 176K total XP). Kalau rumus server beda, ganti isi fungsi ini aja.
 */
private fun clanXpFloor(level: Int): Long = 500L * level * (level - 1)

@Composable
private fun XpProgress(level: Int, totalXp: Long) {
    val floor = clanXpFloor(level)
    val ceil = clanXpFloor(level + 1)
    val current = (totalXp - floor).coerceAtLeast(0)
    val needed = (ceil - floor).coerceAtLeast(1)
    val progress = (current.toFloat() / needed.toFloat()).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Menuju Level ${level + 1}", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            Text(
                "${formatCompactId(current)} / ${formatCompactId(needed)} XP",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(50))
                    .background(ClanAccentBrush)
            )
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ClanAccentBrush, alpha = if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun GlassButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun ClanActions(
    uiState: ClanUiState,
    onRequestJoinClick: () -> Unit,
    onManageClanClick: () -> Unit,
    onDonateClick: () -> Unit,
    onLeaveClick: () -> Unit
) {
    when (uiState.cta) {
        ClanMembershipCta.REQUEST_JOIN -> {
            GradientButton(
                text = "Join Clan",
                icon = Icons.Filled.PersonAdd,
                enabled = !uiState.isSubmittingJoin,
                loading = uiState.isSubmittingJoin,
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestJoinClick
            )
        }
        ClanMembershipCta.PENDING -> DisabledActionButton("Menunggu Persetujuan")
        ClanMembershipCta.BLOCKED_OTHER_CLAN -> DisabledActionButton("Kamu Sudah Gabung Clan Lain")
        else -> {
            // Sudah jadi member (role apa pun): Donasi + (kalau punya izin) Kelola Clan.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                GradientButton(
                    text = "Donasi ZCoin",
                    icon = Icons.Filled.Diamond,
                    modifier = Modifier.weight(1f),
                    onClick = onDonateClick
                )
                if (ClanRoles.canManageClan(uiState.myRole)) {
                    GlassButton(text = "Kelola Clan", modifier = Modifier.weight(1f), onClick = onManageClanClick)
                }
            }
            if (uiState.canLeave) {
                androidx.compose.material3.TextButton(
                    onClick = onLeaveClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Keluar Clan", fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9AA5))
                }
            }
        }
    }
}

@Composable
private fun DisabledActionButton(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

// ---------------------------------------------------------------------------
// Tab, search, filter
// ---------------------------------------------------------------------------

@Composable
private fun ClanTabs(selected: ClanTab, onSelected: (ClanTab) -> Unit) {
    val outer = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outer)
            .background(ClanSurface)
            .border(1.dp, ClanBorder, outer)
            .padding(4.dp)
    ) {
        listOf(ClanTab.MEMBERS to "Members", ClanTab.DONATION_TODAY to "Donasi").forEach { (tab, label) ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (isSelected) Modifier.background(ClanAccentBrush) else Modifier)
                    .clickable { onSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else ClanMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DonationSummaryPill(uiState: ClanUiState) {
    val memberCount = uiState.clan?.memberCount ?: uiState.members.size
    val total = "%,d".format(Locale("id", "ID"), uiState.totalDonatedToday)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ClanAccent.copy(alpha = 0.12f))
            .border(1.dp, ClanAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "Hari ini · ${uiState.donorCountToday} dari $memberCount member donasi · $total ZCoin",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchAndFilterRow(
    query: String,
    onQueryChange: (String) -> Unit,
    sortAscending: Boolean,
    onToggleSort: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(50),
            placeholder = { Text("Cari member (nama atau ID)", color = ClanMuted, fontSize = 14.sp, maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ClanMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = ClanAccent,
                focusedBorderColor = ClanAccent,
                unfocusedBorderColor = ClanBorder,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ClanSurface)
                .border(1.dp, ClanBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onToggleSort),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = "Urutkan",
                tint = Color.White
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Baris member & donasi
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(
    member: ClanMemberDisplay,
    clanTag: String,
    level: Int?,
    isPremium: Boolean,
    globalRole: String?,
    globalRoleBadgeColor: String?,
    onClick: () -> Unit,
    onActionClick: (() -> Unit)?
) {
    // Sama kayak Chat Global: role global (developer/admin/moderator) menang atas Premium -- satu centang aja.
    val roleCheckColor: Color? = globalRole?.let { role ->
        val hex = globalRoleBadgeColor ?: when (role) {
            "developer" -> "#E53935"
            "admin" -> "#43A047"
            "moderator" -> "#8E24AA"
            else -> null
        }
        hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    }
    val checkColor: Color? = roleCheckColor ?: if (isPremium) VerifiedBlue else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onActionClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(50.dp).height(58.dp)) {
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    MemberAvatar(member = member, size = 50.dp)
                }
                RoleBadge(
                    role = member.role,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .wrapContentWidth(unbounded = true)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.username,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (member.userNumber != null) {
                        Text(
                            "#${member.userNumber}",
                            color = ClanMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 5.dp)
                        )
                    }
                    if (checkColor != null) {
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = globalRole ?: "Premium",
                            tint = checkColor,
                            modifier = Modifier.padding(start = 4.dp).size(17.dp)
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClanRainbowBadge(text = clanTag)
                    if (level != null) {
                        Spacer(Modifier.width(8.dp))
                        LevelBadge(level = level)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            GemPill(amount = member.totalContribution)
        }

        if (onActionClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onActionClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Kelola member", tint = ClanMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun GemPill(amount: Long) {
    Row(
        modifier = Modifier
            .widthIn(min = 84.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1B2440), Color(0xFF16203A))))
            .border(1.dp, GemBlue.copy(alpha = 0.30f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Diamond, contentDescription = null, tint = GemBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(formatCompactId(amount), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun DonationRow(rank: Int, entry: ClanDonationEntry, member: ClanMemberDisplay?) {
    val medal: Color? = when (rank) {
        1 -> Color(0xFFFFC107)
        2 -> Color(0xFFB0BEC5)
        3 -> Color(0xFFCD7F32)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(medal ?: Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$rank",
                color = if (medal != null) Color.Black else ClanMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(8.dp))
        DonationAvatar(entry = entry, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.username,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoleBadge(role = entry.role)
                if (member != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Total ${formatCompactId(member.totalContribution)} · Gabung ${formatJoinShort(member.joinedAt)}",
                        color = ClanMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        GemPill(amount = entry.amountToday)
    }
}

@Composable
private fun RoleBadge(role: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (role) {
        ClanRoles.LEADER -> RoleLeaderColor to Color.Black
        ClanRoles.VICE_LEADER -> RoleViceColor to Color.White
        ClanRoles.ADMIRAL -> RoleAdmiralColor to Color(0xFF2B1600)
        ClanRoles.OFFICER -> RoleOfficerColor to Color.White
        else -> RoleMemberColor to Color.White
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = ClanRoles.label(role),
            color = fg,
            fontSize = 7.5.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun MemberAvatar(member: ClanMemberDisplay, size: Dp) {
    if (member.avatarUrl != null) {
        AsyncImage(
            model = member.avatarUrl,
            contentDescription = member.username,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        GeneratedAvatar(seed = member.firebaseUid, label = member.username, size = size)
    }
}

@Composable
private fun DonationAvatar(entry: ClanDonationEntry, size: Dp) {
    if (entry.avatarUrl != null) {
        AsyncImage(
            model = entry.avatarUrl,
            contentDescription = entry.username,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        GeneratedAvatar(seed = entry.firebaseUid, label = entry.username, size = size)
    }
}

// ---------------------------------------------------------------------------
// Format angka & tanggal
// ---------------------------------------------------------------------------

/** 84700 -> "84,7K" (koma desimal ala Indonesia, sama kayak referensi). */
private fun formatCompactId(value: Long): String {
    val abs = kotlin.math.abs(value)
    val id = Locale("id", "ID")
    return when {
        abs >= 1_000_000 -> "%.1fM".format(id, value / 1_000_000.0)
        abs >= 1_000 -> "%.1fK".format(id, value / 1000.0)
        else -> value.toString()
    }
}

/** "Gabung 13 hr" versi ringkas buat tab Donasi. */
private fun formatJoinShort(isoTimestamp: String): String {
    return try {
        val minutes = java.time.Duration.between(Instant.parse(isoTimestamp), Instant.now()).toMinutes()
        when {
            minutes < 60 -> "${minutes.coerceAtLeast(1)} mnt"
            minutes < 60 * 24 -> "${minutes / 60} jam"
            minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} hr"
            minutes < 60 * 24 * 365 -> "${minutes / (60 * 24 * 30)} bln"
            else -> "${minutes / (60 * 24 * 365)} thn"
        }
    } catch (e: Exception) {
        ""
    }
}

private fun formatCompact(value: Long): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 1_000_000 -> "%.1fM".format(Locale.US, value / 1_000_000.0)
        abs >= 1_000 -> "%.0fK".format(Locale.US, value / 1000.0)
        else -> value.toString()
    }
}

private fun formatRelativeDate(isoTimestamp: String): String {
    return try {
        val instant = Instant.parse(isoTimestamp)
        val now = Instant.now()
        val minutes = java.time.Duration.between(instant, now).toMinutes()
        when {
            minutes < 1 -> "baru saja"
            minutes < 60 -> "$minutes menit lalu"
            minutes < 60 * 24 -> "${minutes / 60} jam lalu"
            minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} hari lalu"
            minutes < 60 * 24 * 365 -> "${minutes / (60 * 24 * 30)} bulan lalu"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("id", "ID"))
                formatter.withZone(ZoneId.systemDefault()).format(instant)
            }
        }
    } catch (e: Exception) {
        ""
    }
}

@Composable
private fun LeaveClanDialog(
    isLeaving: Boolean,
    feedback: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isLeaving) onDismiss() },
        title = { Text("Keluar Clan?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Kamu bakal kehilangan kontribusi & role kamu di clan ini. Kamu bisa gabung lagi nanti lewat Request Join.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                feedback?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLeaving,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isLeaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Keluar")
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !isLeaving) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun DonateDialog(
    amountInput: String,
    isDonating: Boolean,
    feedback: String?,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isDonating) onDismiss() },
        title = { Text("Donasi ZCoin", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "ZCoin yang kamu donasiin bakal masuk ke XP clan (naikin level) dan dicatat sebagai kontribusi kamu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = onAmountChange,
                    label = { Text("Jumlah ZCoin") },
                    singleLine = true,
                    enabled = !isDonating,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                feedback?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !isDonating) {
                if (isDonating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Donasi")
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !isDonating) {
                Text("Batal")
            }
        }
    )
}


@Composable
private fun MemberActionDialog(
    target: ClanMemberDisplay,
    actorRole: String?,
    isBusy: Boolean,
    feedback: String?,
    onDismiss: () -> Unit,
    onSetRole: (String) -> Unit,
    onKick: () -> Unit
) {
    val assignable = ClanRoles.assignableRoles(actorRole, target.role)
    val canKick = ClanRoles.canKick(actorRole, target.role)
    var confirmKick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.username, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (confirmKick) {
                    Text(
                        "Yakin mau kick ${target.username} dari clan?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Role sekarang:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        RoleBadge(role = target.role)
                    }
                    if (assignable.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Ubah jadi:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        assignable.forEach { role ->
                            androidx.compose.material3.OutlinedButton(
                                onClick = { onSetRole(role) },
                                enabled = !isBusy,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Text(ClanRoles.label(role), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                feedback?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (isBusy) {
                    Spacer(Modifier.height(10.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            if (confirmKick) {
                Button(
                    onClick = onKick,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Kick") }
            } else if (canKick) {
                androidx.compose.material3.TextButton(onClick = { confirmKick = true }, enabled = !isBusy) {
                    Text("Kick Member", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (confirmKick) confirmKick = false else onDismiss() },
                enabled = !isBusy
            ) { Text(if (confirmKick) "Batal" else "Tutup") }
        }
    )
}
