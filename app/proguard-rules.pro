# ProGuard / R8 rules for TKB SV

# Preserve line numbers and source file names for crash reporting
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Preserve network, grades and updater classes
-keep class com.sv.tnu.schools.tnut.** { *; }
-keep class com.sv.tnu.grades.** { *; }
-keep class com.sv.tnu.update.** { *; }

# Retrofit & OkHttp
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

# Android AppWidget & RemoteViews
-keep class com.sv.tnu.widget.** { *; }

# JNI uses exported Rust symbols bound to these exact method names.
# R8 must not rename the Kotlin entry points in release builds.
-keep class com.sv.tnu.rust.RustScheduleCore { *; }

-keep class com.sv.tnu.rust.RustPortalCore { *; }
-keep class com.sv.tnu.rust.RustGradeCore { *; }

# Tink's Error Prone annotations are source/static-analysis metadata only.
# They have no runtime behavior; suppress R8 missing-annotation warnings.
-dontwarn com.google.errorprone.annotations.**

-keep class com.sv.tnu.rust.RustCacheStore { *; }
-keep class com.sv.tnu.rust.RustCacheStore$Companion { *; }

# Offline quiz JNI entry points must retain exported Rust symbol names.
-keep class com.sv.tnu.rust.RustQuizCore { *; }

-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**
