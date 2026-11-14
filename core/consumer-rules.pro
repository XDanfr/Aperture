# companion protocol models are serialised with kotlinx.serialization.
-keep,includedescriptorclasses class me.xdan.aperture.companion.protocol.**$$serializer { *; }
-keepclassmembers class me.xdan.aperture.companion.protocol.** {
    *** Companion;
}
-keepclasseswithmembers class me.xdan.aperture.companion.protocol.** {
    kotlinx.serialization.KSerializer serializer(...);
}
