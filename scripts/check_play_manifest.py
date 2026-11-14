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
    # Android 17 local network protection: companion phones connect to the TV.
    "android.permission.ACCESS_LOCAL_NETWORK",
    "cc.xdan.aperture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
assert not permissions - allowed, f"Unexpected Play permissions: {permissions - allowed}"
application = root.find("application")
assert application.get(android + "debuggable") != "true"
assert not any(p.get(android + "name") == "androidx.core.content.FileProvider" for p in application.findall("provider"))
assert any("android.intent.category.LEANBACK_LAUNCHER" == n.get(android + "name") for n in application.findall("activity/intent-filter/category"))
tv_features = {n.get(android + "name"): n.get(android + "required", "true") for n in root.findall("uses-feature")}
assert tv_features.get("android.software.leanback") == "true", "TV build must require leanback so phones never get it"
print("Play manifest checks passed:", ", ".join(sorted(permissions)))

# the phone companion shares the listing (and application ID) but must never reach TVs:
# it requires a touchscreen and has no leanback launcher. phones never get the TV build,
# which requires leanback.
companion = Path("mobile/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml")
if companion.exists():
    phone = ET.parse(companion).getroot()
    assert phone.get("package") == root.get("package"), "Companion and TV app must share an application ID"
    phone_permissions = {node.get(android + "name") for node in phone.findall("uses-permission")}
    phone_allowed = {
        "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_LOCAL_NETWORK",
        "cc.xdan.aperture.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    }
    assert not phone_permissions - phone_allowed, f"Unexpected companion permissions: {phone_permissions - phone_allowed}"
    features = {n.get(android + "name"): n.get(android + "required", "true") for n in phone.findall("uses-feature")}
    assert features.get("android.hardware.touchscreen") == "true", "Companion must require a touchscreen"
    assert features.get("android.software.leanback") in (None, "false")
    phone_app = phone.find("application")
    assert phone_app.get(android + "debuggable") != "true"
    assert not any("android.intent.category.LEANBACK_LAUNCHER" == n.get(android + "name") for n in phone_app.findall("activity/intent-filter/category"))
    print("Companion manifest checks passed:", ", ".join(sorted(phone_permissions)))
else:
    print("Companion manifest not built; skipped")
