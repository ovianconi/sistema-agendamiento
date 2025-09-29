package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.RolVista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface RolVistaRepository extends JpaRepository<RolVista, Long> {
    List<RolVista> findByRol(String rol);

    List<RolVista> findByVistaId(Long vistaId);

    void deleteByRol(String nombre);

    @Query("SELECT rv FROM RolVista rv JOIN rv.vista v " + "JOIN Rol r ON rv.rol = r.nombre "
            + "WHERE r.id = :rolId")
    List<RolVista> findByRolId(@Param("rolId") Long rolId);

    @Modifying
    @Transactional
    @Query("DELETE FROM RolVista rv WHERE rv.rol = :rol AND rv.vista.id IN :vistaIds")
    void deleteByRolAndVistaIds(@Param("rol") String rol,
            @Param("vistaIds") Collection<Long> vistaIds);

}
