package com.radiofides.data.model

/**
 * Esta clase representa un bloque de la programación.
 * @param startTime Hora en formato "HH:mm" (ej: "05:45")
 * @param name Nombre del programa
 * @param conductor Nombre del locutor
 * @param isMusical Si es verdadero, la app mostrará la información del JSON (canción/foto)
 */
data class Program(
    val startTime: String,
    val name: String,
    val conductor: String = "",
    val isMusical: Boolean = false
)
