# [APRENDIZAJE] Reglas para Media3 y ExoPlayer
# Estas reglas evitan que la radio falle en la Play Store cuando Google optimice el código.

-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.ui.** { *; }

# Mantener clases de Hilt y Coil para que no haya errores de carga de imágenes o dependencias
-keep class io.coilkt.** { *; }
-keep class com.google.dagger.** { *; }
