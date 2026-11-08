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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val avatarBitmapCache = LruCache<String, ImageBitmap>(4)

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
    val bitmap by produceState<ImageBitmap?>(
        initialValue = avatarPath?.takeIf { it.isNotBlank() }?.let { avatarBitmapCache.get(it) },
        key1 = avatarPath,
    ) {
        if (avatarPath.isNullOrBlank()) {
            value = null
            return@produceState
        }
        val cached = avatarBitmapCache.get(avatarPath)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(avatarPath)
                if (file.exists() && file.length() > 0) {
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                    val targetDim = 256
                    var sampleSize = 1
                    while (boundsOpts.outWidth / (sampleSize * 2) >= targetDim &&
                        boundsOpts.outHeight / (sampleSize * 2) >= targetDim
                    ) {
                        sampleSize *= 2
                    }
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()?.also { bmp ->
                        avatarBitmapCache.put(avatarPath, bmp)
                    }
                } else {
                    null
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
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
