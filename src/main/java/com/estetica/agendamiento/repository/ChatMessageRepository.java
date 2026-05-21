package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop12ByTelefonoOrderByCreatedAtDesc(String telefono);

    List<ChatMessage> findByTelefonoAndCreatedAtAfterOrderByCreatedAtAsc(
            String telefono,
            LocalDateTime desde
    );
}