package com.estetica.agendamiento.whatsapp;

import com.estetica.agendamiento.dto.WhatsappMessageDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import com.estetica.agendamiento.service.WhatsappConversationService;
import com.estetica.agendamiento.service.FlowResult;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${app.api.prefix}/webhooks/whatsapp")
public class WebhookController {

    // @Autowired
    // private NluOrchestrator orchestrator;

    @Autowired
    private WhatsappConversationService whatsappConversationService;

    @Autowired
    private MetaMediaService metaMediaService;

    @Value("${whatsapp.verify-token}")
    private String verifyTokenConfig;

    // ===========================================================
    // ✅ VERIFICACIÓN DEL WEBHOOK (GET)
    // ===========================================================
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        if ("subscribe".equals(mode) && verifyTokenConfig.equals(verifyToken)) {
            System.out.println("✅ Webhook verificado correctamente por Meta.");
            return ResponseEntity.ok(challenge);
        } else {
            System.err.println("❌ Falló la verificación del Webhook.");
            return ResponseEntity.status(403).body("Verification failed");
        }
    }

    // ===========================================================
    // 📩 RECEPCIÓN DE MENSAJES DESDE META (POST)
    // ===========================================================
    @PostMapping
    public ResponseEntity<String> receiveMessage(@RequestBody Map<String, Object> payload) {
        try {
            System.out.println("📦 Payload recibido desde Meta:");
            System.out.println(payload);

            // 1️⃣ Validar tipo de objeto
            if (!"whatsapp_business_account".equals(payload.get("object"))) {
                return ResponseEntity.ok("ignored");
            }

            // 2️⃣ Obtener entry
            List<Map<String, Object>> entryList = (List<Map<String, Object>>) payload.get("entry");
            if (entryList == null || entryList.isEmpty())
                return ResponseEntity.ok("no entry");

            Map<String, Object> entry = entryList.get(0);
            List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.get("changes");
            if (changes == null || changes.isEmpty())
                return ResponseEntity.ok("no changes");

            Map<String, Object> value = (Map<String, Object>) changes.get(0).get("value");
            if (value == null)
                return ResponseEntity.ok("no value");

            // 3️⃣ Contactos y mensajes
            List<Map<String, Object>> contacts = (List<Map<String, Object>>) value.get("contacts");
            List<Map<String, Object>> messages = (List<Map<String, Object>>) value.get("messages");

            if (contacts == null || messages == null || contacts.isEmpty() || messages.isEmpty()) {
                return ResponseEntity.ok("no messages");
            }

            Map<String, Object> contact = contacts.get(0);
            Map<String, Object> message = messages.get(0);

            String telefono = (String) contact.get("wa_id");
            String tipoMensaje = (String) message.get("type");

            // ===========================================================
            // 🗣️ CASO 1: TEXTO
            // ===========================================================
            if ("text".equals(tipoMensaje)) {
                Map<String, Object> text = (Map<String, Object>) message.get("text");
                String texto = (text != null) ? (String) text.get("body") : "";

                WhatsappMessageDTO dto = new WhatsappMessageDTO();
                dto.setTelefono(telefono);
                dto.setTexto(texto);

                System.out.println("✅ Usando Conversation V2");
                FlowResult result = whatsappConversationService.procesarMensaje(dto);
                metaMediaService.sendWhatsappMessage(telefono, result.getRespuesta());

                return ResponseEntity.ok("EVENT_RECEIVED");
            }

            // ===========================================================
            // ❔ OTROS TIPOS
            // ===========================================================
            metaMediaService.sendWhatsappMessage(telefono,
                    "Por ahora solo puedo procesar mensajes de texto. 😊");

            return ResponseEntity.ok("unsupported");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok("EVENT_RECEIVED_WITH_ERROR");
        }
    }
}
