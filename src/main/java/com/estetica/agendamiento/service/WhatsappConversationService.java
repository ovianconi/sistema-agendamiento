package com.estetica.agendamiento.service;

import com.estetica.agendamiento.ai.ConversationAiResult;
import com.estetica.agendamiento.ai.ConversationLlmClient;
import com.estetica.agendamiento.dto.ClienteResponseDTO;
import com.estetica.agendamiento.dto.WhatsappMessageDTO;
import com.estetica.agendamiento.model.ChatConversationState;
import com.estetica.agendamiento.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.estetica.agendamiento.model.Cliente;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WhatsappConversationService {

    private final ChatContextService chatContextService;
    private final ConversationLlmClient conversationLlmClient;
    private final FlowService flowService;
    private final ClienteService clienteService;

    public FlowResult procesarMensaje(WhatsappMessageDTO msg) {
        String telefono = msg.getTelefono();
        String texto = msg.getTexto();

        ClienteResponseDTO cliente = identificarClientePorTelefono(telefono);

        Long clienteId = cliente != null ? cliente.getId() : null;

        chatContextService.guardarMensajeUsuario(telefono, texto, clienteId);

        ChatConversationState estado = chatContextService.obtenerOCrearEstado(telefono);

        if (cliente == null
                && estado.getCliente() == null
                && "documento".equalsIgnoreCase(estado.getEsperando())) {

            cliente = identificarClientePorDocumento(texto);

            if (cliente != null) {
                chatContextService.vincularClienteSiHaceFalta(telefono, cliente.getId());
                estado = chatContextService.obtenerOCrearEstado(telefono);
            }
        }

        List<ChatMessage> historial = chatContextService.obtenerContextoReciente(telefono);

        ConversationAiResult ai = conversationLlmClient.analizar(
                texto,
                estado,
                historial);

        FlowResult result = flowService.manejar(
                telefono,
                texto,
                estado,
                ai);

        Long clienteFinalId = estado.getCliente() != null ? estado.getCliente().getId() : clienteId;

        chatContextService.guardarMensajeAsistente(
                telefono,
                result.getRespuesta(),
                clienteFinalId);

        return result;
    }

    private ClienteResponseDTO identificarClientePorTelefono(String telefonoCrudo) {
        try {
            String normalizado = normalizePhone(telefonoCrudo);
            return clienteService.getClienteByTelefono(normalizado)
                    .map(ClienteResponseDTO::fromEntity)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private ClienteResponseDTO identificarClientePorDocumento(String texto) {
        try {
            if (texto == null)
                return null;

            String documento = texto.trim().replaceAll("\\D", "");

            if (documento.isBlank())
                return null;

            Cliente cliente = clienteService.findEntityByDocumento(documento);

            return ClienteResponseDTO.fromEntity(cliente);

        } catch (Exception e) {
            return null;
        }
    }

    private String normalizePhone(String telefono) {
        if (telefono == null)
            return null;

        String num = telefono.trim().replaceAll("\\s+", "");

        if (num.startsWith("+595")) {
            num = "0" + num.substring(4);
        } else if (num.startsWith("595")) {
            num = "0" + num.substring(3);
        } else if (!num.startsWith("0")) {
            num = "0" + num;
        }

        return num;
    }
}