# R8 / ProGuard rules for Cluster Launcher

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# DataStore Preferences
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Keep Application and Automotive Components
-keep class com.joaohouto.clusterlauncher.ClusterLauncherApplication { *; }
-keep class com.joaohouto.clusterlauncher.MainActivity { *; }
-keep class com.joaohouto.clusterlauncher.media.** { *; }
-keep class com.joaohouto.clusterlauncher.player.** { *; }
-keep class com.joaohouto.clusterlauncher.data.receiver.** { *; }
-keep class com.joaohouto.clusterlauncher.data.model.** { *; }
-keep class com.joaohouto.clusterlauncher.data.local.entity.** { *; }
-keep class com.joaohouto.clusterlauncher.utils.** { *; }

# AndroidX Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.media3.session.MediaSessionService {
    public <init>();
}
-keep class * extends androidx.media3.session.MediaSession$Callback { *; }

# Android Architecture Components / Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.Dao { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}

# Compose Runtime
-keepclassmembers class androidx.compose.runtime.** { *; }