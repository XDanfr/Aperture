# Project-specific R8 / ProGuard rules for Aperture.

# The Jellyfin SDK logs through kotlin-logging's SLF4J backend; slf4j-api is bundled and, with no
# binding, logs nowhere. Its optional binding classes aren't present.
-dontwarn org.slf4j.impl.**
