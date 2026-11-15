package com.joaohouto.clusterlauncher.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.joaohouto.clusterlauncher.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val currentVersion: String) : UpdateStatus
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateStatus
    data class Downloading(
        val release: ReleaseInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateStatus
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : UpdateStatus
    data class Error(val message: String, val release: ReleaseInfo? = null) : UpdateStatus
}

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/joaohouto/clusterlauncher/releases/latest"
    private const val AUTO_CHECK_COOLDOWN_MS = 30 * 60 * 1000L // 30 minutes

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private var lastCheckTimestampMs: Long = 0L
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var pendingInstallFile: File? = null

    val hasPendingUpdate: Boolean
        get() {
            val s = _updateStatus.value
            return s is UpdateStatus.UpdateAvailable || s is UpdateStatus.ReadyToInstall
        }

    fun checkForUpdates(context: Context, isManual: Boolean = false) {
        val currentStatus = _updateStatus.value
        if (currentStatus is UpdateStatus.Checking || currentStatus is UpdateStatus.Downloading) {
            return
        }

        val now = System.currentTimeMillis()
        if (!isManual && lastCheckTimestampMs > 0L && (now - lastCheckTimestampMs) < AUTO_CHECK_COOLDOWN_MS) {
            return
        }

        checkJob?.cancel()
        checkJob = scope.launch {
            _updateStatus.value = UpdateStatus.Checking
            try {
                val jsonText = fetchLatestReleaseJson()
                lastCheckTimestampMs = System.currentTimeMillis()
                val release = parseLatestReleaseJson(jsonText)
                val currentVersion = normalizeVersionString(BuildConfig.VERSION_NAME)

                if (release != null && isNewerVersion(release.versionName, currentVersion)) {
                    // Check if APK was already downloaded in cache
                    val cachedApk = getTargetApkFile(context, release.versionName)
                    if (cachedApk.exists() && cachedApk.length() > 1024 * 1024 &&
                        (release.apkSizeBytes <= 0L || cachedApk.length() == release.apkSizeBytes)
                    ) {
                        _updateStatus.value = UpdateStatus.ReadyToInstall(release, cachedApk)
                    } else {
                        _updateStatus.value = UpdateStatus.UpdateAvailable(release)
                    }
                } else {
                    if (isManual) {
                        _updateStatus.value = UpdateStatus.UpToDate(currentVersion)
                    } else {
                        _updateStatus.value = UpdateStatus.Idle
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check for updates", e)
                if (isManual) {
                    _updateStatus.value = UpdateStatus.Error(
                        message = e.localizedMessage ?: "Falha de conexão com o GitHub"
                    )
                } else {
                    _updateStatus.value = UpdateStatus.Idle
                }
            }
        }
    }

    fun downloadAndInstall(context: Context, release: ReleaseInfo) {
        val appCtx = context.applicationContext
        if (_updateStatus.value is UpdateStatus.Downloading) return

        downloadJob?.cancel()
        downloadJob = scope.launch {
            val targetFile = getTargetApkFile(appCtx, release.versionName)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
            try {
                _updateStatus.value = UpdateStatus.Downloading(
                    release = release,
                    progressPercent = 0,
                    downloadedBytes = 0L,
                    totalBytes = release.apkSizeBytes
                )

                var currentUrl = release.apkDownloadUrl
                var redirects = 0
                var conn: HttpURLConnection
                while (true) {
                    conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "ClusterLauncher/${BuildConfig.VERSION_NAME}")
                    }
                    val code = conn.responseCode
                    if (code in 300..399 && redirects < 5) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (!location.isNullOrBlank()) {
                            currentUrl = location
                            redirects++
                            continue
                        }
                    }
                    if (code !in 200..299) {
                        conn.disconnect()
                        throw IllegalStateException("HTTP $code ao baixar APK")
                    }
                    break
                }

                val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
                targetFile.parentFile?.mkdirs()
                if (tempFile.exists()) tempFile.delete()

                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var downloaded = 0L
                        var lastReportedPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val percent = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                _updateStatus.value = UpdateStatus.Downloading(
                                    release = release,
                                    progressPercent = percent,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes
                                )
                            }
                        }
                        output.flush()
                    }
                }
                conn.disconnect()

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                _updateStatus.value = UpdateStatus.ReadyToInstall(release, targetFile)
                withContext(Dispatchers.Main) {
                    installApk(context, targetFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading update APK", e)
                if (tempFile.exists()) tempFile.delete()
                _updateStatus.value = UpdateStatus.Error(
                    message = e.localizedMessage ?: "Erro ao baixar atualização",
                    release = release
                )
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: Exception) {
                true
            }
            if (!canInstall) {
                pendingInstallFile = apkFile
                try {
                    val permIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permIntent)
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open ACTION_MANAGE_UNKNOWN_APP_SOURCES", e)
                }
            }
        }

        pendingInstallFile = null
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
        }
    }

    fun onActivityResumed(context: Context) {
        val pending = pendingInstallFile ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = try {
                context.packageManager.canRequestPackageInstalls()
            } catch (_: Exception) {
                false
            }
            if (canInstall && pending.exists()) {
                pendingInstallFile = null
                installApk(context, pending)
            }
        }
    }

    private fun getTargetApkFile(context: Context, versionName: String): File {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()
        return File(updatesDir, "ClusterLauncher-v$versionName.apk")
    }

    private fun fetchLatestReleaseJson(): String {
        val conn = (URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ClusterLauncher/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("GitHub API retornou HTTP $code")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    internal fun parseLatestReleaseJson(json: String): ReleaseInfo? {
        if (json.isBlank()) return null

        val tagName = extractJsonString(json, "tag_name") ?: return null
        val versionName = normalizeVersionString(tagName)
        val title = extractJsonString(json, "name")?.ifBlank { tagName } ?: tagName
        val htmlUrl = extractJsonString(json, "html_url")
            ?: "https://github.com/joaohouto/clusterlauncher/releases/tag/$tagName"
        val changelog = extractJsonString(json, "body")?.trim().orEmpty()

        // Find the first .apk asset in "assets": [ ... ]
        val assetsIndex = json.indexOf("\"assets\"")
        var apkDownloadUrl: String? = null
        var apkSizeBytes = 0L

        if (assetsIndex >= 0) {
            val assetsSection = json.substring(assetsIndex)
            val urlRegex = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"", RegexOption.IGNORE_CASE)
            val urlMatch = urlRegex.find(assetsSection)
            if (urlMatch != null) {
                apkDownloadUrl = unescapeJsonString(urlMatch.groupValues[1])
                // Look for "size": <number> near the matched asset
                val beforeAndAfter = assetsSection.substring(0, urlMatch.range.last.coerceAtMost(assetsSection.length))
                val sizeRegex = Regex("\"size\"\\s*:\\s*(\\d+)")
                val allSizes = sizeRegex.findAll(beforeAndAfter).toList()
                if (allSizes.isNotEmpty()) {
                    apkSizeBytes = allSizes.last().groupValues[1].toLongOrNull() ?: 0L
                }
            }
        }

        if (apkDownloadUrl.isNullOrBlank()) return null

        return ReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            title = title,
            changelog = changelog,
            apkDownloadUrl = apkDownloadUrl,
            apkSizeBytes = apkSizeBytes,
            htmlUrl = htmlUrl
        )
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = pattern.find(json) ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    private fun unescapeJsonString(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                val next = raw[i + 1]
                when (next) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val hex = raw.substring(i + 2, i + 6)
                            val codePoint = hex.toIntOrNull(16)
                            if (codePoint != null) {
                                sb.append(codePoint.toChar())
                                i += 6
                                continue
                            }
                        }
                        sb.append(next)
                        i += 2
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    internal fun normalizeVersionString(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .trim()
    }

    internal fun isNewerVersion(remoteVersion: String, localVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteVersion)
        val localParts = parseVersionParts(localVersion)
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun parseVersionParts(version: String): List<Int> {
        val clean = normalizeVersionString(version)
        if (clean.isEmpty()) return listOf(0)
        return clean.split(".")
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
    }
}
