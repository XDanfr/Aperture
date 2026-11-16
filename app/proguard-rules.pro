# Project-specific R8 / ProGuard rules for Aperture.

# The Jellyfin SDK logs through kotlin-logging, whose optional SLF4J bridge is unused on Android
# (it falls back to android.util.Log when SLF4J isn't there).
-dontwarn org.slf4j.**
