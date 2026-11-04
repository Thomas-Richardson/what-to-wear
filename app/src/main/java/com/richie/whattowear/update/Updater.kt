package com.richie.whattowear.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import com.richie.whattowear.model.Release
import com.richie.whattowear.model.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The "Check for updates" button on the History tab: asks GitHub for the newest version, downloads its
 * app file into the app's private cache, and hands it to Android's installer. Android checks the new
 * file is signed with the same key as the installed app (Richie's Mac), and asks him to confirm.
 */
object Updater {
    private const val AGENT = "What-to-Wear-app"

    /** The newest published version, or null if nothing has been published yet. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val connection = URL(UpdateCheck.LATEST_URL).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", AGENT)
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            when (connection.responseCode) {
                200 -> UpdateCheck.parse(connection.inputStream.bufferedReader().use { it.readText() })
                404 -> null
                else -> throw IOException("GitHub answered ${connection.responseCode}.")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads the app file, calling [progress] with whole percentages as it goes. */
    suspend fun download(context: Context, release: Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only ever one download kept
        val file = File(dir, "what-to-wear-${release.version}.apk")
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", AGENT)
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        try {
            if (connection.responseCode != 200) throw IOException("GitHub answered ${connection.responseCode}.")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkBytes
            var done = 0L
            var shown = -1
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent != shown) progress(percent).also { shown = percent }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (release.apkBytes > 0 && file.length() != release.apkBytes) {
            file.delete()
            throw IOException("The download stopped part of the way through.")
        }
        file
    }

    /** Whether Richie has let this app install updates ("Install unknown apps" in Android's settings). */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** Opens the Android setting that lets this app install updates. */
    fun allowInstallsIntent(context: Context) =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Gives the downloaded file to Android's installer. Android replies to [InstallResultReceiver], which
     * shows Android's "update this app?" screen. On Android 12 and later the app asks to skip that screen
     * when it installed the current version itself; Android decides whether it may.
     */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("what-to-wear.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val reply = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            // Mutable, because Android adds the result to it.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, reply, flags).intentSender)
        }
    }
}
