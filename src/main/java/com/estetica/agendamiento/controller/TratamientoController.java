package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Tratamiento;
import com.estetica.agendamiento.service.TratamientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tratamientos")
public class TratamientoController {

    @Autowired
    private TratamientoService tratamientoService;

    @GetMapping
    public ResponseEntity<List<Tratamiento>> getAllTratamientos() {
        return ResponseEntity.ok(tratamientoService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tratamiento> getTratamientoById(@PathVariable Long id) {
        return tratamientoService.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Tratamiento> createTratamiento(@RequestBody Tratamiento tratamiento) {
        return ResponseEntity.ok(tratamientoService.save(tratamiento));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Tratamiento> updateTratamiento(@PathVariable Long id,
            @RequestBody Tratamiento tratamiento) {
        return tratamientoService.findById(id).map(existing -> {
            tratamiento.setId(id);
            return ResponseEntity.ok(tratamientoService.save(tratamiento));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTratamiento(@PathVariable Long id) {
        if (tratamientoService.findById(id).isPresent()) {
            tratamientoService.delete(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
