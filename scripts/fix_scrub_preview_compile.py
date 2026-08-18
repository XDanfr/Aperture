from pathlib import Path

screen = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PlayerScreen.kt')
text = screen.read_text(encoding='utf-8')

replacements = [
    (
        '    val duration = player.duration.coerceAtLeast(0L)\n\n    var focused by remember { mutableStateOf(false) }',
        '    val duration = player.duration.coerceAtLeast(0L)\n    val context = LocalContext.current\n\n    var focused by remember { mutableStateOf(false) }',
    ),
    (
        '    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }\n    val previewLoader = remember { PreviewFrameLoader(LocalContext.current) }',
        '    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }\n    val previewLoader = remember(context) { PreviewFrameLoader(context) }',
    ),
    (
        '                tonalElevation = 6.dp,\n                shadowElevation = 8.dp\n',
        '                tonalElevation = 6.dp\n',
    ),
    (
        '                    Box(\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .height(previewHeight)\n                            .background(MaterialTheme.colorScheme.surfaceVariant),\n                        contentAlignment = Alignment.Center\n                    ) {\n                        previewBitmap?.let { bitmap ->\n                            Image(\n                                bitmap = bitmap.asImageBitmap(),\n                                contentDescription = null,\n                                contentScale = ContentScale.Crop,\n                                modifier = Modifier.fillMaxSize()\n                            )\n                        } ?: ExpressiveLoadingIndicator(\n                            color = MaterialTheme.colorScheme.primary,\n                            size = 28.dp\n                        )\n                    }',
        '                    Box(\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .height(previewHeight)\n                            .background(MaterialTheme.colorScheme.surfaceVariant),\n                        contentAlignment = Alignment.Center\n                    ) {\n                        if (previewBitmap != null) {\n                            Image(\n                                bitmap = previewBitmap!!.asImageBitmap(),\n                                contentDescription = null,\n                                contentScale = ContentScale.Crop,\n                                modifier = Modifier.fillMaxSize()\n                            )\n                        } else {\n                            ExpressiveLoadingIndicator(\n                                color = MaterialTheme.colorScheme.primary,\n                                size = 28.dp\n                            )\n                        }\n                    }',
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'Expected one match, found {count}: {old[:100]!r}')
    text = text.replace(old, new, 1)

screen.write_text(text, encoding='utf-8')

loader = Path('app/src/main/java/me/xdan/aperture/ui/screen/player/PreviewFrameLoader.kt')
text = loader.read_text(encoding='utf-8')

replacements = [
    (
        '            if (sourcePath.startsWith("content://")) {\n                setDataSource(context, Uri.parse(sourcePath), null)\n            } else {\n                setDataSource(File(sourcePath).absolutePath)\n            }',
        '            if (sourcePath.startsWith("content://")) {\n                context.contentResolver.openFileDescriptor(Uri.parse(sourcePath), "r")\n                    ?.use { descriptor ->\n                        setDataSource(descriptor.fileDescriptor)\n                    }\n                    ?: error("Unable to open preview source: $sourcePath")\n            } else {\n                setDataSource(File(sourcePath).absolutePath)\n            }',
    ),
    (
        '        private const val DEFAULT_CACHE_SIZE = 10\n        private const val PREVIEW_WIDTH = 320\n        private const val PREVIEW_HEIGHT = 180\n        private const val QUANTUM_MS = 2_000L\n',
        '        private const val DEFAULT_CACHE_SIZE = 10\n        private const val QUANTUM_MS = 2_000L\n',
    ),
    (
        'private fun Bitmap.scaleToPreview(): Bitmap {\n    if (width <= PREVIEW_WIDTH && height <= PREVIEW_HEIGHT) return this\n\n    val scale = minOf(\n        PREVIEW_WIDTH.toFloat() / width,\n        PREVIEW_HEIGHT.toFloat() / height\n    )',
        'private const val PREVIEW_WIDTH = 320\nprivate const val PREVIEW_HEIGHT = 180\n\nprivate fun Bitmap.scaleToPreview(): Bitmap {\n    if (width <= PREVIEW_WIDTH && height <= PREVIEW_HEIGHT) return this\n\n    val scale = minOf(\n        PREVIEW_WIDTH.toFloat() / width,\n        PREVIEW_HEIGHT.toFloat() / height\n    )',
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'Expected one loader match, found {count}: {old[:100]!r}')
    text = text.replace(old, new, 1)

loader.write_text(text, encoding='utf-8')

Path('scripts/fix_scrub_preview_compile.py').unlink()
Path('.github/workflows/fix-scrub-preview-compile.yml').unlink()
