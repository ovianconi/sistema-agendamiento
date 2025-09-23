package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SesionRepository extends JpaRepository<Sesion, Long> {
    List<Sesion> findByClienteId(Long clienteId);
}
