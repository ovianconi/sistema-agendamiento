package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Cita;
import com.estetica.agendamiento.service.CitaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sesiones")
public class CitaController {

    @Autowired
    private CitaService citaService;

    @GetMapping
    public List<Cita> listarCitas() {
        return citaService.listarCitas();
    }

    @PostMapping
    public Cita crearCita(@RequestBody Cita cita) {
        return citaService.guardarCita(cita);
    }

    @DeleteMapping("/{id}")
    public void eliminarCita(@PathVariable Long id) {
        citaService.eliminarCita(id);
    }
}
