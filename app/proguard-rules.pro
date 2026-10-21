# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class org.ivanivorontsov.launcher.**$$serializer { *; }
-keepclassmembers class org.ivanivorontsov.launcher.** { *** Companion; }
-keepclasseswithmembers class org.ivanivorontsov.launcher.** { kotlinx.serialization.KSerializer serializer(...); }
# Services referenced from the manifest are kept by AAPT rules automatically.
-dontwarn org.jetbrains.annotations.**

# «Боец поневоле» init is called reflectively from LauncherApp (so the «Лёгкий» flavor compiles without modules).
-keep class org.ivanivorontsov.boec.data.Store { public *; }
-keep class org.ivanivorontsov.boec.alarm.Notifier { public *; }
