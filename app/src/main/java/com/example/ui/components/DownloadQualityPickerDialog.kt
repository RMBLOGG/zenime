package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.data.model.StreamServer
import com.example.ui.theme.ZenimePrimary

/**
 * Dialog pilih kualitas sebelum mulai download episode buat offline.
 *
 * [options] null berarti masih loading (fetch server list), kosong sudah
 * ditangani di caller lewat [errorMessage]. Dipakai bareng di PlayerScreen
 * (episode yang lagi diputer) dan DetailScreen (episode list) -- makanya
 * dipisah jadi komponen shared di sini, bukan didefinisikan dua kali.
 */
@Composable
fun DownloadQualityPickerDialog(
    options: List<StreamServer>?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onQualitySelected: (StreamServer) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Kualitas Download") },
        text = {
            when {
                errorMessage != null -> {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
                options == null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
                else -> {
                    Column {
                        options.forEachIndexed { index, server ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onQualitySelected(server) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DownloadForOffline,
                                        contentDescription = null,
                                        tint = ZenimePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = server.quality ?: "Kualitas ${index + 1}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                if (index == 0) {
                                    // Opsi pertama = kualitas tertinggi (list udah
                                    // disortir desc di repository), kasih badge
                                    // biar user gampang milih "yang terbaik".
                                    Text(
                                        text = "Terbaik",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZenimePrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
