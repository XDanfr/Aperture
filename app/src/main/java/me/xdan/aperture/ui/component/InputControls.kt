@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package me.xdan.aperture.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.tv.material3.NavigationDrawerItem as TvNavigationDrawerItem

// TV Material 1.1 surfaces only implement D-pad activation. These adapters add
// pointer gestures to the same controls and interaction source, keeping their
// focus, semantics, key repeat counts, and native key-up handling unchanged.
@Composable
private fun Modifier.pointerActivation(
    enabled: Boolean,
    source: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
): Modifier {
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    return pointerInput(enabled, source, onLongClick != null) {
        if (!enabled) return@pointerInput
        detectTapGestures(
            onTap = { click() },
            onLongPress = if (onLongClick != null) ({ longClick?.invoke() }) else null,
            onPress = { offset ->
                val press = PressInteraction.Press(offset)
                source.emit(press)
                var completed = false
                try {
                    val released = tryAwaitRelease()
                    source.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                    completed = true
                } finally {
                    if (!completed) source.tryEmit(PressInteraction.Cancel(press))
                }
            }
        )
    }
}

@Composable
fun InputSurface(
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 0.dp,
    shape: Shape = SurfaceDefaults.shape,
    colors: SurfaceColors = SurfaceDefaults.colors(),
    border: Border = Border.None,
    glow: Glow = Glow.None,
    content: @Composable (BoxScope.() -> Unit),
) {
    androidx.tv.material3.Surface(
        modifier = modifier,
        tonalElevation = tonalElevation,
        shape = shape,
        colors = colors,
        border = border,
        glow = glow,
        content = content,
    )
}

@Composable
fun InputSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onPointerLongClick: (() -> Unit)? = onLongClick,
    enabled: Boolean = true,
    tonalElevation: Dp = 0.dp,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    border: ClickableSurfaceBorder = ClickableSurfaceDefaults.border(),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable (BoxScope.() -> Unit),
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier.pointerActivation(enabled, source, onClick, onPointerLongClick),
        onLongClick = onLongClick,
        enabled = enabled,
        tonalElevation = tonalElevation,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        glow = glow,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun InputSurface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onPointerLongClick: (() -> Unit)? = onLongClick,
    tonalElevation: Dp = 0.dp,
    shape: SelectableSurfaceShape = SelectableSurfaceDefaults.shape(),
    colors: SelectableSurfaceColors = SelectableSurfaceDefaults.colors(),
    scale: SelectableSurfaceScale = SelectableSurfaceDefaults.scale(),
    border: SelectableSurfaceBorder = SelectableSurfaceDefaults.border(),
    glow: SelectableSurfaceGlow = SelectableSurfaceDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable (BoxScope.() -> Unit),
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    androidx.tv.material3.Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.pointerActivation(enabled, source, onClick, onPointerLongClick),
        enabled = enabled,
        onLongClick = onLongClick,
        tonalElevation = tonalElevation,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        glow = glow,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun InputButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = ButtonDefaults.scale(),
    glow: ButtonGlow = ButtonDefaults.glow(),
    shape: ButtonShape = ButtonDefaults.shape(),
    colors: ButtonColors = ButtonDefaults.colors(),
    tonalElevation: Dp = 0.dp,
    border: ButtonBorder = ButtonDefaults.border(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = modifier.pointerActivation(enabled, source, onClick, onLongClick),
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun InputOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = OutlinedButtonDefaults.scale(),
    glow: ButtonGlow = OutlinedButtonDefaults.glow(),
    shape: ButtonShape = OutlinedButtonDefaults.shape(),
    colors: ButtonColors = OutlinedButtonDefaults.colors(),
    tonalElevation: Dp = 0.dp,
    border: ButtonBorder = OutlinedButtonDefaults.border(),
    contentPadding: PaddingValues = OutlinedButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    androidx.tv.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.pointerActivation(enabled, source, onClick, onLongClick),
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun InputIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = IconButtonDefaults.scale(),
    glow: ButtonGlow = IconButtonDefaults.glow(),
    shape: ButtonShape = IconButtonDefaults.shape(),
    colors: ButtonColors = IconButtonDefaults.colors(),
    border: ButtonBorder = IconButtonDefaults.border(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    androidx.tv.material3.IconButton(
        onClick = onClick,
        modifier = modifier.pointerActivation(enabled, source, onClick, onLongClick),
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        border = border,
        interactionSource = source,
        content = content,
    )
}

@Composable
fun NavigationDrawerScope.InputNavigationDrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    tonalElevation: Dp = NavigationDrawerItemDefaults.NavigationDrawerItemElevation,
    shape: NavigationDrawerItemShape = NavigationDrawerItemDefaults.shape(),
    colors: NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(),
    scale: NavigationDrawerItemScale = NavigationDrawerItemScale.None,
    border: NavigationDrawerItemBorder = NavigationDrawerItemDefaults.border(),
    glow: NavigationDrawerItemGlow = NavigationDrawerItemDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    TvNavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = leadingContent,
        modifier = modifier.pointerActivation(enabled, source, onClick, onLongClick),
        enabled = enabled,
        onLongClick = onLongClick,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        tonalElevation = tonalElevation,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        glow = glow,
        interactionSource = source,
        content = content,
    )
}

