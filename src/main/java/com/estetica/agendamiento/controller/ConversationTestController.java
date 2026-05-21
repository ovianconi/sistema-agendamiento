package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.ai.ConversationAiResult;
import com.estetica.agendamiento.ai.ConversationLlmClient;
import com.estetica.agendamiento.model.ChatConversationState;
import com.estetica.agendamiento.model.ChatMessage;
import com.estetica.agendamiento.service.ChatContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test/conversation")
@RequiredArgsConstructor
public class ConversationTestController {

        private final ChatContextService chatContextService;
        private final ConversationLlmClient conversationLlmClient;

        @PostMapping("/analyze")
        public ConversationAiResult analyze(@RequestBody TestConversationRequest request) {

                ChatConversationState estado = chatContextService.obtenerOCrearEstado(request.telefono());

                List<ChatMessage> historial = chatContextService.obtenerContextoReciente(request.telefono());

                return conversationLlmClient.analizar(
                                request.mensaje(),
                                estado,
                                historial);
        }

        public record TestConversationRequest(
                        String telefono,
                        String mensaje) {
        }
}