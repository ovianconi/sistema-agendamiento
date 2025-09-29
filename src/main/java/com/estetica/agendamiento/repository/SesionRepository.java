package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Sesion;
import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.model.Equipo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface SesionRepository extends JpaRepository<Sesion, Long> {

        @Query("""
                        SELECT s FROM Sesion s
                        WHERE s.fecha = :fecha
                          AND s.horaInicio < :horaFin
                          AND s.horaFin > :horaInicio
                        """)
        List<Sesion> findSesionesEnHorario(LocalDate fecha, LocalTime horaInicio,
                        LocalTime horaFin);

        boolean existsByPersonalAndFechaAndHoraInicioLessThanAndHoraFinGreaterThan(
                        Personal personal, LocalDate fecha, LocalTime horaFin,
                        LocalTime horaInicio);

        boolean existsByEquipoAndFechaAndHoraInicioLessThanAndHoraFinGreaterThan(Equipo equipo,
                        LocalDate fecha, LocalTime horaFin, LocalTime horaInicio);

        // Verificar si el cliente ya tiene otra sesión en el mismo rango horario
        boolean existsByClientePaquete_Cliente_IdAndFechaAndHoraInicioBetween(Long clienteId,
                        LocalDate fecha, LocalTime horaInicio, LocalTime horaFin);
}
// existsByClienteIdAndFechaAndHoraInicioBetween
