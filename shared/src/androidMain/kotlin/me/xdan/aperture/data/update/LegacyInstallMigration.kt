package me.xdan.aperture.data.update

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The pre-Play package (me.xdan.aperture) must be removed before this one is used. */
class AndroidLegacyInstallMigration(
    private val context: Context
) : LegacyInstallMigration {
    private val _cleanupRequired = MutableStateFlow(false)
    override val cleanupRequired = _cleanupRequired.asStateFlow()

    init {
        refresh()
    }

    override fun refresh() {
        val installed = if (!me.xdan.aperture.platform.AppBuild.SIDELOAD_UPDATES_ENABLED) false else try {
            context.packageManager.getApplicationInfo(LEGACY_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        _cleanupRequired.value = installed
    }

    companion object {
        const val LEGACY_PACKAGE = "me.xdan.aperture"
    }
}
