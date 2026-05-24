package com.estetica.agendamiento.ai;

import com.estetica.agendamiento.model.ChatConversationState;
import com.estetica.agendamiento.model.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class ConversationLlmClient {

    @Value("${openai.api-key}")
    private String openAiApiKey;

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public ConversationAiResult analizar(
            String mensajeActual,
            ChatConversationState estado,
            List<ChatMessage> historial) {
        try {
            String prompt = construirPrompt(mensajeActual, estado, historial);

            Map<String, Object> body = Map.of(
                    "model", "gpt-4.1-mini",
                    "temperature", 0.2,
                    "messages", new Object[] {
                            Map.of("role", "system", "content", systemPrompt()),
                            Map.of("role", "user", "content", prompt)
                    });

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(openAiApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> resp = http.postForEntity(
                    "https://api.openai.com/v1/chat/completions",
                    new HttpEntity<>(body, headers),
                    Map.class);

            Map<?, ?> choice = (Map<?, ?>) ((List<?>) resp.getBody().get("choices")).get(0);
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            String content = (String) message.get("content");

            System.out.println("🧠 Conversation RAW: " + content);

            return parseJson(content);

        } catch (Exception e) {
            System.err.println("❌ Error en ConversationLlmClient: " + e.getMessage());

            ConversationAiResult fallback = new ConversationAiResult();
            fallback.setIntent("conversacion_general");
            fallback.setTemaGeneral("error_ia");
            fallback.setRespuestaSugerida(
                    "Tuve un inconveniente interpretando tu mensaje 😕. ¿Podés repetirlo de otra forma?");
            return fallback;
        }
    }

    private ConversationAiResult parseJson(String content) throws Exception {
        if (content == null || content.isBlank()) {
            return new ConversationAiResult();
        }

        String json = content.trim();

        if (json.startsWith("```")) {
            json = json.replaceAll("(?s)^```json\\s*", "")
                    .replaceAll("(?s)^```\\s*", "")
                    .replaceAll("```$", "")
                    .trim();
        }

        return mapper.readValue(json, ConversationAiResult.class);
    }

    private String construirPrompt(
            String mensajeActual,
            ChatConversationState estado,
            List<ChatMessage> historial) {
        StringBuilder sb = new StringBuilder();

        sb.append("Fecha actual: ").append(LocalDate.now()).append("\n\n");

        sb.append("ESTADO ACTUAL DEL FLUJO:\n");
        sb.append("{\n");
        sb.append("  \"flujoActivo\": \"").append(nullSafe(estado.getFlujoActivo())).append("\",\n");
        sb.append("  \"esperando\": \"").append(nullSafe(estado.getEsperando())).append("\",\n");
        sb.append("  \"tratamiento\": \"").append(nullSafe(estado.getTratamiento())).append("\",\n");
        sb.append("  \"fecha\": \"").append(estado.getFecha() != null ? estado.getFecha() : "").append("\",\n");
        sb.append("  \"hora\": \"").append(estado.getHora() != null ? estado.getHora() : "").append("\",\n");
        sb.append("  \"esperandoConfirmacion\": ").append(estado.isEsperandoConfirmacion()).append(",\n");
        sb.append("  \"lastBotQuestion\": \"").append(nullSafe(estado.getLastBotQuestion())).append("\"\n");
        sb.append("}\n\n");

        sb.append("HISTORIAL RECIENTE:\n");
        for (ChatMessage m : historial) {
            sb.append(m.getRole())
                    .append(": ")
                    .append(m.getContent())
                    .append("\n");
        }

        sb.append("\nMENSAJE ACTUAL DEL USUARIO:\n");
        sb.append(mensajeActual).append("\n\n");

        sb.append("Respondé SOLO JSON válido.\n");

        return sb.toString();
    }

    private String systemPrompt() {
        return """
                Sos el asistente virtual de una clínica estética y de rehabilitación en Paraguay.

                Tu función principal es ayudar a:
                1. consultar sesiones restantes,
                2. agendar sesiones,
                3. cancelar sesiones,
                4. orientar de forma general sobre tratamientos sin diagnosticar.

                También podés responder preguntas casuales de forma breve y amable,
                pero siempre debés reconducir hacia las funciones del sistema.

                REGLA CRÍTICA:
                Si hay flujoActivo y esperando, interpretá el mensaje del usuario dentro de ese flujo.
                No cambies de intención salvo que el usuario pida claramente empezar otra cosa.

                Si el usuario dice claramente algo como:
                - "mejor quiero cancelar"
                - "olvidá eso, quiero agendar"
                - "dejemos eso, quiero consultar sesiones"
                entonces devolvé cambiarFlujo=true y el nuevo intent.

                Si solo responde un dato corto como "Lipolaser", "mañana", "a las 15", "sí" o "no",
                NO cambies de flujo.

                No debés:
                - inventar sesiones restantes,
                - inventar disponibilidad,
                - inventar tratamientos del cliente,
                - diagnosticar,
                - decir que una persona necesita un tratamiento,
                - confirmar agendamientos sin validación del backend.

                Si el usuario pide recomendaciones estéticas, indicá que la recomendación final
                debe hacerla un profesional de la clínica y ofrecé realizar tus funciones principales.

                Fechas:
                - Si el usuario dice "mañana", usar la fecha actual + 1 día.
                - Si dice día de semana + número, como "martes 26", priorizá el número 26.
                - Si dice una fecha inexistente, pedí aclaración de la fecha.
                - Si dice próximo + día de la semana, como "próximo lunes", usar el siguiente lunes más próximo que todavía no pasó

                Intenciones posibles:
                - agendar_sesion
                - cancelar_sesion
                - consultar_sesiones_restantes
                - corregir_dato
                - confirmar
                - negar
                - saludo
                - agradecimiento
                - conversacion_general
                - pregunta_fuera_de_alcance
                - pregunta_sobre_tratamientos
                - pregunta_recomendacion_estetica
                - consulta_multiple

                Formato obligatorio:
                {
                  "intent": "",
                  "flujoActivo": "",
                  "esperando": "",
                  "tratamiento": "",
                  "fecha": "",
                  "hora": "",
                  "confirmacion": null,
                  "campoACorregir": "",
                  "temaGeneral": "",
                  "respuestaSugerida": "",
                  "cambiarFlujo": false
                }
                """;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}