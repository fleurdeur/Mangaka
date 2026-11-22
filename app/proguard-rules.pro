# Kurenai release rules

# Keep source adapters loaded reflectively by the Kotatsu registry.
-keep class org.kotatsu.** { *; }
-keep class app.kurenai.core.parsers.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt / Dagger generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *

# WorkManager workers instantiated by Hilt
-keep class app.kurenai.core.data.DownloadWorker { *; }

# Media3 / ExoPlayer
-dontwarn com.google.common.**
-keep class androidx.media3.** { *; }

# OkHttp / Okio optional platform classes
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep line numbers for readable crash reports without exposing source.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
