package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.ClientePaquete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ClientePaqueteRepository extends JpaRepository<ClientePaquete, Long> {
    List<ClientePaquete> findByClienteId(Long clienteId);

    @Query("""
                SELECT cp
                FROM ClientePaquete cp
                JOIN cp.tratamientos t
                WHERE cp.cliente.id = :clienteId
                AND t.tratamiento.id = :tratamientoId
                AND cp.fechaValidez >= CURRENT_DATE
                AND t.sesionesRestantes > 0
                ORDER BY cp.fechaValidez ASC
            """)
    List<ClientePaquete> findByClienteIdAndTratamientoId(@Param("clienteId") Long clienteId,
            @Param("tratamientoId") Long tratamientoId);
}
