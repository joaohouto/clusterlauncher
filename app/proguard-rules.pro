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
-keep class com.joaohouto.clusterlauncher.data.receiver.** { *; }
-keep class com.joaohouto.clusterlauncher.data.model.** { *; }
-keep class com.joaohouto.clusterlauncher.utils.** { *; }

# Compose Runtime
-keepclassmembers class androidx.compose.runtime.** { *; }