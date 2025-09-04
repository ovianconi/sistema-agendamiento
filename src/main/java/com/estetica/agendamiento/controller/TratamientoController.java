package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Tratamiento;
import com.estetica.agendamiento.service.TratamientoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tratamientos")
@CrossOrigin(origins = "http://localhost:5173") // frontend
public class TratamientoController {

    private final TratamientoService tratamientoService;

    public TratamientoController(TratamientoService tratamientoService) {
        this.tratamientoService = tratamientoService;
    }

    @GetMapping
    public List<Tratamiento> getAll() {
        return tratamientoService.findAll();
    }

    @GetMapping("/{id}")
    public Tratamiento getById(@PathVariable Long id) {
        return tratamientoService.findById(id);
    }

    @PostMapping
    public Tratamiento create(@RequestBody Map<String, Object> payload) {
        Tratamiento tratamiento = new Tratamiento();
        tratamiento.setNombre((String) payload.get("nombre"));

        Long equipoId = payload.get("equipoId") != null ? Long.valueOf(payload.get("equipoId").toString()) : null;

        return tratamientoService.save(tratamiento, equipoId);
    }

    @PutMapping("/{id}")
    public Tratamiento update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Tratamiento tratamiento = new Tratamiento();
        tratamiento.setNombre((String) payload.get("nombre"));

        Long equipoId = payload.get("equipoId") != null ? Long.valueOf(payload.get("equipoId").toString()) : null;

        return tratamientoService.update(id, tratamiento, equipoId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        tratamientoService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
