package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Paquete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaqueteRepository extends JpaRepository<Paquete, Long> {
    Optional<Paquete> findByNombre(String nombre);
}
