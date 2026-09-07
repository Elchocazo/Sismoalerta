# Security & Anti-Reverse Engineering Obfuscation Rules for SismoAlerta
-repackageclasses ''
-allowaccessmodification
-renamesourcefileattribute SourceFile
-keepattributes Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Preserve Room Database Entities & Crypto Models from obfuscation breakage
-keep class com.example.data.local.entity.** { *; }
-keepclassmembers class com.example.data.local.entity.** { *; }
-keep class com.example.data.local.dao.** { *; }
-keep class com.example.data.crypto.** { *; }

# Firebase & Play Services Keep Rules
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Strip Debug Logs in Release Builds (Prevents GPS & Sensitive Phone Leaks)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
