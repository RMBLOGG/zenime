package com.example.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.RoleListEntry
import com.example.data.model.UserListEntry
import com.example.data.model.ZenimeRole
import com.example.data.model.defaultBadgeColorHex
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeBackgroundDark
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimeSurfaceDark

private val BanRed = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = ZenimeBackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Panel Admin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ZenimeBackgroundDark)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoadingMyRole -> {
                    CircularProgressIndicator(
                        color = ZenimePrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.myRole == null -> {
                    Text(
                        "Kamu gak punya akses ke halaman ini.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }
                else -> {
                    AdminScreenContent(role = uiState.myRole!!, uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun AdminScreenContent(
    role: ZenimeRole,
    uiState: AdminUiState,
    viewModel: AdminViewModel
) {
    val tabs = remember(role) {
        buildList {
            add("Semua User")
            if (role == ZenimeRole.DEVELOPER) add("Pemegang Role")
            add("Info")
        }
    }
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        RoleBadgeChip(
            role = role,
            badgeColorHex = null,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        uiState.actionSuccessMessage?.let { msg ->
            FeedbackCard(text = msg, isError = false, modifier = Modifier.padding(horizontal = 16.dp))
        }
        uiState.actionError?.let { msg ->
            FeedbackCard(text = msg, isError = true, modifier = Modifier.padding(horizontal = 16.dp))
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = ZenimeBackgroundDark,
            contentColor = ZenimePrimary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (tabs[selectedTab]) {
            "Semua User" -> UserListTab(role = role, uiState = uiState, viewModel = viewModel)
            "Pemegang Role" -> RoleHoldersTab(uiState = uiState)
            else -> InfoTab(role = role)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserListTab(
    role: ZenimeRole,
    uiState: AdminUiState,
    viewModel: AdminViewModel
) {
    var roleDialogTarget by remember { mutableStateOf<UserListEntry?>(null) }
    var banDeviceTarget by remember { mutableStateOf<UserListEntry?>(null) }
    var banAccountTarget by remember { mutableStateOf<UserListEntry?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = uiState.userSearchQuery,
            onValueChange = { viewModel.onUserSearchQueryChange(it) },
            placeholder = { Text("Cari nama, kode Zenime, atau UID...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        when {
            uiState.isLoadingUserList -> {
                CircularProgressIndicator(
                    color = ZenimePrimary,
                    modifier = Modifier.padding(24.dp)
                )
            }
            uiState.userListError != null -> {
                Text(
                    uiState.userListError,
                    color = BanRed,
                    modifier = Modifier.padding(16.dp)
                )
            }
            uiState.userList.isEmpty() -> {
                Text(
                    "Gak ada user ditemukan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(uiState.userList) { user ->
                        UserRow(
                            user = user,
                            isSelf = user.firebaseUid == viewModel.currentUid(),
                            myRole = role,
                            onSetRole = { roleDialogTarget = user },
                            onBanDevice = { banDeviceTarget = user },
                            onUnbanDevice = { user.lastDeviceId?.let { viewModel.unbanDevice(it) } },
                            onBanAccount = { banAccountTarget = user },
                            onUnbanAccount = { viewModel.unbanUser(user.firebaseUid) }
                        )
                    }
                    if (uiState.userListHasMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                if (uiState.isLoadingMoreUsers) {
                                    CircularProgressIndicator(color = ZenimePrimary, modifier = Modifier.size(24.dp))
                                } else {
                                    OutlinedButton(onClick = { viewModel.loadMoreUsers() }) {
                                        Text("Muat lebih banyak")
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    roleDialogTarget?.let { user ->
        RoleAssignDialog(
            user = user,
            isProcessing = uiState.isProcessing,
            onDismiss = { roleDialogTarget = null },
            onConfirm = { newRole, badgeColor ->
                viewModel.assignRole(user.firebaseUid, newRole, badgeColor)
                roleDialogTarget = null
            },
            onRemoveRole = {
                viewModel.removeRole(user.firebaseUid)
                roleDialogTarget = null
            }
        )
    }

    banDeviceTarget?.let { user ->
        BanReasonDialog(
            title = "Ban device -- ${user.username ?: user.firebaseUid}",
            description = "Target gak akan bisa daftar/login lagi dari HP yang sama.",
            confirmColor = BanRed,
            onDismiss = { banDeviceTarget = null },
            onConfirm = { reason ->
                viewModel.banDevice(user.firebaseUid, reason)
                banDeviceTarget = null
            }
        )
    }

    banAccountTarget?.let { user ->
        BanReasonDialog(
            title = "Ban akun -- ${user.username ?: user.firebaseUid}",
            description = "Target gak bisa login lagi walau ganti device.",
            confirmColor = BanRed,
            onDismiss = { banAccountTarget = null },
            onConfirm = { reason ->
                viewModel.banUser(user.firebaseUid, reason)
                banAccountTarget = null
            }
        )
    }
}

@Composable
private fun UserRow(
    user: UserListEntry,
    isSelf: Boolean,
    myRole: ZenimeRole,
    onSetRole: () -> Unit,
    onBanDevice: () -> Unit,
    onUnbanDevice: () -> Unit,
    onBanAccount: () -> Unit,
    onUnbanAccount: () -> Unit
) {
    val entryRole = ZenimeRole.fromValue(user.role)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ZenimeSurfaceDark)
            .border(1.dp, CardOutlineBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.username ?: user.firebaseUid,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                user.zenimeCode?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isSelf) {
                Text(
                    "Kamu",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZenimePrimary
                )
            }
        }

        if (entryRole != null || user.bannedAccount || user.bannedDevice) {
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                entryRole?.let {
                    RoleBadgeChip(role = it, badgeColorHex = user.badgeColor)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (user.bannedAccount) StatusChip("Diban (akun)")
                if (user.bannedDevice) {
                    if (user.bannedAccount) Spacer(modifier = Modifier.width(6.dp))
                    StatusChip("Diban (device)")
                }
            }
        }

        if (!isSelf) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (myRole == ZenimeRole.DEVELOPER) {
                    ActionChip(
                        text = if (entryRole != null) "Ubah Role" else "Set Role",
                        onClick = onSetRole
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ActionChip(
                        text = if (user.bannedDevice) "Unban Device" else "Ban Device",
                        onClick = if (user.bannedDevice) onUnbanDevice else onBanDevice,
                        isDestructive = !user.bannedDevice
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (myRole == ZenimeRole.MODERATOR || myRole == ZenimeRole.DEVELOPER) {
                    ActionChip(
                        text = if (user.bannedAccount) "Unban Akun" else "Ban Akun",
                        onClick = if (user.bannedAccount) onUnbanAccount else onBanAccount,
                        isDestructive = !user.bannedAccount
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(8.dp))
            .background((if (isDestructive) BanRed else ZenimePrimary).copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDestructive) Color(0xFFE57373) else ZenimePrimary
        )
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BanRed.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xFFE57373))
    }
}

@Composable
private fun RoleHoldersTab(uiState: AdminUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (uiState.isLoadingRoleList) {
            item {
                CircularProgressIndicator(
                    color = ZenimePrimary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (uiState.roleList.isEmpty()) {
            item {
                Text(
                    "Belum ada pemegang role.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(uiState.roleList) { entry ->
                RoleListItemView(entry = entry)
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun InfoTab(role: ZenimeRole) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (role == ZenimeRole.ADMIN || role == ZenimeRole.DEVELOPER) {
            SectionTitle("Hapus Pesan Chat")
            Text(
                "Buat hapus pesan orang lain, tahan (long-press) pesannya langsung di Chat Global -- opsi \"Hapus\" bakal muncul otomatis buat akun admin/developer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
        SectionTitle("Tentang tombol di tab \"Semua User\"")
        Text(
            "Cari user lewat username, kode Zenime (ZN-XXXXXX), atau UID, lalu " +
                "pencet tombol aksi langsung di barisnya -- gak perlu ketik/salin UID manual lagi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 10.dp, top = 4.dp)
    )
}

@Composable
private fun FeedbackCard(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background((if (isError) Color(0xFFE53935) else Color(0xFF43A047)).copy(alpha = 0.14f))
            .padding(12.dp)
    ) {
        Text(
            text,
            color = if (isError) Color(0xFFE57373) else Color(0xFF81C784),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Badge centang + label role, warna sesuai [defaultBadgeColorHex] atau custom. */
@Composable
fun RoleBadgeChip(role: ZenimeRole, badgeColorHex: String?, modifier: Modifier = Modifier) {
    val color = remember(role, badgeColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(badgeColorHex ?: role.defaultBadgeColorHex())) }
            .getOrDefault(Color(android.graphics.Color.parseColor(role.defaultBadgeColorHex())))
    }
    val label = when (role) {
        ZenimeRole.DEVELOPER -> "Developer"
        ZenimeRole.ADMIN -> "Admin"
        ZenimeRole.MODERATOR -> "Moderator"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(Icons.Filled.Verified, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun RoleListItemView(entry: RoleListEntry) {
    val role = ZenimeRole.fromValue(entry.role) ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ZenimeSurfaceDark)
            .border(1.dp, CardOutlineBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.username ?: entry.firebaseUid,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            RoleBadgeChip(role = role, badgeColorHex = entry.badgeColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleAssignDialog(
    user: UserListEntry,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ZenimeRole, String?) -> Unit,
    onRemoveRole: () -> Unit
) {
    var selectedRole by remember { mutableStateOf(ZenimeRole.fromValue(user.role) ?: ZenimeRole.MODERATOR) }
    var badgeColorInput by remember { mutableStateOf(user.badgeColor ?: "") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Role -- ${user.username ?: user.firebaseUid}") },
        text = {
            Column {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = when (selectedRole) {
                            ZenimeRole.DEVELOPER -> "Developer"
                            ZenimeRole.ADMIN -> "Admin"
                            ZenimeRole.MODERATOR -> "Moderator"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(ZenimeRole.DEVELOPER, ZenimeRole.ADMIN, ZenimeRole.MODERATOR).forEach { r ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (r) {
                                            ZenimeRole.DEVELOPER -> "Developer"
                                            ZenimeRole.ADMIN -> "Admin"
                                            ZenimeRole.MODERATOR -> "Moderator"
                                        }
                                    )
                                },
                                onClick = { selectedRole = r; expanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = badgeColorInput,
                    onValueChange = { badgeColorInput = it },
                    label = { Text("Warna badge custom (opsional, hex #RRGGBB)") },
                    singleLine = true,
                    placeholder = { Text("Kosongkan buat pakai warna default role") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (user.role != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Cabut Role",
                        color = BanRed,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable(onClick = onRemoveRole)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedRole, badgeColorInput.trim().takeIf { it.isNotBlank() }) },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary)
            ) {
                Text(if (isProcessing) "Memproses..." else "Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun BanReasonDialog(
    title: String,
    description: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (reason: String?) -> Unit
) {
    var reasonInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("Alasan (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reasonInput.trim().takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
            ) {
                Text("Konfirmasi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
