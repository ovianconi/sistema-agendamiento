package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Paquete;
import com.estetica.agendamiento.service.PaqueteService;
import lombok.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/paquetes")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PaqueteController {

    private final PaqueteService paqueteService;

    @GetMapping
    public List<PaqueteResp> all() {
        return paqueteService.findAll().stream().map(PaqueteController::toResp).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaqueteResp> get(@PathVariable Long id) {
        return paqueteService.findById(id).map(PaqueteController::toResp).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PaqueteResp> create(@RequestBody PaqueteReq req) {
        if (req.nombre() == null || req.nombre().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var created = paqueteService.create(req.nombre(), req.duracion(), req.items());
        return ResponseEntity.ok(toResp(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaqueteResp> update(@PathVariable Long id, @RequestBody PaqueteReq req) {
        var updated = paqueteService.update(id, req.nombre(), req.duracion(), req.items());
        return ResponseEntity.ok(toResp(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        paqueteService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ===== Requests / Responses =====
    public record PaqueteReq(String nombre, Integer duracion,
            List<com.estetica.agendamiento.service.PaqueteService.ItemReq> items) {
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PaqueteResp {
        private Long id;
        private String nombre;
        private Integer duracion;
        private List<ItemResp> items;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ItemResp {
        private Long tratamientoId;
        private String tratamientoNombre;
        private Integer sesiones;
    }

    private static PaqueteResp toResp(Paquete p) {
        List<ItemResp> items = p.getItems().stream()
                .map(pt -> ItemResp.builder().tratamientoId(pt.getTratamiento().getId())
                        .tratamientoNombre(pt.getTratamiento().getNombre())
                        .sesiones(pt.getSesiones()).build())
                .toList();

        return PaqueteResp.builder().id(p.getId()).nombre(p.getNombre()).duracion(p.getDuracion())
                .items(items).build();
    }
}
