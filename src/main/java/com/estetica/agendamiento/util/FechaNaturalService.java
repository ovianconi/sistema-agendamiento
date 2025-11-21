package com.estetica.agendamiento.util;

import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * Servicio robusto para interpretar fechas y horas expresadas en lenguaje
 * natural (español).
 * Compatible con expresiones absolutas, relativas y ordinales (primer domingo,
 * etc.)
 */
@Service
public class FechaNaturalService {

    private static final Map<String, DayOfWeek> DIAS = Map.ofEntries(
            Map.entry("lunes", DayOfWeek.MONDAY), Map.entry("martes", DayOfWeek.TUESDAY),
            Map.entry("miercoles", DayOfWeek.WEDNESDAY),
            Map.entry("miércoles", DayOfWeek.WEDNESDAY), Map.entry("jueves", DayOfWeek.THURSDAY),
            Map.entry("viernes", DayOfWeek.FRIDAY), Map.entry("sabado", DayOfWeek.SATURDAY),
            Map.entry("sábado", DayOfWeek.SATURDAY), Map.entry("domingo", DayOfWeek.SUNDAY));
    private static final Map<String, Month> MESES = Map.ofEntries(Map.entry("enero", Month.JANUARY),
            Map.entry("febrero", Month.FEBRUARY), Map.entry("marzo", Month.MARCH),
            Map.entry("abril", Month.APRIL), Map.entry("mayo", Month.MAY),
            Map.entry("junio", Month.JUNE), Map.entry("julio", Month.JULY),
            Map.entry("agosto", Month.AUGUST), Map.entry("septiembre", Month.SEPTEMBER),
            Map.entry("setiembre", Month.SEPTEMBER), Map.entry("octubre", Month.OCTOBER),
            Map.entry("noviembre", Month.NOVEMBER), Map.entry("diciembre", Month.DECEMBER));

    // Patrón para horas tipo “a las 13”, “a las 13:30”, “13hs”, “3pm”
    private static final Pattern HORA_PATTERN = Pattern.compile(
            "\\b(?:(?:a\\s+las\\s+)|(?:a\\s+la\\s+))?(\\d{1,2})(?::(\\d{1,2}))?\\s*(am|pm|hs|horas)?\\b");

    // Patrón para fechas numéricas 21/10, 21-10-2025, etc.
    private static final Pattern FECHA_NUM_PATTERN = Pattern
            .compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b");

    public record Resultado(LocalDate fecha, LocalTime hora) {
    }

