package com.turkish.series.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.turkish.series.R

/**
 * Helper للتعامل مع تطبيق TDM للتحميل
 */
object TDMHelper {

    // Package name لتطبيق TDM
    private const val TDM_PACKAGE = "com.tdm.manager"

    // Activity للتحميل
    private const val TDM_DOWNLOAD_ACTIVITY = "com.tdm.manager.dialog.DownloadEditor"

    // TDM Deep Link prefix
    private const val TDM_DEEPLINK_PREFIX = "tdm://open?url="

    /**
     * التحقق إذا TDM مثبت
     */
    fun isTDMInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TDM_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TDM_PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * التحقق إذا الرابط هو TDM deep link
     */
    fun isTDMDeepLink(url: String): Boolean {
        return url.startsWith("tdm://")
    }

    /**
     * استخراج الرابط الأصلي من TDM deep link
     */
    fun extractUrlFromDeepLink(deepLink: String): String {
        return if (deepLink.startsWith(TDM_DEEPLINK_PREFIX)) {
            deepLink.removePrefix(TDM_DEEPLINK_PREFIX)
        } else {
            deepLink
        }
    }

    /**
     * تحميل ملف باستخدام TDM
     */
    fun downloadWithTDM(context: Context, url: String, fileName: String? = null) {
        // لو الرابط هو TDM deep link، نفتحه مباشرة
        if (isTDMDeepLink(url)) {
            openTDMDeepLink(context, url)
            return
        }
        // نحاول التحميل مباشرة بدون التحقق أولاً
        try {
            // الطريقة الأولى: استخدام DownloadEditor مباشرة
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setClassName(TDM_PACKAGE, TDM_DOWNLOAD_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // فشل، نجرب الطريقة الثانية
        }

        try {
            // الطريقة الثانية: استخدام ACTION_SEND
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                setPackage(TDM_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            // فشل، نجرب الطريقة الثالثة
        }

        try {
            // الطريقة الثالثة: فتح الرابط عادي وخلي TDM يلتقطه
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "تحميل بواسطة"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "فشل فتح رابط التحميل",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * فتح رابط TDM deep link
     */
    private fun openTDMDeepLink(context: Context, deepLink: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(deepLink)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // لو فشل فتح الـ deep link، نستخرج الرابط ونفتحه عادي
            val actualUrl = extractUrlFromDeepLink(deepLink)
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(actualUrl)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "تحميل بواسطة"))
            } catch (e2: Exception) {
                Toast.makeText(
                    context,
                    "فشل فتح رابط التحميل",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * عرض dialog لتثبيت TDM
     */
    fun showInstallDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tdm_not_installed)
            .setMessage("لتحميل الفيديوهات، تحتاج تثبيت تطبيق TDM")
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
