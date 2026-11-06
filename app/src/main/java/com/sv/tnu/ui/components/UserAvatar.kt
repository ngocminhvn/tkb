package com.sv.tnu.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Hiển thị ảnh đại diện sinh viên được tải từ portal.
 * Nếu chưa có ảnh hoặc lỗi, hiển thị icon mặc định trong khung tròn.
 */
@Composable
fun UserAvatar(
    avatarPath: String?,
    defaultIcon: ImageVector,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    contentDescription: String? = "Ảnh đại diện",
) {
    val bitmap = remember(avatarPath) {
        if (!avatarPath.isNullOrBlank()) {
            val file = File(avatarPath)
            if (file.exists() && file.length() > 0) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else null
        } else null
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = defaultIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
