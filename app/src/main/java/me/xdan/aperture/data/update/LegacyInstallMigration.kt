package me.xdan.aperture.data.update

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyInstallMigration @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _cleanupRequired = MutableStateFlow(false)
    val cleanupRequired = _cleanupRequired.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val installed = try {
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
