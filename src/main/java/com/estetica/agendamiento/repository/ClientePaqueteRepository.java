package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.ClientePaquete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientePaqueteRepository extends JpaRepository<ClientePaquete, Long> {
    List<ClientePaquete> findByClienteId(Long clienteId);
}
