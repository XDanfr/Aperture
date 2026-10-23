"""Fail CI if Play gains sideload permissions or exported private components."""
from pathlib import Path
import xml.etree.ElementTree as ET

android = "{http://schemas.android.com/apk/res/android}"
manifest = Path("app/build/intermediates/merged_manifest/playRelease/processPlayReleaseMainManifest/AndroidManifest.xml")
root = ET.parse(manifest).getroot()
permissions = {node.get(android + "name") for node in root.findall("uses-permission")}
allowed = {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.WAKE_LOCK", "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_VIDEO",
    "cc.xdan.aperture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
assert not permissions - allowed, f"Unexpected Play permissions: {permissions - allowed}"
application = root.find("application")
assert application.get(android + "debuggable") != "true"
assert not any(p.get(android + "name") == "androidx.core.content.FileProvider" for p in application.findall("provider"))
assert any("android.intent.category.LEANBACK_LAUNCHER" == n.get(android + "name") for n in application.findall("activity/intent-filter/category"))
print("Play manifest checks passed:", ", ".join(sorted(permissions)))
