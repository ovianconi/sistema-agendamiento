package com.estetica.agendamiento.util;

import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * Extiende FechaRelativaUtil para manejar expresiones mixtas: fecha + hora o rango. Ejemplo:
 * "mañana a las 15", "jueves a la tarde", "entre martes y jueves".
 */
public class FechaHoraRelativaUtil {

    public static record ResultadoFechaHora(LocalDate fechaInicio, LocalDate fechaFin,
            String hora) {
    }

    /**
     * Interpreta expresiones que combinan fecha y hora. Devuelve un objeto ResultadoFechaHora que
     * puede contener: - una sola fecha y hora - un rango de fechas (inicio y fin)
     */
    public static ResultadoFechaHora parse(String texto) {
        if (texto == null || texto.isBlank())
            return null;
        texto = texto.trim().toLowerCase(Locale.forLanguageTag("es-PY"));

        LocalDate fechaInicio = null, fechaFin = null;
        String hora = null;

        // 🔹 Detectar rango: “entre martes y jueves”
        Matcher rango = Pattern.compile("entre\\s+(\\w+)\\s+y\\s+(\\w+)").matcher(texto);
        if (rango.find()) {
            String desde = rango.group(1);
            String hasta = rango.group(2);
            fechaInicio = FechaRelativaUtil.parseFecha(desde);
            fechaFin = FechaRelativaUtil.parseFecha(hasta);
            return new ResultadoFechaHora(fechaInicio, fechaFin, null);
        }

        // 🔹 Buscar hora explícita (“a las 15”, “a las 14:30”)
        Matcher mHora =
                Pattern.compile("(?:a las|a la)\\s+(\\d{1,2})(?::(\\d{2}))?").matcher(texto);
        if (mHora.find()) {
            String h = mHora.group(1);
            String min = mHora.group(2);
            hora = (min != null) ? h + ":" + min : h + ":00";
        }

        // 🔹 Buscar expresiones del tipo “a la mañana”, “a la tarde”, “a la noche”
        /*
         * if (hora == null) { if (texto.contains("mañana") && !texto.startsWith("mañana")) hora =
         * "09:00"; else if (texto.contains("tarde")) hora = "17:00"; else if
         * (texto.contains("noche")) hora = "20:00"; }
         */

        // 🔹 Buscar fecha (usa FechaRelativaUtil)
        fechaInicio = FechaRelativaUtil.parseFecha(texto);
        if (fechaInicio == null && texto.contains("el ")) {
            // Extraer la parte después de "el "
            String posibleFecha = texto.substring(texto.indexOf("el ") + 3).trim();
            fechaInicio = FechaRelativaUtil.parseFecha(posibleFecha);
        }

        // Si no se detectó nada, devolver nulo
        if (fechaInicio == null)
            return null;

        return new ResultadoFechaHora(fechaInicio, fechaFin, hora);
    }
}
