package me.xdan.aperture.ui.screen.onboarding

import androidx.compose.runtime.Composable

/** Asks the user to uninstall the legacy Android package; only Android ever shows it. */
@Composable
expect fun LegacyInstallCleanup(onRecheck: () -> Unit, onCancel: () -> Unit)
