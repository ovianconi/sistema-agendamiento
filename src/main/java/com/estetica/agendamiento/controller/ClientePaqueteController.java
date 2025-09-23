package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.ClientePaquete;
import com.estetica.agendamiento.service.ClientePaqueteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/asignaciones")
@RequiredArgsConstructor
public class ClientePaqueteController {

    private final ClientePaqueteService clientePaqueteService;

    @GetMapping
    public ResponseEntity<List<ClientePaquete>> listar() {
        return ResponseEntity.ok(clientePaqueteService.listar());
    }

    @PostMapping
    public ResponseEntity<ClientePaquete> crear(@RequestBody ClientePaquete nuevo) {
        ClientePaquete creado = clientePaqueteService.crear(nuevo);
        return ResponseEntity.ok(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientePaquete> actualizar(@PathVariable Long id,
            @RequestBody ClientePaquete datosActualizados) {
        ClientePaquete actualizado = clientePaqueteService.actualizar(id, datosActualizados);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        clientePaqueteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
