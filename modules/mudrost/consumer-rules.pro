# ONNX Runtime uses JNI and reflection
-keep class ai.onnxruntime.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class org.ivanivorontsov.mudrost.engine.**$$serializer { *; }
-keepclassmembers class org.ivanivorontsov.mudrost.engine.** { *** Companion; }
-keepclasseswithmembers class org.ivanivorontsov.mudrost.engine.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
