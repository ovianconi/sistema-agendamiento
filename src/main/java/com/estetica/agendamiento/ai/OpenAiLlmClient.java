package com.estetica.agendamiento.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@Primary
public class OpenAiLlmClient implements LlmClient {

    @Value("${openai.api-key}")
    private String openAiApiKey;

    private final RestTemplate http;

    // ===============================================================
    // 🧩 Constructor: fuerza UTF-8 en el RestTemplate
    // ===============================================================
    public OpenAiLlmClient() {
        this.http = new RestTemplate();

        // ⚙️ Forzar codificación UTF-8 para evitar pérdida de tildes
        this.http.getMessageConverters().stream().filter(
                c -> c instanceof org.springframework.http.converter.StringHttpMessageConverter)
                .forEach(c -> ((org.springframework.http.converter.StringHttpMessageConverter) c)
                        .setDefaultCharset(StandardCharsets.UTF_8));
    }

    // ===============================================================
    // 🧠 Detección de intención (intents, fecha, hora, etc.)
    // ===============================================================
    @Override
    public IntentResult extractIntent(String texto, String locale) {
        try {
            // ✅ Normalizar tildes antes de enviar al modelo
            texto = normalizarAcentos(texto);

            String prompt =
                    """
                            Analiza el siguiente mensaje de un cliente de una clínica de estética.
                            Determina su intención entre las siguientes opciones:
                            - consultar_sesiones_restantes
                            - cancelar_por_fecha_hora
                            - cancelar_por_tratamiento_fecha
                            - agendar_sesion
                            - saludo
                            - agradecimiento

                            Además, detecta cómo se identifica el cliente si es posible:
                            - "por_telefono"
                            - "por_documento"
                            - "por_nombre"
                            Si no lo menciona, devuelve "por_telefono".

                            Devuelve SOLO un JSON con los campos:
                            { "intent": ..., "tratamiento": ..., "fecha": ..., "hora": ..., "clienteIdent": ... }

                            ⚠️ Devuelve todos los valores en minúsculas y SIN acentos.
                            Mensaje: "%s"
                            """
                            .formatted(texto);

            Map<String, Object> body = Map.of("model", "gpt-4.1-mini", "temperature", 0.3,
                    "messages",
                    new Object[] {Map.of("role", "system", "content",
                            "Eres un analizador de lenguaje natural que devuelve intenciones en JSON."),
                            Map.of("role", "user", "content", prompt)});

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(openAiApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = http
                    .postForEntity("https://api.openai.com/v1/chat/completions", req, Map.class);

            // ==========================================================
            // 🧾 Extraer la respuesta cruda y forzar UTF-8 correctamente
            // ==========================================================
            Map<?, ?> choice =
                    (Map<?, ?>) ((java.util.List<?>) resp.getBody().get("choices")).get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            String content = (String) message.get("content");

            // 🔍 Log crudo del modelo (útil para depuración)
            System.out.println("🧾 Respuesta RAW del modelo: " + content);

            // 🔧 Reconvertir explícitamente a UTF-8 (defensivo)
            if (content != null) {
                content = new String(content.getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8);
            }

            // ==========================================================
            // 🧠 Parsear el JSON y devolver IntentResult
            // ==========================================================
            IntentResult result = parseIntentJson(content);

            // ✅ Normalizar los campos parseados
            if (result != null) {
                result.setFecha(normalizarAcentos(result.getFecha()));
                result.setHora(normalizarAcentos(result.getHora()));
                result.setTratamiento(normalizarAcentos(result.getTratamiento()));
            }

            return result;

        } catch (Exception e) {
            System.err.println("❌ Error en extractIntent: " + e.getMessage());
            return new IntentResult("saludo", null, null, null, null);
        }
    }

    // ===============================================================
    // 🔍 Parser simple del JSON generado por el modelo
    // ===============================================================
    private IntentResult parseIntentJson(String json) {
        if (json == null || json.isBlank())
            return new IntentResult("saludo", null, null, null, null);

        json = json.replaceAll("[^\\x20-\\x7E]", ""); // limpiar caracteres invisibles
        String intent = findValue(json, "intent");
        String tratamiento = findValue(json, "tratamiento");
        String fecha = findValue(json, "fecha");
        String hora = findValue(json, "hora");
        String clienteIdent = findValue(json, "clienteIdent");

        if (intent == null)
            intent = "saludo";

        return new IntentResult(intent, tratamiento, fecha, hora, clienteIdent);
    }

    private String findValue(String json, String key) {
        try {
            var regex = "\"%s\"\\s*:\\s*\"([^\"]+)\"".formatted(key);
            var matcher = java.util.regex.Pattern.compile(regex).matcher(json);
            if (matcher.find())
                return matcher.group(1);
        } catch (Exception ignored) {
        }
        return null;
    }

    // ===============================================================
    // ✨ Normalizador simple de acentos y ñ
    // ===============================================================
    private String normalizarAcentos(String texto) {
        if (texto == null)
            return null;

        String n = texto.replace("á", "a").replace("à", "a").replace("ä", "a").replace("é", "e")
                .replace("è", "e").replace("ë", "e").replace("í", "i").replace("ì", "i")
                .replace("ï", "i").replace("ó", "o").replace("ò", "o").replace("ö", "o")
                .replace("ú", "u").replace("ù", "u").replace("ü", "u").replace("ñ", "n");

        n = n.replaceAll("[^\\p{L}\\p{Nd}\\s:]", ""); // limpia símbolos no deseados
        n = n.replaceAll("\\s+", " ").trim().toLowerCase();

        return n;
    }

    @Override
    public String generateResponse(String prompt, String locale, double temperature) {
        try {
            Map<String, Object> body = Map.of("model", "gpt-4.1-mini", "messages",
                    new Object[] {Map.of("role", "system", "content",
                            "Eres un asistente que responde con amabilidad y naturalidad, en tono WhatsApp."),
                            Map.of("role", "user", "content", prompt)},
                    "temperature", temperature // 🔥 controla el nivel de creatividad
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(openAiApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = http
                    .postForEntity("https://api.openai.com/v1/chat/completions", req, Map.class);

            String content = (String) ((Map<?, ?>) ((Map<?, ?>) ((java.util.List<?>) resp.getBody()
                    .get("choices")).get(0)).get("message")).get("content");

            return content != null ? content.trim() : "Ocurrió un error generando la respuesta.";

        } catch (Exception e) {
            System.err.println("❌ Error en generateResponse: " + e.getMessage());
            return "Tuvimos un inconveniente al procesar la respuesta 😔.";
        }
    }
}
