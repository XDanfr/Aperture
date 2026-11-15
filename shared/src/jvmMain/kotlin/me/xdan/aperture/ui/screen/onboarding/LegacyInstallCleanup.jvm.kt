package me.xdan.aperture.ui.screen.onboarding

import androidx.compose.runtime.Composable

// Desktop never has a legacy install (LegacyInstallMigration.None), so this is never composed.
@Composable
actual fun LegacyInstallCleanup(onRecheck: () -> Unit, onCancel: () -> Unit) = Unit
