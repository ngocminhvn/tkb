package com.sv.tnu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sv.tnu.update.AppUpdateDownloadResult
import com.sv.tnu.update.AppUpdateInfo
import com.sv.tnu.update.AppUpdateManager
import com.sv.tnu.ui.glass.GlassTextButton

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo?,
    currentVersionName: String,
    downloading: Boolean,
    downloadedBytes: Long,
    totalBytes: Long?,
    downloadResult: AppUpdateDownloadResult?,
    error: String?,
    onDismiss: () -> Unit,
    onStartDownload: (AppUpdateInfo) -> Unit,
) {
    if (updateInfo == null) return
    val context = LocalContext.current

    val total = totalBytes ?: updateInfo.sizeBytes
    val progress = if (total != null && total > 0L) {
        (downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val percent = progress?.let { (it * 100f).toInt().coerceIn(0, 100) }

    AlertDialog(
        onDismissRequest = {
            if (!downloading) onDismiss()
        },
        icon = {
            Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(
                when {
                    downloading -> "Đang tải bản cập nhật"
                    downloadResult?.file != null || downloadResult?.uri != null -> "Sẵn sàng cài đặt"
                    error != null -> "Cập nhật thất bại"
                    else -> "Có bản cập nhật mới!"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${currentVersionName.ifBlank { "Bản hiện tại" }} → ${updateInfo.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = updateInfo.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when {
                    downloading -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${percent ?: 0}%",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    text = "${AppUpdateManager.formatBytes(downloadedBytes)} / ${AppUpdateManager.formatBytes(total)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = AppUpdateManager.formatBytes(downloadedBytes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    downloadResult?.file != null || downloadResult?.uri != null -> {
                        Text(
                            text = "Bản cập nhật đã tải xong và xác thực SHA-256 an toàn. Nhấn Cài đặt để cập nhật ứng dụng.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    error != null -> {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    else -> {
                        Text(
                            text = "Gói cập nhật được tải trực tiếp từ GitHub Releases của SvTNU và kiểm tra SHA-256 trước khi cài đặt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (!downloading) {
                GlassTextButton(text = "Để sau", onClick = onDismiss)
            }
        },
        confirmButton = {
            when {
                downloading -> {
                    GlassTextButton(
                        text = "${percent ?: 0}%",
                        enabled = false,
                        loading = false,
                        onClick = {},
                    )
                }

                downloadResult?.file != null || downloadResult?.uri != null -> {
                    GlassTextButton(
                        text = "Cài đặt ngay",
                        onClick = { AppUpdateManager.installApk(context, downloadResult) },
                    )
                }

                else -> {
                    GlassTextButton(
                        text = "Cập nhật ngay",
                        onClick = { onStartDownload(updateInfo) },
                    )
                }
            }
        },
    )
}
