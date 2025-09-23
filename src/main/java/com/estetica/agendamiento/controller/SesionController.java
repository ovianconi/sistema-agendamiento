package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Sesion;
import com.estetica.agendamiento.service.SesionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sesiones")
public class SesionController {

    @Autowired
    private SesionService sesionService;

    @GetMapping
    public List<Sesion> listarSesiones() {
        return sesionService.listarSesiones();
    }

    @PostMapping
    public Sesion crearSesiones(@RequestBody Sesion cita) {
        return sesionService.guardarSesiones(cita);
    }

    @DeleteMapping("/{id}")
    public void eliminarSesiones(@PathVariable Long id) {
        sesionService.eliminarSesion(id);
    }
}
