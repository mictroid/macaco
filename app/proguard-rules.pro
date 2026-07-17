# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Google API Client (Drive) — reflection-based JSON model, must not be renamed/stripped.
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keepclassmembers class * extends com.google.api.client.json.GenericJson {
  <fields>;
}
-dontwarn com.google.api.client.**

# kotlinx.serialization — defensive backstop on top of the library's bundled consumer rules.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keep,includedescriptorclasses class com.houseofmmminq.macaco.data.model.**$$serializer { *; }
-keepclassmembers class com.houseofmmminq.macaco.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.houseofmmminq.macaco.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep line numbers for readable (and Crashlytics-deobfuscatable) release stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager (used by ReminderScheduler for periodic reminders) persists scheduled work via an
# internal Room database. Room instantiates its generated *_Impl class via reflection
# (Class.getDeclaredConstructor()) — R8 was stripping WorkDatabase_Impl's no-arg constructor,
# crashing at process start with "NoSuchMethodException: WorkDatabase_Impl.<init>" before
# MainActivity ever rendered. Found via the on-device signed-release QA pass this brief requires.
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**

# Firebase ComponentRegistrars are instantiated reflectively by ComponentDiscovery from
# AndroidManifest meta-data, so nothing calls their no-arg constructors directly. The classes are
# @Keep-annotated and survive, but R8 strips the unused <init>() — discovery then logs
# "Invalid component registrar / Could not instantiate ..." at WARN and the component never
# registers. FirebaseAppCheck.getInstance() returned null and killed the process in
# Application.onCreate; CrashlyticsRegistrar was also a casualty, so release crashes could not
# report themselves. Same shape as the WorkDatabase_Impl case above.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}