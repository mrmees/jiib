# R8 / ProGuard keep rules for Dinghy Display (release build is the MEASURED
# and SHIPPED artifact). Keep just enough that kotlinx.serialization codegen and
# Retrofit model reflection survive shrinking — without these the cleartext
# smoke test (01-02) would fail at runtime as a false negative (RESEARCH
# Pitfall 5 / Security Domain note).

# --- kotlinx.serialization ---
# Keep the generated serializers and the @Serializable companions.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep `Companion` objects and `serializer()` accessors of @Serializable types.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit ---
# Retrofit does reflection on method signatures and generic return types.
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Coil 3 ---
-dontwarn coil3.**
