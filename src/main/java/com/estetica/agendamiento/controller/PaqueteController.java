package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Paquete;
import com.estetica.agendamiento.service.PaqueteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/paquetes")
public class PaqueteController {

    @Autowired
    private PaqueteService paqueteService;

    @GetMapping
    public List<Paquete> listarPaquetes() {
        return paqueteService.listarPaquetes();
    }

    @GetMapping("/{id}")
    public Optional<Paquete> obtenerPaquete(@PathVariable Long id) {
        return paqueteService.obtenerPaquete(id);
    }

    @PostMapping
    public Paquete crearPaquete(@RequestBody Paquete paquete) {
        return paqueteService.guardarPaquete(paquete);
    }

    @DeleteMapping("/{id}")
    public void eliminarPaquete(@PathVariable Long id) {
        paqueteService.eliminarPaquete(id);
    }
}
