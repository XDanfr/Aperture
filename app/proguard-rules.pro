# Project-specific R8 / ProGuard rules for Aperture.

# Moshi codegen adapters live in :shared. Its KSP-generated keep rules
# (META-INF/proguard/moshi-*.pro) are not packaged into the library, so R8
# strips the adapters and Moshi fails to find them by name at runtime.
-if @com.squareup.moshi.JsonClass class *
-keepnames class <1>
-if @com.squareup.moshi.JsonClass class **$*
-keepnames class <1>$<2>
-if @com.squareup.moshi.JsonClass class *
-keep class <1>JsonAdapter {
    <init>(...);
    <fields>;
}
-if @com.squareup.moshi.JsonClass class **$*
-keep class <1>$<2>JsonAdapter {
    <init>(...);
    <fields>;
}
