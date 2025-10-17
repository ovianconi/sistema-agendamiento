package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Sesion;
import com.estetica.agendamiento.service.SesionService;
import com.estetica.agendamiento.dto.SesionCancelacionResponseDTO;
import com.estetica.agendamiento.dto.SesionRequestDTO;
import com.estetica.agendamiento.dto.SesionResponseDTO;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SesionController {

    private final SesionService sesionService;

    public SesionController(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @PostMapping

    public ResponseEntity<Sesion> crearSesion(@RequestParam Long clienteId,

            @RequestParam Long tratamientoId, @RequestParam LocalDate fecha,

            @RequestParam LocalTime horaInicio,

            @RequestParam(defaultValue = "60") int duracionMinutos) {
        return ResponseEntity.ok(sesionService.crearSesion(clienteId, tratamientoId, fecha,
                horaInicio, duracionMinutos));
    }


    @GetMapping("/sesiones")
    public ResponseEntity<List<Sesion>> listar() {
        return ResponseEntity.ok(sesionService.listarSesiones());
    }

    // Cancelar por ID (calendario actual)
    @PutMapping("/sesiones/{id}/cancelar")
    public ResponseEntity<SesionCancelacionResponseDTO> cancelarSesion(@PathVariable Long id) {
        Sesion sesion = sesionService.cancelarSesion(id);
        String mensaje = String.format(
                "Tu sesión de %s el %s a las %s fue cancelada correctamente.",
                sesion.getTratamiento().getNombre(), sesion.getFecha(), sesion.getHoraInicio());
        return ResponseEntity.ok(new SesionCancelacionResponseDTO(sesion, mensaje));
    }

    // Cancelar por Tratamiento + Fecha (WhatsApp modo 1)
    @PutMapping("/clientes/{clienteId}/tratamientos/{tratamientoId}/sesiones/{fecha}/cancelar")
    public ResponseEntity<SesionCancelacionResponseDTO> cancelarSesionPorTratamiento(
            @PathVariable Long clienteId, @PathVariable Long tratamientoId,
            @PathVariable String fecha) {

        LocalDate parsedDate = LocalDate.parse(fecha);
        Sesion sesion =
                sesionService.cancelarSesionPorTratamiento(clienteId, tratamientoId, parsedDate);
        String mensaje = String.format(
                "Tu sesión de %s el %s a las %s fue cancelada correctamente.",
                sesion.getTratamiento().getNombre(), sesion.getFecha(), sesion.getHoraInicio());
        return ResponseEntity.ok(new SesionCancelacionResponseDTO(sesion, mensaje));
    }

    // Cancelar por Fecha + Hora (WhatsApp modo 2)
    @PutMapping("/clientes/{clienteId}/sesiones/{fecha}/{hora}/cancelar")
    public ResponseEntity<SesionCancelacionResponseDTO> cancelarSesionPorFechaHora(
            @PathVariable Long clienteId, @PathVariable String fecha, @PathVariable String hora) {

        LocalDate parsedDate = LocalDate.parse(fecha); // YYYY-MM-DD
        LocalTime parsedTime = LocalTime.parse(hora); // HH:mm

        Sesion sesion = sesionService.cancelarSesionPorFechaHora(clienteId, parsedDate, parsedTime);
        String mensaje = String.format(
                "Tu sesión de %s el %s a las %s fue cancelada correctamente.",
                sesion.getTratamiento().getNombre(), sesion.getFecha(), sesion.getHoraInicio());
        return ResponseEntity.ok(new SesionCancelacionResponseDTO(sesion, mensaje));
    }

    @PutMapping("/sesiones/{id}/usar")
    public ResponseEntity<Sesion> marcarComoUsada(@PathVariable Long id) {
        return ResponseEntity.ok(sesionService.marcarComoUsada(id));
    }

    @PostMapping("/sesiones/dto")
    public ResponseEntity<Sesion> crearSesionDesdeDTO(@RequestBody SesionRequestDTO dto) {
        return ResponseEntity.ok(sesionService.crearSesionDesdeDTO(dto));
    }

    // =======================================================
    // Buscar sesiones de un cliente por fecha (para cancelar o reprogramar)
    // GET /api/clientes/{clienteId}/sesiones/por-fecha/{fecha}
    // =======================================================
    @GetMapping("/clientes/{clienteId}/sesiones/por-fecha/{fecha}")
    public ResponseEntity<List<SesionResponseDTO>> getSesionesPorFecha(@PathVariable Long clienteId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        try {
            List<SesionResponseDTO> sesiones =
                    sesionService.findByClienteAndFecha(clienteId, fecha);
            if (sesiones.isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(sesiones);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

}
