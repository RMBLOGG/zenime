package com.example.ui.screens.clan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ClanMemberDisplay
import com.example.data.model.PendingJoinRequestDisplay
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.GeneratedAvatar
import com.example.ui.theme.ZenimePrimary
import com.example.util.ClanPhotoUploader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageClanScreen(
    viewModel: ManageClanViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Kelola Clan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
                uiState.error != null && uiState.clan == null -> {
                    ErrorStateView(message = uiState.error!!, onRetry = viewModel::retry)
                }
                uiState.clan != null -> {
                    Column(Modifier.fillMaxSize()) {
                        ManageTabRow(
                            selectedTab = uiState.selectedTab,
                            pendingCount = uiState.pendingRequests.size,
                            memberCount = uiState.members.size,
                            onTabSelected = viewModel::onTabSelected
                        )
                        when (uiState.selectedTab) {
                            ManageClanTab.SETTINGS -> SettingsTab(
                                uiState = uiState,
                                onNameChange = viewModel::onNameInputChange,
                                onTagChange = viewModel::onTagInputChange,
                                onSaveClick = viewModel::saveSettings,
                                onPhotoPicked = { uri ->
                                    scope.launch {
                                        val clanId = uiState.clan?.id ?: return@launch
                                        runCatching {
                                            ClanPhotoUploader.uploadClanPhoto(context, uri, clanId)
                                        }.onSuccess { url -> viewModel.onPhotoUploaded(url) }
                                    }
                                }
                            )
                            ManageClanTab.REQUESTS -> RequestsTab(
                                requests = uiState.pendingRequests,
                                actionInFlightId = uiState.actionInFlightId,
                                onApprove = { viewModel.respondToRequest(it, true) },
                                onReject = { viewModel.respondToRequest(it, false) }
                            )
                            ManageClanTab.MEMBERS -> MembersTab(
                                members = uiState.members,
                                actionInFlightId = uiState.actionInFlightId,
                                onKick = viewModel::kickMember
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageTabRow(
    selectedTab: ManageClanTab,
    pendingCount: Int,
    memberCount: Int,
    onTabSelected: (ManageClanTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabChip("Settings", selectedTab == ManageClanTab.SETTINGS, Modifier.weight(1f)) { onTabSelected(ManageClanTab.SETTINGS) }
        TabChip("Request ($pendingCount)", selectedTab == ManageClanTab.REQUESTS, Modifier.weight(1f)) { onTabSelected(ManageClanTab.REQUESTS) }
        TabChip("Member ($memberCount)", selectedTab == ManageClanTab.MEMBERS, Modifier.weight(1f)) { onTabSelected(ManageClanTab.MEMBERS) }
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SettingsTab(
    uiState: ManageClanUiState,
    onNameChange: (String) -> Unit,
    onTagChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onPhotoPicked: (Uri) -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> if (uri != null) onPhotoPicked(uri) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { photoPicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val photoUrl = uiState.clan?.photoUrl
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Foto clan",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Ketuk buat ganti foto clan",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = uiState.nameInput,
            onValueChange = onNameChange,
            label = { Text("Nama Clan") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.tagInput,
            onValueChange = onTagChange,
            label = { Text("Tag") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        uiState.settingsFeedback?.let { feedback ->
            Spacer(Modifier.height(10.dp))
            Text(feedback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSaveClick,
            enabled = !uiState.isSavingSettings,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ZenimePrimary)
        ) {
            if (uiState.isSavingSettings) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Simpan", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun RequestsTab(
    requests: List<PendingJoinRequestDisplay>,
    actionInFlightId: String?,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    if (requests.isEmpty()) {
        EmptyStateView(title = "Gak Ada Request", description = "Belum ada yang minta gabung clan ini.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(requests, key = { it.requestId }) { req ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (req.avatarUrl != null) {
                    AsyncImage(model = req.avatarUrl, contentDescription = req.username, modifier = Modifier.size(40.dp))
                } else {
                    GeneratedAvatar(seed = req.firebaseUid, label = req.username, size = 40.dp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = req.username,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                val isBusy = actionInFlightId == req.requestId
                IconButton(onClick = { onReject(req.requestId) }, enabled = !isBusy) {
                    Icon(Icons.Filled.Close, contentDescription = "Tolak", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { onApprove(req.requestId) }, enabled = !isBusy) {
                    Icon(Icons.Filled.Check, contentDescription = "Terima", tint = ZenimePrimary)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MembersTab(
    members: List<ClanMemberDisplay>,
    actionInFlightId: String?,
    onKick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(members, key = { it.firebaseUid }) { member ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (member.avatarUrl != null) {
                    AsyncImage(model = member.avatarUrl, contentDescription = member.username, modifier = Modifier.size(40.dp))
                } else {
                    GeneratedAvatar(seed = member.firebaseUid, label = member.username, size = 40.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.username,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = member.role.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (member.role != "leader") {
                    val isBusy = actionInFlightId == member.firebaseUid
                    OutlinedButton(
                        onClick = { onKick(member.firebaseUid) },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Kick", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
