# Activities, the DreamService and the manifest-referenced classes are kept automatically by AAPT rules.
# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
