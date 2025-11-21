package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByTelefono(String telefono);

    @Query("""
            SELECT c
            FROM Cliente c
            WHERE
                LOWER(CONCAT(c.nombre, ' ', c.apellido)) LIKE LOWER(CONCAT('%', :nombre, '%'))
             OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :nombre, '%'))
             OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :nombre, '%'))
            """)
    List<Cliente> searchByNombreFlexible(@Param("nombre") String nombre);

    @Query("SELECT c FROM Cliente c WHERE c.documento = :documento")
    Optional<Cliente> findByDocumento(@Param("documento") String documento);

    @Query("""
                SELECT DISTINCT c
                FROM Cliente c
                WHERE EXISTS (
                    SELECT 1
                    FROM ClientePaquete cp
                    WHERE cp.cliente.id = c.id
                )
            """)
    List<Cliente> findClientesConPaquetes();
}
