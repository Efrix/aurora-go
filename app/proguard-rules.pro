# ===== Aurora GO - ProGuard Rules =====

# --- Kotlinx Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.efrix.aurorago.data.model.**$$serializer { *; }
-keepclassmembers class com.efrix.aurorago.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.efrix.aurorago.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Supabase Ktor ---
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# --- Gson ---
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.efrix.aurorago.data.model.PerfilLegacy { *; }
-keep class com.efrix.aurorago.data.model.MiedoLegacy { *; }
-keep class com.efrix.aurorago.data.model.CheckinLegacy { *; }
-keep class com.efrix.aurorago.data.model.EmocionCheckinLegacy { *; }
-keep class com.google.gson.** { *; }

# --- Mapsforge ---
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**

# --- CameraX ---
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- Lottie ---
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# --- Google Play Services Location ---
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# --- WorkManager ---
-keep class androidx.work.** { *; }

# --- Navigation Compose (safe args) ---
-keepnames class com.efrix.aurorago.ui.navegation.Rutas

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Keep BuildConfig fields ---
-keep class com.efrix.aurorago.BuildConfig { *; }

# --- Remove Log.d/Log.v in release (optimization) ---
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# --- Preserve line numbers for crash reports ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
