package com.example.ui.screens.clan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ClanDonationEntry
import com.example.data.model.ClanMemberDisplay
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.GeneratedAvatar
import com.example.ui.theme.ZenimePrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LeaderBadgeColor = Color(0xFFFFC107)      // kuning, sama kayak referensi
private val CoLeaderBadgeColor = Color(0xFF9C6BE0)     // ungu
private val MemberBadgeColor = Color(0xFF3A404C)       // abu gelap

@Composable
fun ClanScreen(
    viewModel: ClanViewModel,
    onBackClick: () -> Unit,
    onManageClanClick: (clanId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Lihat Clan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                        onRequestJoinClick = viewModel::requestJoin,
                        onManageClanClick = { onManageClanClick(uiState.clan!!.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClanContent(
    uiState: ClanUiState,
    onTabSelected: (ClanTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRequestJoinClick: () -> Unit,
    onManageClanClick: () -> Unit
) {
    val clan = uiState.clan ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            ClanHeaderCard(
                uiState = uiState,
                onRequestJoinClick = onRequestJoinClick,
                onManageClanClick = onManageClanClick
            )

            Spacer(Modifier.height(16.dp))

            ClanTabRow(
                selectedTab = uiState.selectedTab,
                memberCount = clan.memberCount,
                donorCountToday = uiState.donorCountToday,
                onTabSelected = onTabSelected
            )

            Spacer(Modifier.height(12.dp))

            if (uiState.selectedTab == ClanTab.MEMBERS) {
                MemberSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange
                )
                Spacer(Modifier.height(12.dp))
            } else {
                TodayDonationSummary(uiState)
                Spacer(Modifier.height(12.dp))
            }
        }

        when (uiState.selectedTab) {
            ClanTab.MEMBERS -> {
                if (uiState.filteredMembers.isEmpty()) {
                    EmptyStateView(
                        title = "Belum Ada Member",
                        description = "Member clan bakal muncul di sini."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 4.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.filteredMembers, key = { it.firebaseUid }) { member ->
                            MemberListItem(member)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
            ClanTab.DONATION_TODAY -> {
                if (uiState.donationsToday.isEmpty()) {
                    EmptyStateView(
                        title = "Belum Ada Donasi Hari Ini",
                        description = "Member yang donasi ZCoin hari ini bakal muncul di sini."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 4.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(uiState.donationsToday) { index, entry ->
                            DonationListItem(rank = index + 1, entry = entry)
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClanHeaderCard(
    uiState: ClanUiState,
    onRequestJoinClick: () -> Unit,
    onManageClanClick: () -> Unit
) {
    val clan = uiState.clan ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClanPhoto(photoUrl = clan.photoUrl, tag = clan.tag, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                TagPill(tag = clan.tag)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = clan.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        uiState.leader?.let { leader ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(member = leader, size = 22.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = leader.username,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ClanStatBox(value = clan.level.toString(), label = "Level", modifier = Modifier.weight(1f))
            ClanStatBox(value = formatCompact(clan.totalXp), label = "Total XP", modifier = Modifier.weight(1f))
            ClanStatBox(value = clan.memberCount.toString(), label = "Members", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        ClanCtaButton(
            cta = uiState.cta,
            isSubmitting = uiState.isSubmittingJoin,
            onRequestJoinClick = onRequestJoinClick,
            onManageClanClick = onManageClanClick
        )

        uiState.joinFeedback?.let { feedback ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = feedback,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun ClanCtaButton(
    cta: ClanMembershipCta,
    isSubmitting: Boolean,
    onRequestJoinClick: () -> Unit,
    onManageClanClick: () -> Unit
) {
    when (cta) {
        ClanMembershipCta.REQUEST_JOIN -> {
            Button(
                onClick = onRequestJoinClick,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LeaderBadgeColor,
                    contentColor = Color.Black
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text("Request Join", fontWeight = FontWeight.Bold)
                }
            }
        }
        ClanMembershipCta.PENDING -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Menunggu Persetujuan", fontWeight = FontWeight.Bold)
            }
        }
        ClanMembershipCta.BLOCKED_OTHER_CLAN -> {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Kamu Sudah Gabung Clan Lain", fontWeight = FontWeight.Bold)
            }
        }
        ClanMembershipCta.IS_MEMBER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Kamu member clan ini",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
        ClanMembershipCta.IS_LEADER -> {
            Button(
                onClick = onManageClanClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary)
            ) {
                Text("Kelola Clan", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun ClanStatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ClanTabRow(
    selectedTab: ClanTab,
    memberCount: Int,
    donorCountToday: Int,
    onTabSelected: (ClanTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ClanTabChip(
            text = "Members ($memberCount)",
            selected = selectedTab == ClanTab.MEMBERS,
            modifier = Modifier.weight(1f)
        ) { onTabSelected(ClanTab.MEMBERS) }

        ClanTabChip(
            text = "Donasi Hari Ini ($donorCountToday)",
            selected = selectedTab == ClanTab.DONATION_TODAY,
            modifier = Modifier.weight(1f)
        ) { onTabSelected(ClanTab.DONATION_TODAY) }
    }
}

@Composable
private fun ClanTabChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MemberSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Cari member (nama atau ID)") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun TodayDonationSummary(uiState: ClanUiState) {
    val today = remember(uiState.donationsToday) {
        DateTimeFormatter.ofPattern("yyyy-MM-dd").format(java.time.LocalDate.now())
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        Text(
            text = "HARI INI · $today",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                append(uiState.donorCountToday)
                append(" dari ")
                append(uiState.clan?.memberCount ?: 0)
                append(" member donasi = ")
                append(formatCompact(uiState.totalDonatedToday))
                append(" ZCoin terkumpul")
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MemberListItem(member: ClanMemberDisplay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(member = member, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.username,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            RoleBadge(role = member.role)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Gabung ${formatRelativeDate(member.joinedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        ContributionChip(amount = member.totalContribution)
    }
}

@Composable
private fun DonationListItem(rank: Int, entry: ClanDonationEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
        GeneratedAvatar(seed = entry.firebaseUid, label = entry.username, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.username,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            RoleBadge(role = entry.role)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${entry.donationCountToday}x donasi hari ini",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        ContributionChip(amount = entry.amountToday)
    }
}

@Composable
private fun RoleBadge(role: String) {
    val (label, color) = when (role) {
        "leader" -> "LEADER" to LeaderBadgeColor
        "co_leader" -> "CO-LEADER" to CoLeaderBadgeColor
        else -> "MEMBER" to MemberBadgeColor
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = if (role == "leader") Color.Black else Color.White
        )
    }
}

@Composable
private fun ContributionChip(amount: Long) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Diamond,
            contentDescription = null,
            tint = ZenimePrimary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatCompact(amount),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MemberAvatar(member: ClanMemberDisplay, size: androidx.compose.ui.unit.Dp) {
    if (member.avatarUrl != null) {
        AsyncImage(
            model = member.avatarUrl,
            contentDescription = member.username,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        GeneratedAvatar(seed = member.firebaseUid, label = member.username, size = size)
    }
}

@Composable
private fun ClanPhoto(photoUrl: String?, tag: String, size: androidx.compose.ui.unit.Dp) {
    if (photoUrl != null) {
        AsyncImage(
            model = photoUrl,
            contentDescription = tag,
            modifier = Modifier.size(size).clip(RoundedCornerShape(14.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
                .background(ZenimePrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(tag.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun TagPill(tag: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZenimePrimary)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = Color.White
        )
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
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("id", "ID"))
        formatter.withZone(ZoneId.systemDefault()).format(instant)
    } catch (e: Exception) {
        ""
    }
}
