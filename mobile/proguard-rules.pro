# protocol serialisers are kept by :core's consumer rules.

# ML Kit code scanner (Google code scanner) resolves its components, such as
# SharedPrefManager, through a reflective registry. R8 full mode strips them,
# and GmsBarcodeScanning.getClient() then crashes with a NullPointerException.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
