package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Equipo;
import com.estetica.agendamiento.model.Tratamiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface EquipoRepository extends JpaRepository<Equipo, Long> {

    // Buscar todos los equipos que pueden realizar un tratamiento
    List<Equipo> findByTratamientosContains(Tratamiento tratamiento);

    // Buscar equipos disponibles para un tratamiento en un rango de fecha y hora
    @Query("""
                SELECT e FROM Equipo e
                JOIN e.tratamientos t
                WHERE t.id = :tratamientoId
                AND e.id NOT IN (
                    SELECT s.equipo.id FROM Sesion s
                    WHERE s.fecha = :fecha
                    AND s.horaInicio < :horaFin
                    AND s.horaFin > :horaInicio
                    AND s.equipo IS NOT NULL
                )
            """)
    List<Equipo> findEquiposDisponiblesParaTratamiento(Long tratamientoId, LocalDate fecha,
            LocalTime horaInicio, LocalTime horaFin);

    Optional<Equipo> findByCodigo(String codigo);

    Optional<Equipo> findByNombre(String nombre);

    // Listar equipos que sirven para un tratamiento
    List<Equipo> findByTratamientos_Id(Long tratamientoId);

    // Validar si un equipo específico sirve para ese tratamiento
    boolean existsByIdAndTratamientos_Id(Long equipoId, Long tratamientoId);
}
