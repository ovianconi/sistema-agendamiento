package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.ClientePaqueteTratamiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClientePaqueteTratamientoRepository
        extends JpaRepository<ClientePaqueteTratamiento, Long> {

    @Query("""
                SELECT cpt
                  FROM ClientePaqueteTratamiento cpt
                  JOIN cpt.clientePaquete cp
                 WHERE cp.cliente.id = :clienteId
                   AND (cp.fechaValidez IS NULL OR cp.fechaValidez >= :hoy)
                   AND cpt.sesionesRestantes > 0
            """)
    List<ClientePaqueteTratamiento> encontrarVigentesConSaldo(@Param("clienteId") Long clienteId,
            @Param("hoy") LocalDate hoy);

    @Query("""
                SELECT cpt
                  FROM ClientePaqueteTratamiento cpt
                 WHERE cpt.clientePaquete.id = :clientePaqueteId
                   AND cpt.tratamiento.id = :tratamientoId
            """)
    Optional<ClientePaqueteTratamiento> findByClientePaqueteIdAndTratamientoId(
            @Param("clientePaqueteId") Long clientePaqueteId,
            @Param("tratamientoId") Long tratamientoId);

    @Query("""
            SELECT cpt
            FROM ClientePaqueteTratamiento cpt
            JOIN cpt.clientePaquete cp
            WHERE cp.cliente.id = :clienteId
              AND cpt.tratamiento.id = :tratamientoId
              AND (
                   cp.fechaValidez IS NULL OR cp.fechaValidez >= :hoy
              )
            ORDER BY cpt.sesionesRestantes DESC
            """)
    Optional<ClientePaqueteTratamiento> findByClienteAndTratamientoConVigencia(
            @Param("clienteId") Long clienteId, @Param("tratamientoId") Long tratamientoId,
            @Param("hoy") LocalDate hoy);

    boolean existsByClientePaquete_Cliente_IdAndTratamiento_Id(Long clienteId, Long tratamientoId);

}
