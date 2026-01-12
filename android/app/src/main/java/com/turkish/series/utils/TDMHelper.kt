package com.turkish.series.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.turkish.series.R

/**
 * Helper للتعامل مع تطبيق TDM للتحميل
 */
object TDMHelper {

    // Package name لتطبيق TDM
    private const val TDM_PACKAGE = "idm.internet.download.manager.plus"
    private const val TDM_PACKAGE_ALT = "idm.internet.download.manager" // النسخة العادية

    // رابط التحميل من Play Store
    private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$TDM_PACKAGE"

    /**
     * التحقق إذا TDM مثبت
     */
    fun isTDMInstalled(context: Context): Boolean {
        return isPackageInstalled(context, TDM_PACKAGE) ||
                isPackageInstalled(context, TDM_PACKAGE_ALT)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * تحميل ملف باستخدام TDM
     */
    fun downloadWithTDM(context: Context, url: String, fileName: String? = null) {
        if (!isTDMInstalled(context)) {
            showInstallDialog(context)
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                // جرب Package الأولاني، ولو مش موجود جرب التاني
                setPackage(
                    if (isPackageInstalled(context, TDM_PACKAGE)) TDM_PACKAGE
                    else TDM_PACKAGE_ALT
                )
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.error_server),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * عرض dialog لتثبيت TDM
     */
    fun showInstallDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.tdm_not_installed)
            .setMessage("لتحميل الفيديوهات، تحتاج تثبيت تطبيق TDM من Play Store")
            .setPositiveButton(R.string.install_tdm) { _, _ ->
                openPlayStore(context)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * فتح صفحة TDM في Play Store
     */
    private fun openPlayStore(context: Context) {
        try {
            // جرب فتح Play Store app
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$TDM_PACKAGE")
                setPackage("com.android.vending")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // لو Play Store مش موجود، افتح في browser
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(PLAY_STORE_URL)
            }
            context.startActivity(intent)
        }
    }
}
