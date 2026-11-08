package com.sv.tnu.update

import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.sv.tnu.rust.RustScheduleCore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject
import org.tukaani.xz.XZInputStream

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val packageUrl: String,
    val compression: String,
    val sha256: String,
    val sizeBytes: Long?,
    val changelog: String,
)

data class AppUpdateCheckResult(
    val update: AppUpdateInfo? = null,
    val currentVersionCode: Long,
    val currentVersionName: String,
    val error: String? = null,
)

data class AppUpdateDownloadResult(
    val uri: Uri? = null,
    val file: File? = null,
    val error: String? = null,
)

object AppUpdateManager {
    private const val UPDATE_JSON_URL = "https://ngocminhvn.github.io/tkb/update.json"
    private const val EXPECTED_UPDATE_PREFIX = "https://ngocminhvn.github.io/tkb/"

    fun getAppVersion(context: Context): Pair<Long, String> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val currentCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val currentName = packageInfo.versionName ?: currentCode.toString()
        return Pair(currentCode, currentName)
    }

    fun check(context: Context): AppUpdateCheckResult {
        val (currentCode, currentName) = getAppVersion(context)

        return try {
            val endpoint = UPDATE_JSON_URL + "?t=" + System.currentTimeMillis()
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 12_000
                readTimeout = 20_000
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "TKB-SV-Updater/$currentName")
            }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            connection.disconnect()

            if (code !in 200..299) {
                return AppUpdateCheckResult(
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    error = "HTTP $code khi đọc thông tin cập nhật.",
                )
            }

            val json = JSONObject(body)
            val remoteCode = json.optLong("versionCode", -1L)
            val remoteName = json.optString("versionName").trim()
            val apkUrl = json.optString("apkUrl").trim()
            val packageUrl = json.optString("packageUrl").trim().ifBlank { apkUrl }
            val compression = json.optString("compression")
                .trim()
                .lowercase(Locale.ROOT)
                .ifBlank { "none" }
            val legacySha256 = json.optString("sha256").trim().lowercase(Locale.ROOT)
            val sha256 = json.optString("packageSha256")
                .trim()
                .lowercase(Locale.ROOT)
                .ifBlank { legacySha256 }
            val legacySizeBytes = json.optLong("sizeBytes", -1L).takeIf { it > 0L }
            val sizeBytes = json.optLong("packageSizeBytes", -1L)
                .takeIf { it > 0L }
                ?: legacySizeBytes
            val changelog = json.optString("changelog").trim()

            when {
                remoteCode <= 0L ->
                    AppUpdateCheckResult(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        error = "update.json thiếu versionCode hợp lệ.",
                    )

                remoteName.isBlank() ->
                    AppUpdateCheckResult(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        error = "update.json thiếu versionName.",
                    )

                !packageUrl.startsWith(EXPECTED_UPDATE_PREFIX) ->
                    AppUpdateCheckResult(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        error = "Nguồn cập nhật TKB SV không hợp lệ.",
                    )

                compression !in setOf("none", "xz") ->
                    AppUpdateCheckResult(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        error = "Kiểu nén cập nhật không được hỗ trợ.",
                    )

                !sha256.matches(Regex("^[0-9a-f]{64}$")) ->
                    AppUpdateCheckResult(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                        error = "update.json thiếu SHA-256 hợp lệ.",
                    )

                remoteCode <= currentCode ->
                    AppUpdateCheckResult(
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                    )

                else ->
                    AppUpdateCheckResult(
                        update = AppUpdateInfo(
                            versionCode = remoteCode,
                            versionName = remoteName,
                            apkUrl = apkUrl,
                            packageUrl = packageUrl,
                            compression = compression,
                            sha256 = sha256,
                            sizeBytes = sizeBytes,
                            changelog = changelog.ifBlank { "Bản cập nhật TKB SV mới." },
                        ),
                        currentVersionCode = currentCode,
                        currentVersionName = currentName,
                    )
            }
        } catch (t: Throwable) {
            AppUpdateCheckResult(
                currentVersionCode = currentCode,
                currentVersionName = currentName,
                error = t.message ?: t.javaClass.simpleName,
            )
        }
    }

    fun download(
        context: Context,
        update: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): AppUpdateDownloadResult {
        val safeVersion = update.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val tempFile = File(context.cacheDir, "TKB-SV-${safeVersion}.part")
        val destinationApk = File(context.cacheDir, "TKB-SV-${safeVersion}.apk")

        return try {
            tempFile.delete()
            destinationApk.delete()

            val connection = (URL(update.packageUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 120_000
                useCaches = false
                setRequestProperty("Accept", "application/octet-stream,*/*")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "TKB-SV-Updater/${update.versionName}")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                connection.disconnect()
                return AppUpdateDownloadResult(error = "Tải bản cập nhật thất bại (HTTP $code)")
            }

            val totalBytes = update.sizeBytes ?: connection.contentLengthLong.takeIf { it > 0L }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(256 * 1024)
            var downloaded = 0L
            var lastProgressAt = 0L

            onProgress(0L, totalBytes)
            connection.inputStream.use { input ->
                tempFile.outputStream().buffered(256 * 1024).use { out ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count

                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastProgressAt >= 120L || (totalBytes != null && downloaded >= totalBytes)) {
                            onProgress(downloaded, totalBytes)
                            lastProgressAt = now
                        }
                    }
                    out.flush()
                }
            }
            connection.disconnect()

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
            if (!actualSha256.equals(update.sha256, ignoreCase = true)) {
                tempFile.delete()
                return AppUpdateDownloadResult(error = "Mã SHA-256 không khớp. File tải đã bị hủy.")
            }

            // Decompress / copy to destination APK
            destinationApk.outputStream().buffered(256 * 1024).use { out ->
                when (update.compression) {
                    "xz" -> XZInputStream(tempFile.inputStream().buffered(256 * 1024)).use { input ->
                        input.copyTo(out, 256 * 1024)
                    }
                    else -> tempFile.inputStream().buffered(256 * 1024).use { input ->
                        input.copyTo(out, 256 * 1024)
                    }
                }
                out.flush()
            }

            // Save to MediaStore.Downloads or fallback to FileProvider
            var targetUri: Uri? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "TKB-SV-${safeVersion}.apk")
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/TKB-SV")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    targetUri = context.contentResolver.insert(collection, values)
                    if (targetUri != null) {
                        context.contentResolver.openOutputStream(targetUri)?.use { out ->
                            destinationApk.inputStream().buffered().use { input ->
                                input.copyTo(out)
                            }
                            out.flush()
                        }
                        val done = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        context.contentResolver.update(targetUri, done, null, null)
                    }
                } catch (_: Throwable) {
                    targetUri = null
                }
            }

            if (targetUri == null) {
                targetUri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        destinationApk,
                    )
                } catch (_: Throwable) {
                    null
                }
            }

            onProgress(downloaded, totalBytes)
            AppUpdateDownloadResult(uri = targetUri, file = destinationApk)
        } catch (t: Throwable) {
            destinationApk.delete()
            AppUpdateDownloadResult(error = t.message ?: t.javaClass.simpleName)
        } finally {
            tempFile.delete()
        }
    }

    fun installApk(context: Context, downloadResult: AppUpdateDownloadResult): String? {
        val uri = downloadResult.uri ?: downloadResult.file?.let { file ->
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (t: Throwable) {
                null
            }
        } ?: return "Không tìm thấy file APK để cài đặt."

        val clip = ClipData.newUri(context.contentResolver, "TKB-SV update", uri)
        var lastError: String? = null

        for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE)) {
            val intent = Intent(action).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return null
            } catch (t: android.content.ActivityNotFoundException) {
                lastError = t.message ?: "Không tìm thấy ứng dụng cài đặt APK."
            } catch (t: SecurityException) {
                lastError = t.message ?: "Trình cài đặt từ chối quyền đọc APK."
            } catch (t: Throwable) {
                lastError = t.message ?: "Lỗi khởi chạy trình cài đặt APK."
            }
        }

        return "Không mở được trình cài đặt: ${lastError ?: "Lỗi không xác định"}"
    }

    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        return RustScheduleCore.formatBytes(bytes)
    }
}
