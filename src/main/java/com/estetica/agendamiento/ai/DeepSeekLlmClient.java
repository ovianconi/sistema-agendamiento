package com.estetica.agendamiento.ai;

import org.springframework.stereotype.Component;

@Component
public class DeepSeekLlmClient implements LlmClient {

    @Override
    public IntentResult extractIntent(String texto, String locale) {
        texto = texto.toLowerCase();

        String clienteIdent = "por_telefono";
        if (texto.contains("soy") || texto.contains("me llamo"))
            clienteIdent = "por_nombre";
        if (texto.contains("documento") || texto.matches(".*\\d{5,}.*"))
            clienteIdent = "por_documento";

        if (texto.contains("hola") || texto.contains("buen día"))
            return new IntentResult("saludo", null, null, null, clienteIdent);
        if (texto.contains("gracias"))
            return new IntentResult("agradecimiento", null, null, null, clienteIdent);
        if (texto.contains("cuántas") && texto.contains("sesiones"))
            return new IntentResult("consultar_sesiones_restantes", detectarTratamiento(texto),
                    null, null, clienteIdent);
        if (texto.contains("cancelar") && texto.contains("mañana"))
            return new IntentResult("cancelar_por_fecha_hora", detectarTratamiento(texto), "mañana",
                    extraerHora(texto), clienteIdent);
        if (texto.contains("agendar") || texto.contains("reservar"))
            return new IntentResult("agendar_sesion", detectarTratamiento(texto),
                    extraerFecha(texto), extraerHora(texto), clienteIdent);

        return new IntentResult("saludo", null, null, null, clienteIdent);
    }


    private String detectarTratamiento(String texto) {
        if (texto.contains("radio"))
            return "radiofrecuencia";
        if (texto.contains("crio"))
            return "criolipólisis";
        if (texto.contains("masaje"))
            return "masaje relajante";
        return null;
    }

    private String extraerHora(String texto) {
        var matcher = java.util.regex.Pattern.compile("(\\d{1,2})(:?\\d{0,2})").matcher(texto);
        return matcher.find()
                ? matcher.group(1) + (matcher.group(2).isEmpty() ? ":00" : matcher.group(2))
                : null;
    }

    private String extraerFecha(String texto) {
        if (texto.contains("mañana"))
            return "mañana";
        if (texto.contains("viernes"))
            return "viernes";
        if (texto.contains("sábado"))
            return "sábado";
        return null;
    }

    @Override
    public String generateResponse(String prompt, String locale, double temperature) {
        // TODO
        return "Tuvimos un inconveniente al procesar la respuesta 😔.";
    }

}
