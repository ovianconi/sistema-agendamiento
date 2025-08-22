package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Tratamiento;
import com.estetica.agendamiento.service.TratamientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tratamientos")
public class TratamientoController {

    @Autowired
    private TratamientoService tratamientoService;

    @GetMapping
    public List<Tratamiento> listarTratamientos() {
        return tratamientoService.listarTratamientos();
    }

    @GetMapping("/{id}")
    public Optional<Tratamiento> obtenerTratamiento(@PathVariable Long id) {
        return tratamientoService.obtenerTratamiento(id);
    }

    @PostMapping
    public Tratamiento crearTratamiento(@RequestBody Tratamiento tratamiento) {
        return tratamientoService.guardarTratamiento(tratamiento);
    }

    @DeleteMapping("/{id}")
    public void eliminarTratamiento(@PathVariable Long id) {
        tratamientoService.eliminarTratamiento(id);
    }
}
