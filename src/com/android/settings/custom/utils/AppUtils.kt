package com.android.settings.custom.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.os.UserHandle

class AppUtils {
    fun getCloneableAppList(context: Context): List<PackageInfo> {
        val packageManager = context.packageManager
        val packageList: List<PackageInfo> =
            packageManager.getInstalledPackagesAsUser(0, UserHandle.myUserId())
        val cloneableApps = context.resources.getStringArray(com.android.internal.R.array.cloneable_apps)?.toList()
        val filteredList = packageList.filter { packageInfo ->
            val packageName = packageInfo.applicationInfo?.packageName
            val isSystemApp = packageInfo.applicationInfo?.isSystemApp() == true
            packageName != null && (cloneableApps?.contains(packageName) == true || !isSystemApp) &&
                packageManager.getLaunchIntentForPackage(packageName) != null
        }
        return filteredList
    }

    fun getCloneableAppListStr(context: Context): List<String> {
        return getCloneableAppList(context).map { it.packageName }
    }
}

