package com.batmon.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.batmon.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String = ""
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        // Release without prerelease is newer than one with prerelease (e.g. 0.1.0 > 0.1.0-beta)
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1
        return prerelease.compareTo(other.prerelease)
    }

    companion object {
        // ponytail: simple semver parser; upgrade if build metadata (+build) or complex prerelease chains are needed
        fun parse(versionStr: String): AppVersion {
            val clean = versionStr.trim().removePrefix("v").removePrefix("V")
            val parts = clean.split("-", limit = 2)
            val numParts = parts[0].split(".")
            val major = numParts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = numParts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = numParts.getOrNull(2)?.toIntOrNull() ?: 0
            val prerelease = parts.getOrNull(1).orEmpty()
            return AppVersion(major, minor, patch, prerelease)
        }
    }
}

data class UpdateInfo(
    val tagName: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val isNewer: Boolean
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val apiBaseUrl: String = "https://api.github.com"
) {
    suspend fun checkForUpdate(
        repoOwner: String = "sunilsankar",
        repoName: String = "BatMon",
        currentVersion: String = BuildConfig.VERSION_NAME
    ): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$apiBaseUrl/repos/$repoOwner/$repoName/tags"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "BatMon-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("GitHub API returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val tagsArray = JSONArray(body)
                if (tagsArray.length() == 0) return@runCatching null

                val latestTag = tagsArray.getJSONObject(0).getString("name")
                val remoteVer = AppVersion.parse(latestTag)
                val currentVer = AppVersion.parse(currentVersion)
                val isNewer = remoteVer > currentVer

                UpdateInfo(
                    tagName = latestTag,
                    downloadUrl = "https://github.com/$repoOwner/$repoName/releases/download/$latestTag/batmon-$latestTag.apk",
                    releaseUrl = "https://github.com/$repoOwner/$repoName/releases/tag/$latestTag",
                    isNewer = isNewer
                )
            }
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "BatMon-App")
                .build()

            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(updateDir, "batmon-update.apk")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed with HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                val totalLength = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalLength > 0) {
                                onProgress(totalRead.toFloat() / totalLength)
                            }
                        }
                        output.flush()
                    }
                }
            }
            outputFile
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openReleaseInBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
