package me.xdan.aperture.data.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Self-updating, for builds that aren't updated by a store or package manager. */
interface UpdateManager {
    val state: StateFlow<UpdateCheckState>
    suspend fun checkForUpdates(silent: Boolean = false)
    suspend fun downloadAndInstall(update: UpdateCheckState.Available)
    fun reset()
}

/** Builds where something else delivers updates (Play, desktop installers). */
class NoUpdateManager : UpdateManager {
    override val state: StateFlow<UpdateCheckState> = MutableStateFlow(UpdateCheckState.Idle)
    override suspend fun checkForUpdates(silent: Boolean) = Unit
    override suspend fun downloadAndInstall(update: UpdateCheckState.Available) = Unit
    override fun reset() = Unit
}

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Current(val version: String) : UpdateCheckState
    data class Available(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?
    ) : UpdateCheckState
    data class PermissionRequired(val update: Available) : UpdateCheckState
    data class Downloading(val update: Available, val progress: Float) : UpdateCheckState
    data class Installing(val version: String) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

/** An older install that has to go before this one can be used (Android's package migration). */
interface LegacyInstallMigration {
    val cleanupRequired: StateFlow<Boolean>
    fun refresh()

    companion object {
        /** No platform but Android has a legacy install. */
        val None: LegacyInstallMigration = object : LegacyInstallMigration {
            override val cleanupRequired: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
            override fun refresh() = Unit
        }
    }
}
