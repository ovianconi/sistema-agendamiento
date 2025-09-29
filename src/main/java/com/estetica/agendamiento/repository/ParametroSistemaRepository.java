package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.ParametroSistema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParametroSistemaRepository extends JpaRepository<ParametroSistema, Long> {
    Optional<ParametroSistema> findByClave(String clave);
}
