from pathlib import Path

PATH = Path("app/src/main/java/me/xdan/aperture/ui/screen/settings/SettingsScreen.kt")
text = PATH.read_text(encoding="utf-8")


def find_matching_brace(source: str, open_index: int) -> int:
    depth = 0
    for i in range(open_index, len(source)):
        ch = source[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
    raise SystemExit(f"Unmatched brace at {open_index}")


def extract_item(source: str, start: int) -> tuple[str, int]:
    open_index = source.find("{", start)
    if open_index < 0:
        raise SystemExit("Could not find item opening brace")
    end = find_matching_brace(source, open_index)
    return source[start:end + 1], end + 1


# Imports needed by the page shell.
if "import androidx.activity.compose.BackHandler" not in text:
    anchor = "import androidx.activity.compose.rememberLauncherForActivityResult\n"
    text = text.replace(anchor, anchor + "import androidx.activity.compose.BackHandler\n", 1)
if "import androidx.compose.material.icons.rounded.ArrowBack" not in text:
    anchor = "import androidx.compose.material.icons.rounded.DeleteSweep\n"
    text = text.replace(anchor, anchor + "import androidx.compose.material.icons.rounded.ArrowBack\n", 1)

# Add the page enum before SettingsScreen.
if "private enum class SettingsPage" not in text:
    anchor = "@OptIn(ExperimentalTvMaterial3Api::class)\n@Composable\nfun SettingsScreen("
    enum_block = '''private enum class SettingsPage {
    OVERVIEW,
    CUSTOMISATION,
    LIBRARY,
    PLAYBACK,
    ABOUT
}

'''
    if anchor not in text:
        raise SystemExit("SettingsScreen anchor not found")
    text = text.replace(anchor, enum_block + anchor, 1)

# Capture the original LazyColumn body before replacing the shell.
box_anchor = "    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {"
box_start = text.find(box_anchor)
if box_start < 0:
    raise SystemExit("Settings Box anchor not found")

lazy_start = text.find("            LazyColumn(\n", box_start)
if lazy_start < 0:
    raise SystemExit("Settings LazyColumn not found")

lazy_open = text.find("{", lazy_start)
if lazy_open < 0:
    raise SystemExit("LazyColumn opening brace not found")
lazy_end = find_matching_brace(text, lazy_open)

body_start = lazy_open + 1
body = text[body_start:lazy_end]

categories = [
    ("Customisation", "Theme, presentation and ambient mode", "CUSTOMISATION"),
    ("Library & storage", "Folders, hidden titles and library maintenance", "LIBRARY"),
    ("Playback & subtitles", "Playback, subtitles and related options", "PLAYBACK"),
    ("About Aperture", "Updates, licences and project information", "ABOUT"),
]

chunks: dict[str, str] = {}
positions: list[tuple[int, str, str]] = []
for title, description, page in categories:
    marker = f'''            item {{
                SettingsCategoryHeader(
                    title = "{title}",'''
    pos = body.find(marker)
    if pos < 0:
        raise SystemExit(f"Category header not found: {title}")
    positions.append((pos, title, page))
positions.sort()

for index, (pos, title, page) in enumerate(positions):
    end = positions[index + 1][0] if index + 1 < len(positions) else len(body)
    chunk = body[pos:end]
    header_item, header_end = extract_item(chunk, 12)
    chunks[page] = chunk[header_end:]

# Move the language entry into the shared Playback page as a disabled placeholder.
lang_marker = '''            item {
                SettingsItem(
                    title = "Language",'''
if lang_marker in chunks["CUSTOMISATION"]:
    language_item, language_end = extract_item(chunks["CUSTOMISATION"], chunks["CUSTOMISATION"].find(lang_marker) + 12)
    chunks["CUSTOMISATION"] = chunks["CUSTOMISATION"][:chunks["CUSTOMISATION"].find(lang_marker)] + chunks["CUSTOMISATION"][chunks["CUSTOMISATION"].find(lang_marker) + language_end:]

language_placeholder = '''            item {
                SettingsItem(
                    title = "Languages",
                    subtitle = "Audio and subtitle language preferences · Coming soon",
                    icon = Icons.Rounded.Language,
                    enabled = false,
                    drawerFocusRequester = drawerFocusRequester,
                    onFocused = { requester -> onContentFocused(requester) },
                    onClick = {}
                )
            }

'''
chunks["PLAYBACK"] = language_placeholder + chunks["PLAYBACK"]

overview = '''            item {
                SettingsPageEntry(
                    title = "Customisation",
                    description = "Theme, presentation and ambient mode",
                    icon = Icons.Rounded.Palette,
                    onClick = { currentPage = SettingsPage.CUSTOMISATION }
                )
            }
            item {
                SettingsPageEntry(
                    title = "Library & storage",
                    description = "Folders, hidden titles and library maintenance",
                    icon = Icons.Rounded.FolderOpen,
                    onClick = { currentPage = SettingsPage.LIBRARY }
                )
            }
            item {
                SettingsPageEntry(
                    title = "Playback & subtitles",
                    description = "Playback, subtitles and related options",
                    icon = Icons.Rounded.Subtitles,
                    onClick = { currentPage = SettingsPage.PLAYBACK }
                )
            }
            item {
                SettingsPageEntry(
                    title = "About Aperture",
                    description = "Updates, licences and project information",
                    icon = Icons.Rounded.Info,
                    onClick = { currentPage = SettingsPage.ABOUT }
                )
            }
'''


def page_block(page: str, title: str, description: str, contents: str) -> str:
    return f'''            SettingsPageHeader(
                title = "{title}",
                description = "{description}",
                onBack = {{ currentPage = SettingsPage.OVERVIEW }}
            )
{contents}'''

page_body = {
    "OVERVIEW": overview,
    "CUSTOMISATION": page_block("CUSTOMISATION", "Customisation", "Theme, presentation and ambient mode", chunks["CUSTOMISATION"]),
    "LIBRARY": page_block("LIBRARY", "Library & storage", "Folders, hidden titles and library maintenance", chunks["LIBRARY"]),
    "PLAYBACK": page_block("PLAYBACK", "Playback & subtitles", "Playback, subtitles and related options", chunks["PLAYBACK"]),
    "ABOUT": page_block("ABOUT", "About Aperture", "Updates, licences and project information", chunks["ABOUT"]),
}

# Replace the whole Box containing the old Settings title and list.
box_open = text.find("{", box_start)
box_end = find_matching_brace(text, box_open)
page_shell = '''    var currentPage by remember(restoreFocusKey) {
        mutableStateOf(
            when (restoreFocusKey) {
                SETTINGS_THEME_FOCUS_KEY,
                SETTINGS_AMBIENT_FOCUS_KEY,
                SETTINGS_SHOW_LAYOUT_FOCUS_KEY,
                SETTINGS_ROUNDED_SPOTLIGHT_FOCUS_KEY,
                SETTINGS_SPOTLIGHT_TOGGLE_FOCUS_KEY,
                SETTINGS_SPOTLIGHT_DAYS_FOCUS_KEY -> SettingsPage.CUSTOMISATION
                SETTINGS_HIDDEN_FOCUS_KEY,
                SETTINGS_MEDIA_FOLDERS_FOCUS_KEY,
                SETTINGS_RESCAN_FOCUS_KEY,
                SETTINGS_CLEAR_CACHE_FOCUS_KEY -> SettingsPage.LIBRARY
                SETTINGS_OPEN_SUBTITLES_FOCUS_KEY,
                SETTINGS_SUBTITLES_FOCUS_KEY -> SettingsPage.PLAYBACK
                SETTINGS_LICENCES_FOCUS_KEY,
                SETTINGS_UPDATE_FOCUS_KEY,
                SETTINGS_TMDB_FOCUS_KEY,
                SETTINGS_DONATE_FOCUS_KEY -> SettingsPage.ABOUT
                else -> SettingsPage.OVERVIEW
            }
        )
    }
    val listState = rememberLazyListState()

    BackHandler(enabled = currentPage != SettingsPage.OVERVIEW) {
        currentPage = SettingsPage.OVERVIEW
    }

    LaunchedEffect(currentPage) {
        listState.scrollToItem(0)
        delay(80)
        if (currentPage == SettingsPage.CUSTOMISATION && restoreFocusKey == null) {
            runCatching { contentEntryFocusRequester.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1_000.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 72.dp, end = 72.dp, top = 42.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (currentPage) {
                    SettingsPage.OVERVIEW -> {
                        item {
                            SettingsPageHeader(
                                title = "Settings",
                                description = "Choose a settings area"
                            )
                        }
%s
                    }
                    SettingsPage.CUSTOMISATION -> {
%s
                    }
                    SettingsPage.LIBRARY -> {
%s
                    }
                    SettingsPage.PLAYBACK -> {
%s
                    }
                    SettingsPage.ABOUT -> {
%s
                    }
                }
            }
        }
    }''' % (
        page_body["OVERVIEW"],
        page_body["CUSTOMISATION"],
        page_body["LIBRARY"],
        page_body["PLAYBACK"],
        page_body["ABOUT"],
    )

text = text[:box_start] + page_shell + text[box_end + 1:]

# Add page helpers before SettingsCategoryHeader.
helper_anchor = "@Composable\nprivate fun SettingsCategoryHeader("
if "private fun SettingsPageHeader(" not in text:
    helper = '''@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsPageHeader(
    title: String,
    description: String,
    onBack: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (onBack != null) {
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f, pressedScale = 0.98f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Back to Settings", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(title, style = MaterialTheme.typography.displaySmall)
        Text(
            description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsPageEntry(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.02f,
            pressedScale = 0.985f
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(18.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.ViewModule, contentDescription = null)
        }
    }
}

'''
    if helper_anchor not in text:
        raise SystemExit("SettingsCategoryHeader anchor not found")
    text = text.replace(helper_anchor, helper + helper_anchor, 1)

# Remove the now-unused old restore-index LaunchedEffect and duplicate list-state declaration if present.
old_restore_start = text.find("    LaunchedEffect(Unit) {\n        val restoreIndex = when (restoreFocusKey) {")
if old_restore_start >= 0:
    old_restore_open = text.find("{", old_restore_start)
    old_restore_end = find_matching_brace(text, old_restore_open)
    text = text[:old_restore_start] + text[old_restore_end + 1:]

# The page shell declares listState, so remove an earlier duplicate declaration if the old one survived.
# Keep the first occurrence after the new currentPage block.
occurrences = []
needle = "    val listState = rememberLazyListState()"
start = 0
while True:
    i = text.find(needle, start)
    if i < 0:
        break
    occurrences.append(i)
    start = i + 1
if len(occurrences) > 1:
    i = occurrences[0]
    j = occurrences[1]
    text = text[:j] + text[j + len(needle) + 1:]

PATH.write_text(text, encoding="utf-8")
print("Refactored SettingsScreen.kt into page-based settings.")
