# El extractor y sus dependencias usan reflexión: se conservan completos.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }

# Clases opcionales que referencian librerías que no existen en Android.
-dontwarn **
