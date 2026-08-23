# Shizuku's provider is instantiated by the system.
-keep class rikka.shizuku.** { *; }

# We reflect on framework internals by name; keep our own reflection helpers intact.
-keepclassmembers class dev.takeru.perapplocale.core.** { *; }
