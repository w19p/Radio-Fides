package com.radiofides.data

import com.radiofides.data.model.Program
import java.util.Calendar
import java.util.TimeZone

object ScheduleProvider {

    private val weekSchedule = listOf(
        Program("05:45", "Sartasiñani", "Ramiro Mamani"),
        Program("06:30", "La Hora del País: Matinal", "Gabriela Perez"),
        Program("08:30", "El Café de la Mañana", "Mario Espinoza Osorio"),
        Program("10:10", "AM - Antes de Mediodia", "Jhon Arandia"),
        Program("12:00", "La Hora del país: Meridiano", "Gabriela Perez"),
        Program("13:00", "Futbolmania", "Gonzalo Cobo"),
        Program("14:00", "El Show es Noticia", "Denise Mendieta"),
        Program("16:00", "Sin Concesiones", "Mery Vaca"),
        Program("17:00", "Radio en vivo", "Andrés Rojas"),
        Program("18:00", "La Hora del País: Vespertino", "Nancy Vacaflor"),
        Program("19:00", "Musical / Hablando de Bolivia", "Natalia Aparicio", isMusical = true),
        Program("20:00", "Al Rojo Vivo Radio", "Roger Veneros"),
        Program("22:00", "Cierre de Emisión")
    )

    private val saturdaySchedule = listOf(
        Program("05:45", "Sartasiñani", "Ramiro Mamani"),
        Program("07:00", "La Hora del País: Matinal", "Gabriela Perez"),
        Program("09:00", "Musical", isMusical = true),
        Program("10:00", "El Menu de la semana", "Denise Mendieta"),
        Program("12:00", "La Hora del país: Meridiano"),
        Program("13:30", "Musical Fides", isMusical = true),
        Program("22:00", "Cierre de emisión")
    )

    private val sundaySchedule = listOf(
        Program("07:00", "Musical Fides", isMusical = true),
        Program("07:30", "Santa Misa I", "Iglesia de San Calixto"),
        Program("08:30", "Musical Fides", isMusical = true),
        Program("10:00", "Santa Misa II", "Iglesia de San Calixto"),
        Program("11:00", "Bolivia, el país que construimos"),
        Program("13:00", "Musical Fides o Futbolmania", isMusical = true),
        Program("19:00", "Santa Misa III", "Iglesia de San Calixto"),
        Program("20:30", "Cierre de Emisión")
    )

    // CORRECCIÓN: usamos la zona horaria de Bolivia explícitamente
    // para que funcione igual sin importar dónde esté el dispositivo
    private fun getBoliviaCalendar(): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("America/La_Paz"))

    fun getTodaySchedule(): List<Program> {
        val calendar = getBoliviaCalendar()
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> saturdaySchedule
            Calendar.SUNDAY -> sundaySchedule
            else -> weekSchedule
        }
    }

    fun getCurrentProgram(): Program {
        val calendar = getBoliviaCalendar()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentTime = "%02d:%02d".format(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )

        val currentList = getTodaySchedule()

        // CORRECCIÓN: si no hay ningún programa que haya empezado aún
        // (madrugada antes de las 05:45) mostramos el Cierre de Emisión
        // del día anterior, que es el último programa de la lista
        var foundProgram = currentList.lastOrNull { it.startTime <= currentTime }
            ?: currentList.last() // ← CORRECCIÓN: last() en lugar de first()

        // Lógica especial para el programa de las 19:00 entre semana
        if (dayOfWeek != Calendar.SATURDAY &&
            dayOfWeek != Calendar.SUNDAY &&
            foundProgram.startTime == "19:00"
        ) {
            foundProgram = if (dayOfWeek == Calendar.TUESDAY ||
                dayOfWeek == Calendar.THURSDAY
            ) {
                foundProgram.copy(
                    name = "Hablando de Bolivia con una taza de café",
                    isMusical = false
                )
            } else {
                foundProgram.copy(name = "Musical", isMusical = true)
            }
        }

        return foundProgram
    }
}