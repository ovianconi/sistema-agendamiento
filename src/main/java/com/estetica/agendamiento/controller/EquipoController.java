package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Equipo;
import com.estetica.agendamiento.service.EquipoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/equipos")
public class EquipoController {

    private final EquipoService equipoService;

    public EquipoController(EquipoService equipoService) {
        this.equipoService = equipoService;
    }

    @GetMapping
    public List<Equipo> listar() {
        return equipoService.findAll();
    }

    @GetMapping("/{id}")
    public Equipo obtener(@PathVariable Long id) {
        return equipoService.findById(id);
    }

    @PostMapping
    public Equipo crear(@RequestBody Equipo equipo) {
        return equipoService.save(equipo);
    }

    @PutMapping("/{id}")
    public Equipo actualizar(@PathVariable Long id, @RequestBody Equipo equipo) {
        return equipoService.update(id, equipo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        equipoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
