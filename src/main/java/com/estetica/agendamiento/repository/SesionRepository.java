package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Sesion;
import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.model.Equipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface SesionRepository extends JpaRepository<Sesion, Long> {

    @Query("""
                SELECT COUNT(s) > 0 FROM Sesion s
                WHERE s.clientePaquete.cliente.id = :clienteId
                AND s.fecha = :fecha
                AND s.horaInicio < :horaFin
                AND s.horaFin > :horaInicio
                AND s.estado IN (
                        com.estetica.agendamiento.model.Sesion.EstadoSesion.PENDIENTE,
                        com.estetica.agendamiento.model.Sesion.EstadoSesion.USADA
                )
            """)
    boolean existsActivaByClienteAndRango(@Param("clienteId") Long clienteId,
            @Param("fecha") LocalDate fecha, @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin);

    @Query("""
                SELECT COUNT(s) > 0 FROM Sesion s
                WHERE s.personal = :personal
                  AND s.fecha = :fecha
                  AND (
                      (s.horaInicio < :horaFin AND s.horaFin > :horaInicio)
                  )
                  AND s.estado IN (
                      com.estetica.agendamiento.model.Sesion.EstadoSesion.PENDIENTE,
                      com.estetica.agendamiento.model.Sesion.EstadoSesion.USADA
                  )
            """)
    boolean existsActivaByPersonalAndRango(@Param("personal") Personal personal,
            @Param("fecha") LocalDate fecha, @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin);

    @Query("""
                SELECT COUNT(s) > 0 FROM Sesion s
                WHERE s.equipo = :equipo
                  AND s.fecha = :fecha
                  AND (
                      (s.horaInicio < :horaFin AND s.horaFin > :horaInicio)
                  )
                  AND s.estado IN (
                      com.estetica.agendamiento.model.Sesion.EstadoSesion.PENDIENTE,
                      com.estetica.agendamiento.model.Sesion.EstadoSesion.USADA
                  )
            """)
    boolean existsActivaByEquipoAndRango(@Param("equipo") Equipo equipo,
            @Param("fecha") LocalDate fecha, @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin);

    @Query("""
                SELECT s FROM Sesion s
                WHERE s.clientePaquete.cliente.id = :clienteId
                  AND s.tratamiento.id = :tratamientoId
                  AND s.fecha = :fecha
                  AND s.estado = 'PENDIENTE'
            """)
    Optional<Sesion> findSesionPorTratamientoYFecha(@Param("clienteId") Long clienteId,
            @Param("tratamientoId") Long tratamientoId,
            @Param("fecha") LocalDate fecha);

    @Query("""
                SELECT s FROM Sesion s
                WHERE s.clientePaquete.cliente.id = :clienteId
                  AND s.fecha = :fecha
                  AND s.horaInicio = :hora
                  AND s.estado = 'PENDIENTE'
            """)
    Optional<Sesion> findSesionPorFechaYHora(@Param("clienteId") Long clienteId,
            @Param("fecha") LocalDate fecha, @Param("hora") LocalTime hora);

    boolean existsByClientePaquete_Id(Long clientePaqueteId);

    @Query("""
            SELECT s FROM Sesion s
            WHERE s.fecha = :fecha
              AND s.horaInicio < :horaFin
              AND s.horaFin > :horaInicio
            """)
    List<Sesion> findSesionesEnHorario(LocalDate fecha, LocalTime horaInicio,
            LocalTime horaFin);

    @Modifying
    @Query("UPDATE Sesion s SET s.estado = 'PERDIDA' WHERE s.estado = 'PENDIENTE' AND s.fecha = :hoy")
    int marcarComoPerdidasSiVencidas(@Param("hoy") LocalDate hoy);

    @Query("""
                SELECT s FROM Sesion s
                WHERE s.clientePaquete.cliente.id = :clienteId
                  AND s.fecha = :fecha
                  AND s.estado = com.estetica.agendamiento.model.Sesion.EstadoSesion.PENDIENTE
            """)
    List<Sesion> findByClienteIdAndFecha(@Param("clienteId") Long clienteId,
            @Param("fecha") LocalDate fecha);

    @Query("""
                SELECT s
                FROM Sesion s
                JOIN s.clientePaquete cp
                WHERE cp.cliente.id = :clienteId
                AND s.estado = 'USADA'
                ORDER BY s.fecha DESC, s.horaInicio DESC
            """)
    List<Sesion> findUltimasSesionesByClienteId(@Param("clienteId") Long clienteId);

    @Query("""
                SELECT s
                FROM Sesion s
                JOIN s.clientePaquete cp
                WHERE cp.cliente.id = :clienteId
                  AND s.estado = 'PENDIENTE'
                ORDER BY s.id DESC
            """)
    List<Sesion> findUltimasSesionesPendientesByClienteId(@Param("clienteId") Long clienteId);

    @Query("""
                SELECT s
                FROM Sesion s
                JOIN s.clientePaquete cp
                WHERE cp.cliente.id = :clienteId
                  AND s.estado = 'PENDIENTE'
                  AND s.fecha >= :hoy
                ORDER BY s.fecha ASC, s.horaInicio ASC
            """)
    List<Sesion> findSesionProximaPendientesByClienteId(
            @Param("clienteId") Long clienteId,
            @Param("hoy") LocalDate hoy);
}
