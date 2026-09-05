// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import android.content.ComponentName

data class AppInfo(
    val componentName: ComponentName,
    val label: String,
) {
    val key: String get() = componentName.flattenToString()
    val packageName: String get() = componentName.packageName
}