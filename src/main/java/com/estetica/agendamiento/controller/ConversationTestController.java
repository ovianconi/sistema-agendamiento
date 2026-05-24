package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.ai.ConversationAiResult;
import com.estetica.agendamiento.ai.ConversationLlmClient;
import com.estetica.agendamiento.model.ChatConversationState;
import com.estetica.agendamiento.model.ChatMessage;
import com.estetica.agendamiento.service.ChatContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.estetica.agendamiento.service.FlowResult;
import com.estetica.agendamiento.service.FlowService;
import com.estetica.agendamiento.dto.WhatsappMessageDTO;
import com.estetica.agendamiento.service.WhatsappConversationService;

import java.util.List;

@RestController
@RequestMapping("/api/test/conversation")
@RequiredArgsConstructor
public class ConversationTestController {

        private final ChatContextService chatContextService;
        private final ConversationLlmClient conversationLlmClient;
        private final WhatsappConversationService whatsappConversationService;

        private final FlowService flowService;

        @PostMapping("/analyze")
        public ConversationAiResult analyze(@RequestBody TestConversationRequest request) {

                ChatConversationState estado = chatContextService.obtenerOCrearEstado(request.telefono());

                List<ChatMessage> historial = chatContextService.obtenerContextoReciente(request.telefono());

                return conversationLlmClient.analizar(
                                request.mensaje(),
                                estado,
                                historial);
        }

        @PostMapping("/flow")
        public FlowResult flow(@RequestBody TestConversationRequest request) {

                ChatConversationState estado = chatContextService.obtenerOCrearEstado(request.telefono());

                List<ChatMessage> historial = chatContextService.obtenerContextoReciente(request.telefono());

                ConversationAiResult ai = conversationLlmClient.analizar(
                                request.mensaje(),
                                estado,
                                historial);

                return flowService.manejar(
                                request.telefono(),
                                request.mensaje(),
                                estado,
                                ai);
        }

        @PostMapping("/process")
        public FlowResult process(@RequestBody TestConversationRequest request) {

                WhatsappMessageDTO dto = new WhatsappMessageDTO();

                dto.setTelefono(request.telefono());
                dto.setTexto(request.mensaje());

                return whatsappConversationService.procesarMensaje(dto);
        }

        public record TestConversationRequest(
                        String telefono,
                        String mensaje) {
        }
}