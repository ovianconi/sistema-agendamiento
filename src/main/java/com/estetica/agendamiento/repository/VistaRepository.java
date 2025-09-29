package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Vista;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VistaRepository extends JpaRepository<Vista, Long> {

    boolean existsByNombre(String nombre);

    boolean existsByPath(String path);

    // 🔧 CORREGIDO: eliminamos el JOIN FETCH rp.rol porque 'rol' es un String, no entidad
    @Query("SELECT DISTINCT v FROM Vista v LEFT JOIN FETCH v.rolesPermitidos rp ORDER BY v.id ASC")
    List<Vista> findAllWithRoles();

    @EntityGraph(attributePaths = {"rolesPermitidos"})
    List<Vista> findAll();
}
