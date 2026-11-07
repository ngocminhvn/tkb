# ProGuard / R8 rules for TKB SV

# Preserve line numbers and source file names for crash reporting
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlinx Serialization
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * implements kotlinx.serialization.internal.GeneratedSerializer {
    <fields>;
    <methods>;
}

# Preserve data & network models
-keep class com.sv.tnu.tnut.** { *; }
-keep class com.sv.tnu.data.** { *; }
-keep class com.sv.tnu.update.** { *; }

# Retrofit & OkHttp
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

# Android AppWidget & RemoteViews
-keep class com.sv.tnu.widget.** { *; }
