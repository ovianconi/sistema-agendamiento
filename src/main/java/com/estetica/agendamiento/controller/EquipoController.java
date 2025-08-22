package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Equipo;
import com.estetica.agendamiento.service.EquipoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/equipos")
public class EquipoController {

    @Autowired
    private EquipoService equipoService;

    @GetMapping
    public List<Equipo> listarEquipos() {
        return equipoService.listarEquipos();
    }

    @GetMapping("/{id}")
    public Optional<Equipo> obtenerEquipo(@PathVariable Long id) {
        return equipoService.obtenerEquipo(id);
    }

    @PostMapping
    public Equipo crearEquipo(@RequestBody Equipo equipo) {
        return equipoService.guardarEquipo(equipo);
    }

    @DeleteMapping("/{id}")
    public void eliminarEquipo(@PathVariable Long id) {
        equipoService.eliminarEquipo(id);
    }
}
