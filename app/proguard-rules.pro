# Navigate — R8 rules (release)
#
# There is no reflection in the code. Only two things need protecting:
# custom views from XML layouts (R8 does not see the layout inflater) and the
# LauncherApps callback (called by the framework).
# AGP keeps the activities from the manifest by itself.

# AppGridView is instantiated in activity_launcher.xml by FQCN.
-keep class com.piotv.navigate.ui.AppGridView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# LauncherApps.Callback arrives via Binder from the system process.
-keepclassmembers class * extends android.content.pm.LauncherApps$Callback {
    public void *(...);
}

# Remove Log calls in release: every call is a JNI transition, and the
# output does not reach logcat on the field anyway. On a 1.4 GB device with
# a 32-bit ABI that is measurable.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

