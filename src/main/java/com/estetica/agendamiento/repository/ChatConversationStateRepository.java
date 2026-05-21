package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.ChatConversationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatConversationStateRepository extends JpaRepository<ChatConversationState, Long> {

    Optional<ChatConversationState> findByTelefono(String telefono);
}