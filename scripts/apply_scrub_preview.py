from pathlib import Path

path = Path("app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerScreen.kt")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        'import android.view.KeyEvent\nimport android.view.View\n',
        'import android.graphics.Bitmap\nimport android.view.KeyEvent\nimport android.view.View\n',
    ),
    (
        'import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.shape.RoundedCornerShape\n',
        'import androidx.compose.foundation.Image\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.shape.RoundedCornerShape\n',
    ),
    (
        'import androidx.compose.ui.graphics.Color\n',
        'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.asImageBitmap\n',
    ),
    (
        '                media = media,\n                player = player,\n',
        '                media = media,\n                mediaSource = media?.filePath,\n                player = player,\n',
        ),
    (
        'private fun PlayerOsd(\n    media: MediaEntity?,\n    player: androidx.media3.common.Player,\n',
        'private fun PlayerOsd(\n    media: MediaEntity?,\n    mediaSource: String?,\n    player: androidx.media3.common.Player,\n',
    ),
    (
        '            PlayerSeekProgress(\n                player = player,\n                progress = progress,\n                isPlaying = isPlaying,\n',
        '            PlayerSeekProgress(\n                player = player,\n                mediaSource = mediaSource,\n                progress = progress,\n                isPlaying = isPlaying,\n',
    ),
    (
        'private fun PlayerSeekProgress(\n    player: androidx.media3.common.Player,\n    progress: Float,\n',
        'private fun PlayerSeekProgress(\n    player: androidx.media3.common.Player,\n    mediaSource: String?,\n    progress: Float,\n',
    ),
    (
        '    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }\n\n    var holdDirection by remember { mutableIntStateOf(0) }\n',
        '    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }\n    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }\n    val previewLoader = remember { PreviewFrameLoader(LocalContext.current) }\n\n    var holdDirection by remember { mutableIntStateOf(0) }\n',
    ),
    (
        '    val waveAmplitude = if (isPlaying && !scrubbing) 1f else 0f\n\n    val handleSize by animateDpAsState(\n',
        '    val waveAmplitude = if (isPlaying && !scrubbing) 1f else 0f\n    val previewPosition = if (scrubbing) {\n        PreviewFrameLoader.quantise(seekPosition)\n    } else {\n        -1L\n    }\n\n    LaunchedEffect(previewPosition, scrubbing, mediaSource) {\n        previewBitmap = null\n        if (!scrubbing || mediaSource.isNullOrBlank() || previewPosition < 0L) return@LaunchedEffect\n\n        delay(100)\n        previewBitmap = previewLoader.load(mediaSource, previewPosition)\n    }\n\n    DisposableEffect(previewLoader) {\n        onDispose { previewLoader.clear() }\n    }\n\n    val handleSize by animateDpAsState(\n',
    ),
    (
        '    ) {\n        LinearWavyProgressIndicator(\n',
        '    ) {\n        if (scrubbing) {\n            val previewWidth = 320.dp\n            val previewHeight = 180.dp\n            val previewX = ((maxWidth - previewWidth) * animatedProgress)\n                .coerceIn(0.dp, (maxWidth - previewWidth).coerceAtLeast(0.dp))\n\n            Surface(\n                modifier = Modifier\n                    .width(previewWidth)\n                    .wrapContentHeight()\n                    .offset(x = previewX, y = -(previewHeight + 28.dp) / 2)\n                    .align(Alignment.CenterStart),\n                shape = RoundedCornerShape(24.dp),\n                colors = SurfaceDefaults.colors(\n                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)\n                ),\n                tonalElevation = 6.dp,\n                shadowElevation = 8.dp\n            ) {\n                Column {\n                    Box(\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .height(previewHeight)\n                            .background(MaterialTheme.colorScheme.surfaceVariant),\n                        contentAlignment = Alignment.Center\n                    ) {\n                        previewBitmap?.let { bitmap ->\n                            Image(\n                                bitmap = bitmap.asImageBitmap(),\n                                contentDescription = null,\n                                contentScale = ContentScale.Crop,\n                                modifier = Modifier.fillMaxSize()\n                            )\n                        } ?: ExpressiveLoadingIndicator(\n                            color = MaterialTheme.colorScheme.primary,\n                            size = 28.dp\n                        )\n                    }\n                    Text(\n                        text = formatTime(seekPosition),\n                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),\n                        style = MaterialTheme.typography.labelLarge,\n                        color = MaterialTheme.colorScheme.onSurface\n                    )\n                }\n            }\n        }\n\n        LinearWavyProgressIndicator(\n',
    ),
]

for replacement in replacements:
    old = replacement[0]
    new = replacement[1]
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match for patch fragment, found {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
Path("scripts/apply_scrub_preview.py").unlink()
Path(".github/workflows/apply-scrub-preview.yml").unlink()
