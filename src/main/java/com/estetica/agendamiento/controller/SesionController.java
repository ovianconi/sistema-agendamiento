package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Sesion;
import com.estetica.agendamiento.service.SesionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/sesiones")
@RequiredArgsConstructor
public class SesionController {

    private final SesionService sesionService;

    @PostMapping
    public ResponseEntity<Sesion> crearSesion(@RequestParam Long clienteId,
            @RequestParam Long tratamientoId, @RequestParam LocalDate fecha,
            @RequestParam LocalTime horaInicio,
            @RequestParam(defaultValue = "60") int duracionMinutos) {
        return ResponseEntity.ok(sesionService.crearSesion(clienteId, tratamientoId, fecha,
                horaInicio, duracionMinutos));
    }

    @GetMapping
    public ResponseEntity<List<Sesion>> listar() {
        return ResponseEntity.ok(sesionService.listarSesiones());
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<Sesion> cancelarSesion(@PathVariable Long id) {
        return ResponseEntity.ok(sesionService.cancelarSesion(id));
    }
}
