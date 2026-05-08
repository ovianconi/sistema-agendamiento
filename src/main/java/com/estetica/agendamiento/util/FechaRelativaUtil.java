package com.estetica.agendamiento.util;

import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;

public class FechaRelativaUtil {

    private static final Locale LOCALE_ES = Locale.forLanguageTag("es-PY");

    private static String normalizar(String texto) {
        if (texto == null)
            return "";
        String n = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", ""); // elimina
        return n.toLowerCase(LOCALE_ES).trim();
    }

    public static LocalDate parseFecha(String texto) {
        if (texto == null || texto.isBlank())
            return null;

        LocalDate hoy = LocalDate.now();
        String t = normalizar(texto);

        // === Casos directos ===
        if (t.contains("hoy"))
            return hoy;
        if (t.contains("manana"))
            return hoy.plusDays(1);
        if (t.contains("pasado manana"))
            return hoy.plusDays(2);

        // === Map de días ===
        Map<String, DayOfWeek> dias = new LinkedHashMap<>();
        dias.put("lunes", DayOfWeek.MONDAY);
        dias.put("martes", DayOfWeek.TUESDAY);
        dias.put("miercoles", DayOfWeek.WEDNESDAY);
        dias.put("jueves", DayOfWeek.THURSDAY);
        dias.put("viernes", DayOfWeek.FRIDAY);
        dias.put("sabado", DayOfWeek.SATURDAY);
        dias.put("domingo", DayOfWeek.SUNDAY);

        for (var entry : dias.entrySet()) {
            String palabraDia = entry.getKey();
            int idxDia = t.indexOf(palabraDia);
            if (idxDia == -1)
                continue;

            DayOfWeek diaObjetivo = entry.getValue();
            DayOfWeek hoyDia = hoy.getDayOfWeek();
            int diff = diaObjetivo.getValue() - hoyDia.getValue();

            // Buscar modificadores
            int idxEste = t.indexOf("este");
            int idxProximo = t.indexOf("proximo");
            int idxQueViene = t.indexOf("que viene");

            boolean hayEsteAntes = (idxEste != -1 && idxEste < idxDia);
            boolean hayProximoAntes = (idxProximo != -1 && idxProximo < idxDia);
            boolean hayQueVieneDespues = (idxQueViene != -1 && idxQueViene > idxDia);

            // === “este martes” → esta semana (si no pasó)
            if (hayEsteAntes || hayProximoAntes || hayQueVieneDespues) {
                if (diff < 0)
                    diff += 7;
                return hoy.plusDays(diff);
            }

            // === “próximo martes” o “el martes que viene” → próxima semana

            // === Solo “martes” → si no pasó, esta semana; si ya pasó, próxima
            if (diff < 0)
                diff += 7;
            return hoy.plusDays(diff);
        }

        // === Formatos “3 de octubre”, “3/10”, “3/10/2025” ===
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d 'de' MMMM", LOCALE_ES);
            LocalDate parsed = LocalDate.parse(texto.toLowerCase(LOCALE_ES).trim(), fmt);
            return parsed.withYear(hoy.getYear());
        } catch (DateTimeParseException ignored) {
        }

        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d/M/yyyy");
            return LocalDate.parse(texto.trim(), fmt);
        } catch (DateTimeParseException ignored) {
        }

        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d/M");
            LocalDate parsed = LocalDate.parse(texto.trim(), fmt);
            return parsed.withYear(hoy.getYear());
        } catch (DateTimeParseException ignored) {
        }

        return null;
    }
}
