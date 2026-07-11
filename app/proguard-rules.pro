# --- Media3 / ExoPlayer ---
-dontwarn androidx.media3.**
# Keep extension renderer / reflection-instantiated Media3 classes.
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.datasource.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# --- Coroutines ---
-dontwarn kotlinx.coroutines.**

# Keep generic signatures for reflection-based libs.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
