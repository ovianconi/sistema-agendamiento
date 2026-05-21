package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.ChatConversationState;
import com.estetica.agendamiento.model.ChatMessage;
import com.estetica.agendamiento.model.Cliente;
import com.estetica.agendamiento.repository.ChatConversationStateRepository;
import com.estetica.agendamiento.repository.ChatMessageRepository;
import com.estetica.agendamiento.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatContextService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatConversationStateRepository stateRepository;
    private final ClienteRepository clienteRepository;

    @Transactional
    public void guardarMensajeUsuario(String telefono, String texto, Long clienteId) {
        guardarMensaje(telefono, texto, "user", clienteId);
    }

    @Transactional
    public void guardarMensajeAsistente(String telefono, String texto, Long clienteId) {
        guardarMensaje(telefono, texto, "assistant", clienteId);
    }

    private void guardarMensaje(String telefono, String texto, String role, Long clienteId) {
        Cliente cliente = null;

        if (clienteId != null) {
            cliente = clienteRepository.findById(clienteId).orElse(null);
        }

        ChatMessage message = ChatMessage.builder()
                .telefono(telefono)
                .cliente(cliente)
                .role(role)
                .content(texto)
                .messageType("text")
                .createdAt(LocalDateTime.now())
                .build();

        chatMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> obtenerContextoReciente(String telefono) {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();

        List<ChatMessage> mensajesDelDia =
                chatMessageRepository.findByTelefonoAndCreatedAtAfterOrderByCreatedAtAsc(
                        telefono,
                        inicioDia
                );

        if (mensajesDelDia.size() <= 12) {
            return mensajesDelDia;
        }

        return chatMessageRepository.findTop12ByTelefonoOrderByCreatedAtDesc(telefono)
                .stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt))
                .toList();
    }

    @Transactional
    public ChatConversationState obtenerOCrearEstado(String telefono) {
        return stateRepository.findByTelefono(telefono)
                .orElseGet(() -> stateRepository.save(
                        ChatConversationState.builder()
                                .telefono(telefono)
                                .esperandoConfirmacion(false)
                                .updatedAt(LocalDateTime.now())
                                .build()
                ));
    }

    @Transactional
    public ChatConversationState guardarEstado(ChatConversationState state) {
        state.setUpdatedAt(LocalDateTime.now());
        return stateRepository.save(state);
    }

    @Transactional
    public void vincularClienteSiHaceFalta(String telefono, Long clienteId) {
        if (clienteId == null) return;

        ChatConversationState state = obtenerOCrearEstado(telefono);

        if (state.getCliente() == null) {
            clienteRepository.findById(clienteId).ifPresent(state::setCliente);
            guardarEstado(state);
        }
    }
}