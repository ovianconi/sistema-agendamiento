package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.model.Tratamiento;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonalRepository extends JpaRepository<Personal, Long> {

    // Buscar personal que pueda realizar un tratamiento
    List<Personal> findByTratamientosContains(Tratamiento tratamiento);

    // Buscar personal disponible para un tratamiento en un rango de fecha y hora
    @Query("""
                SELECT p FROM Personal p
                JOIN p.tratamientos t
                WHERE t.id = :tratamientoId
                AND p.id NOT IN (
                    SELECT s.personal.id FROM Sesion s
                    WHERE s.fecha = :fecha
                    AND s.horaInicio < :horaFin
                    AND s.horaFin > :horaInicio
                )
            """)
    List<Personal> findPersonalDisponibleParaTratamiento(Long tratamientoId, LocalDate fecha,
            LocalTime horaInicio, LocalTime horaFin);
}
