package com.forgerom.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/** Hands a file to Android's DownloadManager (notification, resume, saved in Downloads). ForgeROM never installs it. */
object Downloader {

    /** Only HTTPS downloads from LineageOS hosts are allowed. */
    fun isAllowed(url: String): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host ?: return false
        return uri.scheme == "https" && (host == "lineageos.org" || host.endsWith(".lineageos.org"))
    }

    fun safeFileName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Returns false if the download could not be started (for example no storage permission on old Android). */
    fun start(context: Context, url: String, fileName: String): Boolean {
        if (!isAllowed(url)) return false
        return try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(safeFileName(fileName))
                .setDescription("ForgeROM")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFileName(fileName))
            manager.enqueue(request)
            true
        } catch (e: Exception) {
            false
        }
    }
}
