package com.turkish.series.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.turkish.series.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Checks for app updates from GitHub and shows mandatory update dialog
 */
object UpdateChecker {

    // URL to version.json on GitHub Pages
    private const val VERSION_URL = "https://mboshkash.github.io/turkish-series/data/version.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val releaseNotes: String,
        val forceUpdate: Boolean
    )

    /**
     * Check for updates - call this in MainActivity.onCreate()
     */
    suspend fun checkForUpdate(activity: Activity) {
        try {
            val updateInfo = fetchUpdateInfo() ?: return

            // Get current version code
            val currentVersionCode = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.packageManager.getPackageInfo(
                        activity.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    ).longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode
                }
            } catch (e: Exception) {
                1
            }

            if (updateInfo.versionCode > currentVersionCode) {
                // New version available
                withContext(Dispatchers.Main) {
                    showUpdateDialog(activity, updateInfo)
                }
            }
        } catch (e: Exception) {
            // Silently fail - don't block the app if update check fails
            e.printStackTrace()
        }
    }

    private suspend fun fetchUpdateInfo(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val response = URL(VERSION_URL).readText()
            val json = JSONObject(response)

            UpdateInfo(
                versionCode = json.getInt("version_code"),
                versionName = json.getString("version_name"),
                apkUrl = json.getString("apk_url"),
                releaseNotes = json.optString("release_notes", ""),
                forceUpdate = json.optBoolean("force_update", true)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showUpdateDialog(activity: Activity, updateInfo: UpdateInfo) {
        val dialog = MaterialAlertDialogBuilder(activity, R.style.AlertDialogTheme)
            .setTitle("تحديث جديد متاح")
            .setMessage(buildString {
                append("الإصدار ${updateInfo.versionName} متاح الآن!\n\n")
                if (updateInfo.releaseNotes.isNotEmpty()) {
                    append("ما الجديد:\n")
                    append(updateInfo.releaseNotes)
                }
            })
            .setPositiveButton("تحديث الآن") { _, _ ->
                // Open APK URL in browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkUrl))
                activity.startActivity(intent)

                // If force update, close the app
                if (updateInfo.forceUpdate) {
                    activity.finishAffinity()
                }
            }
            .setCancelable(!updateInfo.forceUpdate)

        // Only show skip button if not force update
        if (!updateInfo.forceUpdate) {
            dialog.setNegativeButton("لاحقاً", null)
        }

        dialog.show()
    }
}
