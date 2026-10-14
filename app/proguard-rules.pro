# Navigate — R8-Regeln (Release)
#
# Es gibt keine Reflection im Code. Zu schützen sind nur zwei Dinge:
# Custom-Views aus XML-Layouts (den Layout-Inflater sieht R8 nicht) und der
# LauncherApps-Callback (wird vom Framework aufgerufen).
# Activities aus dem Manifest behält AGP selbst.

# AppGridView wird in activity_launcher.xml per FQCN instanziiert.
-keep class com.piotv.navigate.ui.AppGridView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# LauncherApps.Callback kommt per Binder aus dem Systemprozess.
-keepclassmembers class * extends android.content.pm.LauncherApps$Callback {
    public void *(...);
}

# Log-Aufrufe in Release entfernen: jeder Aufruf ist ein JNI-Übergang, und die
# Ausgaben landen im Feld ohnehin nicht in logcat. Auf einem 1,4-GB-Gerät mit
# 32-bit-ABI ist das messbar.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