    /** Punto de entrada principal */
    public Resultado parse(String texto) {
        if (texto == null || texto.isBlank())
            return null;
        texto = texto.toLowerCase().trim();

        LocalDate base = LocalDate.now();
        LocalDate fecha = null;
        LocalTime hora = null;

        // --- 1️⃣ Fechas absolutas numéricas (21/10, 21-10-2025)
        Matcher mNum = FECHA_NUM_PATTERN.matcher(texto);
        if (mNum.find()) {
            int dia = Integer.parseInt(mNum.group(1));
            int mes = Integer.parseInt(mNum.group(2));
            int anio = (mNum.group(3) != null) ? Integer.parseInt(mNum.group(3)) : base.getYear();
            if (anio < 100)
                anio += 2000;
            try {
                fecha = LocalDate.of(anio, mes, dia);
            } catch (Exception ignored) {
            }
        }

        // --- 2️⃣ Expresiones relativas simples
        if (fecha == null) {
            if (texto.contains("hoy"))
                fecha = base;
            else if (texto.contains("mañana") || texto.contains("manana"))
                fecha = base.plusDays(1);
            else if (texto.contains("pasado mañana") || texto.contains("pasado manana"))
                fecha = base.plusDays(2);
            else if (texto.contains("ayer"))
                fecha = base.minusDays(1);
        }

        // --- 3️⃣ Días de la semana (este, próximo, que viene, etc.)
        for (var entry : DIAS.entrySet()) {
            if (texto.contains(entry.getKey())) {
                DayOfWeek target = entry.getValue();
                int delta = target.getValue() - base.getDayOfWeek().getValue();
                if (delta <= 0)
                    delta += 7;
                if (texto.contains("próximo") || texto.contains("proximo")
                        || texto.contains("que viene"))
                    delta += 7;
                fecha = base.plusDays(delta);
                break;
            }
        }

        // --- 4️⃣ Ordinales (primer domingo de noviembre, segundo viernes de abril,
        // etc.)
        if (texto.matches(
                ".*(primer|segundo|tercer|cuarto|último|ultimo).*\\s+(lunes|martes|miercoles|miércoles|jueves|viernes|sabado|sábado|domingo).*de.*(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre).*")) {
            try {
                fecha = parseOrdinal(texto, base.getYear());
            } catch (Exception ignored) {
            }
        }

        // --- 5️⃣ Mes + día explícito (“15 de noviembre”)
        if (fecha == null) {
            for (var mesEntry : MESES.entrySet()) {
                if (texto.contains(mesEntry.getKey())) {
                    Matcher num = Pattern.compile("(\\d{1,2})\\s+de\\s+" + mesEntry.getKey())
                            .matcher(texto);
                    if (num.find()) {
                        int dia = Integer.parseInt(num.group(1));
                        Month mes = mesEntry.getValue();
                        int year = base.getYear();
                        if (mes.getValue() < base.getMonthValue())
                            year += 1; // si ya pasó, el año siguiente
                        try {
                            fecha = LocalDate.of(year, mes, dia);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        // --- 6️⃣ Horas (a las 12, 13hs, 3pm, etc.)
        Matcher h = HORA_PATTERN.matcher(texto);
        if (h.find()) {
            try {
                int horaInt = Integer.parseInt(h.group(1));
                int minutos = (h.group(2) != null) ? Integer.parseInt(h.group(2)) : 0;
                String sufijo = h.group(3);

                if ("pm".equalsIgnoreCase(sufijo) && horaInt < 12)
                    horaInt += 12;
                if (horaInt == 12 && "am".equalsIgnoreCase(sufijo))
                    horaInt = 0;

                // si no hay sufijo, y la hora es entre 1-7, se asume tarde (13-19)
                if (sufijo == null && horaInt >= 1 && horaInt <= 7)
                    horaInt += 12;

                hora = LocalTime.of(horaInt, minutos);
            } catch (Exception ignored) {
            }
        }

        // --- 7️⃣ Inferir hora si vino “hora” numérica desde IA (12)
        if (hora == null) {
            Matcher mHoraSimple = Pattern.compile("\\b(\\d{1,2})\\b").matcher(texto);
            if (mHoraSimple.find()) {
                try {
                    int hInt = Integer.parseInt(mHoraSimple.group(1));
                    if (hInt >= 0 && hInt <= 23)
                        hora = LocalTime.of(hInt, 0);
                } catch (Exception ignored) {
                }
            }
        }

        if (fecha == null)
            return null;
        return new Resultado(fecha, hora);
    }

    // =======================
    // FUNCIONES AUXILIARES
    // =======================

    private LocalDate parseOrdinal(String texto, int year) {
        String textoNormalizado = texto.replace("último", "ultimo");
        int ordinal = 1;

        if (textoNormalizado.contains("segundo"))
            ordinal = 2;
        else if (textoNormalizado.contains("tercer"))
            ordinal = 3;
        else if (textoNormalizado.contains("cuarto"))
            ordinal = 4;
        else if (textoNormalizado.contains("ultimo"))
            ordinal = -1;

        DayOfWeek dia = DIAS.entrySet().stream().filter(e -> textoNormalizado.contains(e.getKey()))
                .map(Map.Entry::getValue).findFirst().orElseThrow();

        Month mes = MESES.entrySet().stream().filter(e -> textoNormalizado.contains(e.getKey()))
                .map(Map.Entry::getValue).findFirst().orElseThrow();

        LocalDate firstOfMonth = LocalDate.of(year, mes, 1);

        if (ordinal > 0) {
            int count = 0;
            for (int i = 0; i < 31; i++) {
                LocalDate d = firstOfMonth.plusDays(i);
                if (d.getMonth() != mes)
                    break;
                if (d.getDayOfWeek() == dia) {
                    count++;
                    if (count == ordinal)
                        return d;
                }
            }
        } else {
            // último
            LocalDate last = firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth());
            for (int i = 0; i < 7; i++) {
                LocalDate d = last.minusDays(i);
                if (d.getDayOfWeek() == dia)
                    return d;
            }
        }

        return null;
    }

}
