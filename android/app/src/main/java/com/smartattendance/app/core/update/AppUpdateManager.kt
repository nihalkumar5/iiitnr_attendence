package com.smartattendance.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long,
    val isNewer: Boolean
)

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val GITHUB_REPO = "nihalkumar5/iiitnr_attendence"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Checks GitHub Releases API for the latest release and compares with the currently installed version.
     */
    suspend fun checkForAppUpdate(context: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(LATEST_RELEASE_API)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "SmartAttendance-Android")
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            if (!res.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API returned HTTP ${res.code}"))
            }

            val body = res.body?.string() ?: return@withContext Result.failure(Exception("Empty release response"))
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val releaseTitle = json.optString("name", "New Release")
            val releaseNotes = json.optString("body", "Bug fixes and performance improvements.")

            val remoteCleanVersion = tagName.trimStart('v', 'V').trim()
            val currentVersion = getCurrentVersionName(context)

            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            var apkSize = 0L

            if (assets != null) {
                // Prioritize 'smartattendance.apk', then fallback to any .apk asset
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.equals("smartattendance.apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    } else if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isBlank()) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                    }
                }
            }

            if (downloadUrl.isBlank()) {
                downloadUrl = "https://github.com/$GITHUB_REPO/releases/latest/download/smartattendance.apk"
            }

            val isNewer = isNewerVersion(remoteCleanVersion, currentVersion)

            val info = UpdateInfo(
                tagName = tagName,
                versionName = remoteCleanVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                apkSize = apkSize,
                isNewer = isNewer
            )

            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads the APK file with real-time progress reporting and automatically launches the Android Package Installer.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", "SmartAttendance-Android")
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            if (!res.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download APK: HTTP ${res.code}"))
            }

            val body = res.body ?: return@withContext Result.failure(Exception("Empty APK download body"))
            val totalBytes = body.contentLength()

            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            val apkFile = File(updatesDir, "smartattendance_update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            body.byteStream().use { inputStream ->
                FileOutputStream(apkFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    outputStream.flush()
                }
            }

            onProgress(1.0f)

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Launches the system package installer for the downloaded APK using Android FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
        }
    }

    /**
     * Retrieves the installed app version name.
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    /**
     * Compares remote and local semantic versioning strings (e.g., 1.0.2 vs 1.0.1).
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote.isBlank()) return false
        if (current.isBlank()) return true

        val remoteParts = remote.split(".").mapNotNull { it.filter { ch -> ch.isDigit() }.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.filter { ch -> ch.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
