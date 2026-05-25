# Keep Sherpa-ONNX Native and JNI classes
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }

# Keep MediaPipe and Tasks GenAI native classes
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }

# Keep Room database and entity classes
-keep class com.example.mobileaudiowhatsapp.data.** { *; }
-keepclassmembers class com.example.mobileaudiowhatsapp.data.** { *; }

# Ignore warnings for missing optional dependencies in MediaPipe
-dontwarn com.google.auto.value.**
-dontwarn com.google.mediapipe.framework.image.**
