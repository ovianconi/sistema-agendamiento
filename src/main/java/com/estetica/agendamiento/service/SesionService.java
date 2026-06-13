package com.estetica.agendamiento.service;

import com.estetica.agendamiento.dto.SesionRequestDTO;
import com.estetica.agendamiento.dto.SesionResponseDTO;
import com.estetica.agendamiento.model.*;
import com.estetica.agendamiento.repository.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SesionService {

        private final SesionRepository sesionRepository;
        private final ClienteRepository clienteRepository;
        private final TratamientoRepository tratamientoRepository;
        private final ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository;
        private final PersonalRepository personalRepository;
        private final EquipoRepository equipoRepository;
        private final ClientePaqueteRepository clientePaqueteRepository;
        private final ParametroSistemaService parametroSistemaService;
        private static final java.time.ZoneId ZONE_PY = java.time.ZoneId.of("America/Asuncion");

        public SesionService(SesionRepository sesionRepository, ClienteRepository clienteRepository,
                        TratamientoRepository tratamientoRepository,
                        ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository,
                        PersonalRepository personalRepository, EquipoRepository equipoRepository,
                        ClientePaqueteRepository clientePaqueteRepository,
                        ParametroSistemaService parametroSistemaService) {
                this.sesionRepository = sesionRepository;
                this.clienteRepository = clienteRepository;
                this.tratamientoRepository = tratamientoRepository;
                this.clientePaqueteTratamientoRepository = clientePaqueteTratamientoRepository;
                this.personalRepository = personalRepository;
                this.equipoRepository = equipoRepository;
                this.clientePaqueteRepository = clientePaqueteRepository;
                this.parametroSistemaService = parametroSistemaService;
        }

        // =========================
        // CREAR SESIÓN (con params) carga el calendario
        // =========================
        @Transactional
        public Sesion crearSesion(Long clienteId, Long tratamientoId, LocalDate fecha,
                        LocalTime horaInicio, int duracionMinutos) {

                validarFechaHoraNoPasada(fecha, horaInicio);

                Tratamiento tratamiento = tratamientoRepository.findById(tratamientoId).orElseThrow(
                                () -> new RuntimeException("Tratamiento no encontrado"));

                LocalTime horaFin = validarBaseYConflictosCliente(clienteId, fecha, horaInicio,
                                duracionMinutos);

                // Elegir paquete + descontar saldo
                ClientePaqueteTratamiento cpt = seleccionarCPTConSaldoVigente(clienteId, tratamientoId);

                // Auto-asignar recursos obligatorios
                Personal personal = elegirPersonalDisponibleObligatorio(tratamientoId, fecha,
                                horaInicio, horaFin);
                Equipo equipo = elegirEquipoSiRequiere(tratamiento, fecha, horaInicio, horaFin);

                // Crear sesión
                Sesion sesion = new Sesion();
                sesion.setClientePaquete(cpt.getClientePaquete());
                sesion.setTratamiento(tratamiento);
                sesion.setFecha(fecha);
                sesion.setHoraInicio(horaInicio);
                sesion.setHoraFin(horaFin);
                sesion.setEstado(Sesion.EstadoSesion.PENDIENTE);
                sesion.setPersonal(personal);
                sesion.setEquipo(equipo);

                return sesionRepository.save(sesion);
        }

        // ======================================
        // CREAR SESIÓN (Desde frontend / Desde WhatsApp)
        // ======================================
        @Transactional
        public Sesion crearSesionDesdeDTO(SesionRequestDTO dto) {

                Cliente cliente = clienteRepository.findById(dto.getClienteId())
                                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

                Tratamiento tratamiento = tratamientoRepository.findById(dto.getTratamientoId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Tratamiento no encontrado"));

                validarFechaHoraNoPasada(dto.getFecha(), dto.getHoraInicio());

                int tiempoSesion = parametroSistemaService
                                .getParametroEntero("DURACION_SESION_MINUTOS", 60);

                LocalTime horaEntrada = parametroSistemaService.getParametroHora("HORA_ENTRADA", LocalTime.of(8, 0));
                LocalTime horaSalida = parametroSistemaService.getParametroHora("HORA_SALIDA", LocalTime.of(20, 0));

                // Validaciones de hora
                if (dto.getHoraFin() == null) {
                        dto.setHoraFin(dto.getHoraInicio().plusMinutes(tiempoSesion));
                }
                if ((dto.getHoraFin().isAfter(horaSalida)) || (horaEntrada.isAfter(dto.getHoraInicio()))) {
                        throw new RuntimeException("Hora fuera del rango. De " + horaEntrada.toString() + " a "
                                        + horaSalida.toString());
                }
                if (!dto.getHoraFin().isAfter(dto.getHoraInicio())) {
                        throw new RuntimeException("Hora fin debe ser posterior a hora inicio");
                }

                // Conflicto del cliente (otra sesión en el mismo rango)
                if (sesionRepository.existsActivaByClienteAndRango(cliente.getId(), dto.getFecha(),
                                dto.getHoraInicio(), dto.getHoraFin())) {
                        System.out.println("📚 Entro en cliente: ");
                        throw new RuntimeException("Ya tienes otra sesión en ese rango"); // **Prueba**
                }

                // Elegir paquete + descontar saldo (aceptando fechaValidez NULL como vigente)
                ClientePaqueteTratamiento cpt = seleccionarCPTConSaldoVigente(cliente.getId(), tratamiento.getId());

                // RESERVA de recursos (obligatoria)
                // 1) Personal obligatorio siempre
                Personal personalAsignado = resolverPersonal(dto.getPersonalId(),
                                tratamiento.getId(), dto.getFecha(), dto.getHoraInicio(),
                                dto.getHoraFin());

                // 2) Equipo si el tratamiento lo requiere
                Equipo equipoAsignado = resolverEquipoSiRequiere(tratamiento, dto.getEquipoId(),
                                dto.getFecha(), dto.getHoraInicio(), dto.getHoraFin());

                // Revalidación anti-carrera justo antes de guardar
                if (sesionRepository.existsActivaByPersonalAndRango(personalAsignado,
                                dto.getFecha(), dto.getHoraInicio(), dto.getHoraFin())) {
                        throw new RuntimeException(
                                        "El personal ya quedó ocupado en ese instante. Probá otro horario 🙏");
                }
                if (equipoAsignado != null && sesionRepository.existsActivaByEquipoAndRango(
                                equipoAsignado, dto.getFecha(), dto.getHoraInicio(),
                                dto.getHoraFin())) {
                        throw new RuntimeException(
                                        "El equipo ya quedó ocupado en ese instante. Probá otro horario 🙏");
                }

                // Crear sesión
                Sesion sesion = new Sesion();
                sesion.setClientePaquete(cpt.getClientePaquete());
                sesion.setTratamiento(tratamiento);
                sesion.setFecha(dto.getFecha());
                sesion.setHoraInicio(dto.getHoraInicio());
                sesion.setHoraFin(dto.getHoraFin());
                sesion.setEstado(Sesion.EstadoSesion.PENDIENTE);
                sesion.setPersonal(personalAsignado);
                sesion.setEquipo(equipoAsignado);

                return sesionRepository.save(sesion);
        }

        // ======================
        // LISTAR TODAS LAS SESIONES
        // ======================
        public List<Sesion> listarSesiones() {
                return sesionRepository.findAll();
        }

        // ======================
        // CANCELAR SESIÓN POR ID
        // ======================
        @Transactional
        public Sesion cancelarSesion(Long id) {
                Sesion sesion = sesionRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Sesión no encontrada"));

                int horasMinimas = parametroSistemaService
                                .getParametroEntero("TIEMPO_MINIMO_CANCELACION_HORAS", 2);

                if (sesion.getEstado() == Sesion.EstadoSesion.CANCELADA) {
                        return sesion; // idempotente
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
                LocalDateTime inicioSesion = LocalDateTime.of(sesion.getFecha(), sesion.getHoraInicio());

                if (ahora.isAfter(inicioSesion.minusHours(horasMinimas))) {
                        throw new RuntimeException(String.format(
                                        "La sesión solo puede cancelarse hasta %d horas antes de la hora programada",
                                        horasMinimas));
                }

                // Restituir 1 sesión al paquete correspondiente
                Long clientePaqueteId = sesion.getClientePaquete().getId();
                Long tratamientoId = sesion.getTratamiento().getId();

                ClientePaqueteTratamiento cpt = clientePaqueteTratamientoRepository
                                .findByClientePaqueteIdAndTratamientoId(clientePaqueteId,
                                                tratamientoId)
                                .orElseThrow(() -> new RuntimeException(
                                                "No se encontró el paquete-tratamiento vinculado"));

                cpt.setSesionesRestantes(cpt.getSesionesRestantes() + 1);
                clientePaqueteTratamientoRepository.save(cpt);

                sesion.setEstado(Sesion.EstadoSesion.CANCELADA);
                return sesionRepository.save(sesion);
        }

        // ======================
        // CANCELAR POR TRATAMIENTO + FECHA
        // ======================
        @Transactional
        public Sesion cancelarSesionPorTratamiento(Long clienteId, Long tratamientoId,
                        LocalDate fecha) {
                Sesion sesion = sesionRepository
                                .findSesionPorTratamientoYFecha(clienteId, tratamientoId, fecha)
                                .orElseThrow(() -> new RuntimeException(
                                                "No se encontró sesión pendiente en esa fecha para ese tratamiento"));
                return cancelarSesion(sesion.getId());
        }

        // ======================
        // CANCELAR POR FECHA + HORA
        // ======================
        @Transactional
        public Sesion cancelarSesionPorFechaHora(Long clienteId, LocalDate fecha, LocalTime hora) {
                Sesion sesion = sesionRepository.findSesionPorFechaYHora(clienteId, fecha, hora)
                                .orElseThrow(() -> new RuntimeException(
                                                "No se encontró sesión pendiente en esa fecha y hora"));
                return cancelarSesion(sesion.getId());
        }

        // ======================
        // MARCAR COMO USADA
        // ======================
        @Transactional
        public Sesion marcarComoUsada(Long id) {
                Sesion sesion = sesionRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Sesión no encontrada"));

                if (sesion.getEstado() != Sesion.EstadoSesion.PENDIENTE) {
                        throw new RuntimeException(
                                        "Solo se pueden marcar como usadas las sesiones pendientes");
                }

                ClientePaquete cp = sesion.getClientePaquete();

                // Si el paquete nunca fue usado: setear fechaInicio y fechaValidez usando
                // duración
                // (en meses)
                if (cp.getFechaInicio() == null) {
                        cp.setFechaInicio(sesion.getFecha());
                        Integer meses = Optional.ofNullable(cp.getPaquete().getDuracion())
                                        .orElse(0);
                        if (meses > 0) {
                                cp.setFechaValidez(cp.getFechaInicio().plusMonths(meses));
                        }
                }

                ClientePaqueteTratamiento cpt = clientePaqueteTratamientoRepository
                                .findByClientePaqueteIdAndTratamientoId(cp.getId(),
                                                sesion.getTratamiento().getId())
                                .orElseThrow(() -> new RuntimeException(
                                                "No se encontró paquete-tratamiento vinculado"));

                cpt.setSesionesUsadas(cpt.getSesionesUsadas() + 1);
                clientePaqueteTratamientoRepository.save(cpt);

                clientePaqueteRepository.save(cp); // persistir fechas

                sesion.setEstado(Sesion.EstadoSesion.USADA);
                return sesionRepository.save(sesion);
        }

        // ======================
        // Helpers de negocio
        // ======================

        private void validarFechaHoraNoPasada(LocalDate fecha, LocalTime hora) {
                var ahora = ZonedDateTime.now(ZONE_PY);
                var fechaHora = ZonedDateTime.of(fecha, hora, ZONE_PY);
                if (fechaHora.isBefore(ahora)) {
                        throw new RuntimeException(
                                        "No se puede agendar en una fecha/horario pasado");
                }
        }

        private LocalTime validarBaseYConflictosCliente(Long clienteId, LocalDate fecha,
                        LocalTime horaInicio, int duracionMinutos) {
                System.out.println("📚 Entro en conflicto yo: ");
                if (duracionMinutos <= 0) {
                        throw new RuntimeException("Duración inválida");
                }
                LocalTime horaFin = horaInicio.plusMinutes(duracionMinutos);

                if (sesionRepository.existsActivaByClienteAndRango(clienteId, fecha, horaInicio,
                                horaFin)) {
                        throw new RuntimeException("Alguien ya tiene otra sesión en ese rango"); // **Prueba**
                }
                return horaFin;
        }

        private ClientePaqueteTratamiento seleccionarCPTConSaldoVigente(Long clienteId,
                        Long tratamientoId) {
                // Traer todos los CPT vigentes (fechaValidez NULL o >= hoy) con saldo
                List<ClientePaqueteTratamiento> cpts = clientePaqueteTratamientoRepository
                                .encontrarVigentesConSaldo(clienteId, LocalDate.now());

                if (cpts.isEmpty()) {
                        throw new RuntimeException("No tienes paquetes vigentes con saldo");
                }

                // Filtrar por tratamiento solicitado y priorizar el que vence antes (NULLs al
                // final)
                return cpts.stream().filter(c -> c.getTratamiento().getId().equals(tratamientoId))
                                .sorted(Comparator.comparing(c -> Optional
                                                .ofNullable(c.getClientePaquete().getFechaValidez())
                                                .orElse(LocalDate.MAX)))
                                .findFirst().map(cpt -> {
                                        if (cpt.getSesionesRestantes() <= 0) {
                                                throw new RuntimeException(
                                                                "El paquete no tiene sesiones restantes para este tratamiento");
                                        }
                                        // Reservar 1 sesión
                                        cpt.setSesionesRestantes(cpt.getSesionesRestantes() - 1);
                                        clientePaqueteTratamientoRepository.save(cpt);
                                        return cpt;
                                }).orElseThrow(() -> new RuntimeException(
                                                "No tienes sesiones restantes para este tratamiento"));
        }

        private Personal resolverPersonal(Long personalId, Long tratamientoId, LocalDate fecha,
                        LocalTime horaInicio, LocalTime horaFin) {
                System.out.println("resolverPersonal() IN  → fecha=" + fecha + " hIni=" + horaInicio
                                + " hFin=" + horaFin);
                System.out.println("El personal es:" + personalId);
                System.out.printf("   [DEBUG SQL] fecha=%s, horaInicio=%s, horaFin=%s%n", fecha,
                                horaInicio, horaFin);
                List<Sesion> existentes = sesionRepository.findAll();
                existentes.stream().filter(s -> s.getFecha().equals(fecha)).forEach(s -> System.out
                                .printf("   [EN BD] s.id=%d personal=%d hIni=%s hFin=%s estado=%s%n",
                                                s.getId(),
                                                s.getPersonal() != null ? s.getPersonal().getId()
                                                                : null,
                                                s.getHoraInicio(), s.getHoraFin(), s.getEstado()));
                // 1) Si vino un personal específico
                if (personalId != null) {
                        System.out.println("→ Personal explícito: " + personalId);

                        Personal p = personalRepository.findById(personalId).orElseThrow(
                                        () -> new RuntimeException("Personal no encontrado"));

                        // Habilitación para el tratamiento
                        boolean habilitado = personalRepository
                                        .existsByIdAndTratamientos_Id(p.getId(), tratamientoId);
                        System.out.println("   · habilitado p/ tratamiento? " + habilitado);
                        if (!habilitado) {
                                throw new RuntimeException(
                                                "El personal seleccionado no está habilitado para este tratamiento");
                        }

                        // Disponibilidad (¡orden correcto!: horaInicio, horaFin)
                        System.out.printf("   · verificando disponibilidad p=%s entre %s-%s%n",
                                        p.getId(), horaInicio, horaFin);
                        boolean ocupado = sesionRepository.existsActivaByPersonalAndRango(p, fecha,
                                        horaInicio, horaFin);
                        System.out.println("   · ocupado1p? " + ocupado);
                        if (ocupado) {
                                throw new RuntimeException(
                                                "El personal seleccionado no está disponible en ese horario");
                        }

                        System.out.println("resolverPersonal() OUT → asignado1 p=" + p.getId());
                        return p;
                }

                // 2) Autoasignación
                System.out.println(
                                "→ Autoasignación de personal para tratamiento=" + tratamientoId);
                List<Personal> candidatos = personalRepository.findByTratamientos_Id(tratamientoId);
                System.out.println("   · candidatos habilitados: "
                                + (candidatos == null ? 0 : candidatos.size()));

                if (candidatos == null || candidatos.isEmpty()) {
                        throw new RuntimeException(
                                        "No hay personal habilitado para este tratamiento");
                }

                for (Personal p : candidatos) {
                        Long pid = (p != null ? p.getId() : null);
                        System.out.printf("   · candidato p=%s, verificando %s %s-%s%n", pid, fecha,
                                        horaInicio, horaFin);

                        boolean ocupado = sesionRepository.existsActivaByPersonalAndRango(p, fecha,
                                        horaInicio, horaFin);
                        System.out.println("     · ocupado2p? " + ocupado);

                        if (!ocupado) {
                                System.out.println("resolverPersonal() OUT → asignado2 p=" + pid);
                                return p;
                        }
                }

                throw new RuntimeException(
                                "No hay personal disponible para este tratamiento en ese horario");
        }

        private Personal elegirPersonalDisponibleObligatorio(Long tratamientoId, LocalDate fecha,
                        LocalTime horaInicio, LocalTime horaFin) {
                return resolverPersonal(null, tratamientoId, fecha, horaInicio, horaFin);
        }

        private Equipo resolverEquipoSiRequiere(Tratamiento tratamiento, Long equipoId,
                        LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
                boolean requiere = false;
                try {
                        requiere = Boolean.TRUE.equals(tratamiento.isRequiereEquipo());
                } catch (Exception ignore) {
                        try {
                                requiere = tratamiento.isRequiereEquipo();
                        } catch (Exception ignored) {
                                /* noop */ }
                }

                System.out.println("resolverEquipoSiRequiere() IN → requiere=" + requiere
                                + " fecha=" + fecha + " hIni=" + horaInicio + " hFin=" + horaFin);

                if (!requiere) {
                        System.out.println("   · Tratamiento no requiere equipo → null");
                        return null;
                }
                System.out.println("El equipo es:" + equipoId);
                // 1) Si vino equipo explícito
                if (equipoId != null) {
                        System.out.println("→ Equipo explícito: " + equipoId);

                        Equipo eq = equipoRepository.findById(equipoId).orElseThrow(
                                        () -> new RuntimeException("Equipo no encontrado"));

                        boolean habilitado = equipoRepository.existsByIdAndTratamientos_Id(
                                        eq.getId(), tratamiento.getId());
                        System.out.println("   · habilitado p/ tratamiento? " + habilitado);
                        if (!habilitado) {
                                throw new RuntimeException(
                                                "El equipo seleccionado no está habilitado para este tratamiento");
                        }

                        System.out.printf("   · verificando disponibilidad eq=%s entre %s-%s%n",
                                        eq.getId(), horaInicio, horaFin);
                        boolean ocupado = sesionRepository.existsActivaByEquipoAndRango(eq, fecha,
                                        horaInicio, horaFin);
                        System.out.println("   · ocupado1e? " + ocupado);
                        if (ocupado) {
                                throw new RuntimeException(
                                                "El equipo seleccionado no está disponible en ese horario");
                        }

                        System.out.println("resolverEquipoSiRequiere() OUT → asignado1 eq="
                                        + eq.getId());
                        return eq;
                }

                // 2) Autoasignación de equipo
                System.out.println("→ Autoasignación de equipo para tratamiento="
                                + tratamiento.getId());
                List<Equipo> equipos = equipoRepository.findByTratamientos_Id(tratamiento.getId());
                System.out.println("   · equipos habilitados: "
                                + (equipos == null ? 0 : equipos.size()));

                if (equipos == null || equipos.isEmpty()) {
                        throw new RuntimeException(
                                        "No hay equipos habilitados para este tratamiento");
                }

                for (Equipo eq : equipos) {
                        Long eid = (eq != null ? eq.getId() : null);
                        System.out.printf("   · candidato eq=%s, verificando %s %s-%s%n", eid,
                                        fecha, horaInicio, horaFin);

                        boolean ocupado = sesionRepository.existsActivaByEquipoAndRango(eq, fecha,
                                        horaInicio, horaFin);
                        System.out.println("     · ocupado2e? " + ocupado);

                        if (!ocupado) {
                                System.out.println("resolverEquipoSiRequiere() OUT2 → asignado eq="
                                                + eid);
                                return eq;
                        }
                }

                throw new RuntimeException(
                                "No hay equipos disponibles para este tratamiento en ese horario");
        }

        private Equipo elegirEquipoSiRequiere(Tratamiento tratamiento, LocalDate fecha,
                        LocalTime horaInicio, LocalTime horaFin) {
                return resolverEquipoSiRequiere(tratamiento, null, fecha, horaInicio, horaFin);
        }

        @Transactional
        public void marcarSesionesPendientesComoPerdidas() {
                int actualizadas = sesionRepository.marcarComoPerdidasSiVencidas(LocalDate.now());
                System.out.println(
                                "🕓 Sesiones pendientes marcadas como perdidas: " + actualizadas);
        }

        // =======================================================
        // Buscar sesiones por cliente y fecha exacta
        // =======================================================
        public List<SesionResponseDTO> findByClienteAndFecha(Long clienteId, LocalDate fecha) {
                var sesiones = sesionRepository.findByClienteIdAndFecha(clienteId, fecha);
                return sesiones.stream().map(SesionResponseDTO::fromEntity)
                                .collect(Collectors.toList());
        }

        public Sesion obtenerUltimaSesionDelCliente(Long clienteId) {
                return sesionRepository.findUltimasSesionesByClienteId(clienteId)
                                .stream()
                                .findFirst()
                                .orElse(null);
        }
}
