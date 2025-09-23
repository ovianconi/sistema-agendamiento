package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Equipo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EquipoRepository extends JpaRepository<Equipo, Long> {
    Optional<Equipo> findByCodigo(String codigo);

    Optional<Equipo> findByNombre(String nombre);
}
