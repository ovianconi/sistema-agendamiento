package com.estetica.agendamiento.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class MetaMediaService {

    @Value("${whatsapp.access-token}")
    private String whatsappToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GRAPH_BASE_URL = "https://graph.facebook.com/v18.0/";

    // ============================================================
    // 📥 Descargar el archivo de audio usando el media_id
    // ============================================================
    public byte[] downloadMedia(String mediaId) {
        try {
            // Paso 1: obtener URL del recurso
            String mediaUrlEndpoint = GRAPH_BASE_URL + mediaId;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(whatsappToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> resp = restTemplate.exchange(mediaUrlEndpoint, HttpMethod.GET, entity, Map.class);

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                System.err.println("⚠️ No se pudo obtener la URL del media: " + resp);
                return null;
            }

            String url = (String) resp.getBody().get("url");
            if (url == null) {
                System.err.println("⚠️ URL nula en respuesta de Meta para media_id=" + mediaId);
                return null;
            }

            // Paso 2: descargar binario del audio
            HttpEntity<Void> downloadEntity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> fileResp = restTemplate.exchange(url, HttpMethod.GET, downloadEntity, byte[].class);

            if (!fileResp.getStatusCode().is2xxSuccessful()) {
                System.err.println("⚠️ No se pudo descargar el archivo de audio desde Meta.");
                return null;
            }

            return fileResp.getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ============================================================
    // 💬 Enviar mensaje de texto al usuario vía WhatsApp Cloud API
    // ============================================================
    public void sendWhatsappMessage(String telefonoDestino, String texto) {
        try {
            String url = GRAPH_BASE_URL + phoneNumberId + "/messages";

            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", telefonoDestino,
                    "type", "text",
                    "text", Map.of(
                            "body", texto));

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(whatsappToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            System.out.println("✅ Mensaje enviado a WhatsApp: " + response.getBody());

        } catch (Exception e) {
            System.err.println("❌ Error al enviar mensaje a WhatsApp: " + e.getMessage());
        }
    }
}
