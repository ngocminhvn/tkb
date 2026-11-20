@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sv.tnu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import retrofit2.HttpException

/** Preserve a useful technical reason without reducing all failures to "lỗi mạng". */
internal fun describeTimetableSyncError(cause: Throwable): String {
    val detail = cause.message?.trim()?.take(400).orEmpty()
    return when (cause) {
        is HttpException -> when (cause.code()) {
            401 -> "HTTP 401: Phiên đăng nhập hết hạn hoặc không hợp lệ."
            403 -> "HTTP 403: Máy chủ từ chối quyền truy cập dữ liệu."
            404 -> "HTTP 404: Không tìm thấy API thời khóa biểu."
            429 -> "HTTP 429: Máy chủ giới hạn yêu cầu. Hãy thử lại sau."
            in 500..599 -> "HTTP ${cause.code()}: Máy chủ TNUT đang gặp lỗi. ${detail}"
            else -> "HTTP ${cause.code()}: ${detail.ifBlank { "Yêu cầu bị từ chối." }}"
        }
        is SocketTimeoutException -> "Hết thời gian chờ kết nối hoặc phản hồi từ portal TNUT. ${detail}"
        is UnknownHostException -> "Không tìm thấy máy chủ portal TNUT. Kiểm tra Wi-Fi/DNS. ${detail}"
        is ConnectException -> "Không kết nối được tới portal TNUT. ${detail}"
        is SSLException -> "Kết nối bảo mật SSL/TLS tới portal không thành công. ${detail}"
        else -> detail.ifBlank {
            "Không đồng bộ được (${cause.javaClass.simpleName}). Hãy thử lại sau."
        }
    }.trim()
}

/** A centered result dialog for user-initiated sync only. Automatic sync is silent. */
@Composable
fun TimetableSyncDialog(
    status: TimetableSyncStatus,
    onDismiss: () -> Unit,
) {
    val loading = status is TimetableSyncStatus.Loading
    val warning = (status as? TimetableSyncStatus.Success)?.examWarning
    val failure = (status as? TimetableSyncStatus.Failure)?.detail

    if (loading) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ContainedLoadingIndicator(modifier = Modifier.size(64.dp))
            }
        }
        return
    }

    if (warning == null && failure == null) {
        // Đồng bộ thành công không có cảnh báo: tự động đóng loading
        LaunchedEffect(Unit) {
            onDismiss()
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            val icon = if (failure != null) Icons.Default.ErrorOutline else Icons.Default.WarningAmber
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (failure != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = if (failure != null) "Đồng bộ thất bại" else "Đồng bộ chưa hoàn tất",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (failure != null) {
                    Text(
                        text = failure,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (warning != null) {
                    Text("Lịch học đã được đồng bộ và lưu vào máy.")
                    Text(
                        "Lịch thi chưa đồng bộ đầy đủ: $warning",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),onClick = onDismiss) {
                Text("Đóng")
            }
        },
    )
}
