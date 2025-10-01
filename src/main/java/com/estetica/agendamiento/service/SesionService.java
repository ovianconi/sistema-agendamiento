package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.*;
import com.estetica.agendamiento.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SesionService {

        private final SesionRepository sesionRepository;
        private final ClientePaqueteRepository clientePaqueteRepository;
        private final EquipoRepository equipoRepository;
        private final PersonalRepository personalRepository;
        private final TratamientoRepository tratamientoRepository;
        private final ParametroSistemaService parametroSistemaService;

        // 🔧 Parametrizable: horas mínimas de anticipación para cancelar
        @Value("${sesiones.cancelacion.horas-anticipacion:2}")
        private int horasAnticipacionCancelacion;

        @Transactional
        public Sesion crearSesion(Long clienteId, Long tratamientoId, LocalDate fecha,
                        LocalTime horaInicio, int duracionMinutos) {

                // 1️⃣ Verificar que el cliente tenga un paquete con este tratamiento
                List<ClientePaquete> paquetes = clientePaqueteRepository
                                .findByClienteIdAndTratamientoId(clienteId, tratamientoId);

                if (paquetes.isEmpty()) {
                        throw new RuntimeException(
                                        "El cliente no tiene este tratamiento disponible");
                }

                // Escoger el paquete más próximo a vencer
                ClientePaquete cp = paquetes.stream()
                                .sorted(Comparator.comparing(ClientePaquete::getFechaValidez))
                                .findFirst().orElseThrow(() -> new RuntimeException(
                                                "Error al buscar paquete válido"));

                // Buscar el ClientePaqueteTratamiento correspondiente y restar sesión
                ClientePaqueteTratamiento cpt = cp.getTratamientos().stream()
                                .filter(t -> t.getTratamiento().getId().equals(tratamientoId))
                                .findFirst().orElseThrow(() -> new RuntimeException(
                                                "El cliente no tiene sesiones de este tratamiento"));

                if (cpt.getSesionesRestantes() <= 0) {
                        throw new RuntimeException(
                                        "No quedan sesiones disponibles de este tratamiento");
                }

                // 🔑 Restamos la sesión en el momento de agendar
                cpt.setSesionesRestantes(cpt.getSesionesRestantes() - 1);
                clientePaqueteRepository.save(cp);

                // 2️⃣ Validar que no haya otra sesión del mismo cliente en ese horario
                int duracion = parametroSistemaService.getParametroEntero("DURACION_SESION_MINUTOS",
                                60);
                LocalTime horaFin = horaInicio.plusMinutes(duracion);


                boolean existeOtraSesion = sesionRepository
                                .existsByClientePaquete_Cliente_IdAndFechaAndHoraInicioBetween(
                                                clienteId, fecha, horaInicio,
                                                horaFin.minusSeconds(1));

                if (existeOtraSesion) {
                        throw new RuntimeException(
                                        "El cliente ya tiene una sesión en este horario");
                }

                // 3️⃣ Obtener tratamiento y verificar si requiere equipo
                Tratamiento tratamiento = tratamientoRepository.findById(tratamientoId).orElseThrow(
                                () -> new RuntimeException("Tratamiento no encontrado"));

                Equipo equipoAsignado = null;
                if (tratamiento.isRequiereEquipo()) {
                        List<Equipo> equiposDisponibles =
                                        equipoRepository.findEquiposDisponiblesParaTratamiento(
                                                        tratamientoId, fecha, horaInicio, horaFin);

                        if (equiposDisponibles.isEmpty()) {
                                throw new RuntimeException(
                                                "No hay equipos disponibles para este tratamiento en este horario");
                        }
                        equipoAsignado = equiposDisponibles.get(0);
                }

                // 4️⃣ Verificar disponibilidad de personal
                List<Personal> personalDisponible =
                                personalRepository.findPersonalDisponibleParaTratamiento(
                                                tratamientoId, fecha, horaInicio, horaFin);

                if (personalDisponible.isEmpty()) {
                        throw new RuntimeException(
                                        "No hay personal disponible para este tratamiento en este horario");
                }

                Personal personalAsignado = personalDisponible.get(0);

                // 5️⃣ Crear y guardar la sesión
                Sesion sesion = new Sesion();
                sesion.setClientePaquete(cp);
                sesion.setTratamiento(tratamiento);
                sesion.setFecha(fecha);
                sesion.setHoraInicio(horaInicio);
                sesion.setHoraFin(horaFin);
                sesion.setEstado(Sesion.EstadoSesion.PENDIENTE);
                sesion.setPersonal(personalAsignado);
                sesion.setEquipo(equipoAsignado);

                return sesionRepository.save(sesion);
        }

        public List<Sesion> listarSesiones() {
                return sesionRepository.findAll();
        }

        /**
         * Cancela una sesión si todavía no ha pasado el tiempo límite de cancelación y vuelve a
         * sumar la sesión restada al crearla.
         */
        @Transactional
        public Sesion cancelarSesion(Long sesionId) {
                int horasMinimas = parametroSistemaService
                                .getParametroEntero("TIEMPO_MINIMO_CANCELACION_HORAS", 2);

                Sesion sesion = sesionRepository.findById(sesionId)
                                .orElseThrow(() -> new RuntimeException("Sesión no encontrada"));

                if (sesion.getEstado() == Sesion.EstadoSesion.CANCELADA) {
                        throw new RuntimeException("La sesión ya fue cancelada previamente");
                }
                if (sesion.getEstado() == Sesion.EstadoSesion.USADA) {
                        throw new RuntimeException(
                                        "No se puede cancelar una sesión que ya fue usada");
                }
                if (sesion.getEstado() == Sesion.EstadoSesion.PERDIDA) {
                        throw new RuntimeException(
                                        "No se puede cancelar una sesión marcada como perdida");
                }

                LocalDateTime ahora = LocalDateTime.now();
                LocalDateTime inicioSesion =
                                LocalDateTime.of(sesion.getFecha(), sesion.getHoraInicio());

                if (ahora.isAfter(inicioSesion.minusHours(horasMinimas))) {
                        throw new RuntimeException(String.format(
                                        "La sesión solo puede cancelarse hasta %d horas antes de la hora programada",
                                        horasMinimas));
                }

                sesion.setEstado(Sesion.EstadoSesion.CANCELADA);
                sesion.getClientePaquete().getTratamientos().stream()
                                .filter(t -> t.getTratamiento().equals(sesion.getTratamiento()))
                                .findFirst().ifPresent(ClientePaqueteTratamiento::devolverSesion);

                return sesionRepository.save(sesion);
        }

        @Transactional
        public Sesion marcarComoUsada(Long sesionId) {
                Sesion sesion = sesionRepository.findById(sesionId)
                                .orElseThrow(() -> new RuntimeException("Sesión no encontrada"));

                if (sesion.getEstado() == Sesion.EstadoSesion.CANCELADA) {
                        throw new RuntimeException("No se puede usar una sesión cancelada");
                }
                if (sesion.getEstado() == Sesion.EstadoSesion.USADA) {
                        throw new RuntimeException("La sesión ya fue marcada como usada");
                }

                if (sesion.getEstado() == Sesion.EstadoSesion.PERDIDA) {
                        throw new RuntimeException("La sesión ya fue marcada como perdida");
                }

                // 🔑 Cambiar estado
                sesion.setEstado(Sesion.EstadoSesion.USADA);

                // 🔑 Registrar el uso en ClientePaqueteTratamiento
                sesion.getClientePaquete().getTratamientos().stream()
                                .filter(t -> t.getTratamiento().equals(sesion.getTratamiento()))
                                .findFirst().ifPresent(ClientePaqueteTratamiento::marcarUsada);

                // 🔑 Si el paquete aún no tiene fechaInicio, inicializamos validez
                ClientePaquete cp = sesion.getClientePaquete();
                if (cp.getFechaInicio() == null) {
                        cp.setFechaInicio(LocalDate.now());
                        cp.setFechaValidez(cp.getFechaInicio()
                                        .plusMonths(cp.getPaquete().getDuracion()));
                }

                return sesionRepository.save(sesion);
        }

}
