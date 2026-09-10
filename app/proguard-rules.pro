# The engine and save models are plain Kotlin with no reflection, so R8's
# defaults are enough for them. kotlinx.serialization generates its own
# serializers at compile time; these rules keep R8 from stripping the
# companion-object hooks it looks them up through.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.earthgame.idle.data.persistence.**$$serializer { *; }
-keepclassmembers class com.earthgame.idle.data.persistence.** {
    *** Companion;
}
-keepclasseswithmembers class com.earthgame.idle.data.persistence.** {
    kotlinx.serialization.KSerializer serializer(...);
}
