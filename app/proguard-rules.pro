# Victoria Launcher R8 rules.
#
# Compose, AndroidX and kotlinx ship their own consumer rules, so this file only
# covers the two places where the framework reaches into our code by name.

# AppWidgetHost.createView() instantiates the host view reflectively, and the
# system inflates our container around it.
-keep class dev.victorialauncher.widget.VictoriaAppWidgetHost { *; }
-keep class dev.victorialauncher.widget.LongPressFrameLayout {
    public <init>(android.content.Context);
}

# Services named in AndroidManifest.xml are instantiated by name by the system.
-keep class dev.victorialauncher.service.VictoriaAccessibilityService { *; }
-keep class dev.victorialauncher.media.NowPlayingListenerService { *; }
-keep class dev.victorialauncher.VictoriaApp { *; }

# SystemUi falls back to reflection into StatusBarManager on pre-Android-12
# builds; keep the call site legible in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
