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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.data.model.ZenimeRole
import com.example.data.model.defaultBadgeColorHex
import com.example.ui.theme.CardOutlineBorder
import com.example.ui.theme.ZenimeBackgroundDark
import com.example.ui.theme.ZenimePrimary
import com.example.ui.theme.ZenimeSurfaceDark

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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            RoleBadgeChip(role = role, badgeColorHex = null)
            Spacer(modifier = Modifier.height(16.dp))
        }

        uiState.actionSuccessMessage?.let { msg ->
            item { FeedbackCard(text = msg, isError = false) }
        }
        uiState.actionError?.let { msg ->
            item { FeedbackCard(text = msg, isError = true) }
        }

        if (role == ZenimeRole.DEVELOPER) {
            item { SectionTitle("Kelola Role") }
            item { AssignRoleForm(isProcessing = uiState.isProcessing, viewModel = viewModel) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { SectionTitle("Daftar Pemegang Role") }
            if (uiState.isLoadingRoleList) {
                item {
                    CircularProgressIndicator(
                        color = ZenimePrimary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(uiState.roleList) { entry ->
                    RoleListItem(entry = entry, onRemove = { viewModel.removeRole(entry.firebaseUid) })
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item { SectionTitle("Ban Device") }
            item {
                BanForm(
                    label = "Ban device dari akun ini (target gak akan bisa daftar/login lagi dari HP yang sama)",
                    buttonText = "Ban Device",
                    isProcessing = uiState.isProcessing,
                    onSubmit = { uid, reason -> viewModel.banDevice(uid, reason) }
                )
            }
        }

        if (role == ZenimeRole.ADMIN || role == ZenimeRole.DEVELOPER) {
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item { SectionTitle("Hapus Pesan Chat") }
            item {
                Text(
                    "Buat hapus pesan orang lain, tahan (long-press) pesannya langsung di Chat Global -- opsi \"Hapus\" bakal muncul otomatis buat akun admin/developer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        if (role == ZenimeRole.MODERATOR || role == ZenimeRole.DEVELOPER) {
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item { SectionTitle("Ban Akun") }
            item {
                BanForm(
                    label = "Ban akun (uid) ini -- target gak bisa login lagi walau ganti device",
                    buttonText = "Ban Akun",
                    isProcessing = uiState.isProcessing,
                    onSubmit = { uid, reason -> viewModel.banUser(uid, reason) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
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
private fun FeedbackCard(text: String, isError: Boolean) {
    Box(
        modifier = Modifier
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
private fun RoleListItem(entry: RoleListEntry, onRemove: () -> Unit) {
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
        Text(
            "Cabut",
            color = Color(0xFFE57373),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onRemove).padding(8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignRoleForm(isProcessing: Boolean, viewModel: AdminViewModel) {
    var uidInput by remember { mutableStateOf("") }
    var badgeColorInput by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(ZenimeRole.MODERATOR) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ZenimeSurfaceDark)
            .border(1.dp, CardOutlineBorder.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        OutlinedTextField(
            value = uidInput,
            onValueChange = { uidInput = it },
            label = { Text("Firebase UID target") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

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
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.assignRole(
                    uidInput.trim(),
                    selectedRole,
                    badgeColorInput.trim().takeIf { it.isNotBlank() }
                )
            },
            enabled = !isProcessing && uidInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isProcessing) "Memproses..." else "Set Role")
        }
    }
}

@Composable
private fun BanForm(
    label: String,
    buttonText: String,
    isProcessing: Boolean,
    onSubmit: (uid: String, reason: String?) -> Unit
) {
    var uidInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ZenimeSurfaceDark)
            .border(1.dp, CardOutlineBorder.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        OutlinedTextField(
            value = uidInput,
            onValueChange = { uidInput = it },
            label = { Text("Firebase UID target") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = reasonInput,
            onValueChange = { reasonInput = it },
            label = { Text("Alasan (opsional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { onSubmit(uidInput.trim(), reasonInput.trim().takeIf { it.isNotBlank() }) },
            enabled = !isProcessing && uidInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isProcessing) "Memproses..." else buttonText)
        }
    }
}
