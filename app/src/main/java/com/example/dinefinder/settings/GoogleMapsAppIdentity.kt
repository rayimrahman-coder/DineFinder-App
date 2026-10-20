package com.example.dinefinder.settings

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/** Identity of the installed build, used by Android-restricted Google Maps Platform keys. */
data class GoogleMapsAppIdentity(val packageName: String, val certificateSha1: String) {
    companion object {
        fun from(context: Context): GoogleMapsAppIdentity? = try {
            val info = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
            )
            val certificate = info.signingInfo?.apkContentsSigners?.firstOrNull()
            if (certificate == null) null else GoogleMapsAppIdentity(
                context.packageName,
                sha1Fingerprint(certificate.toByteArray())
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }

        internal fun sha1Fingerprint(certificate: ByteArray): String = MessageDigest
            .getInstance("SHA-1")
            .digest(certificate)
            .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }
    }
}
