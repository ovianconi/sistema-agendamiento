package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Tratamiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TratamientoRepository extends JpaRepository<Tratamiento, Long> {
    Optional<Tratamiento> findByNombre(String nombre);
}
