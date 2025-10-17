package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Equipo;
import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.model.Tratamiento;
import com.estetica.agendamiento.repository.EquipoRepository;
import com.estetica.agendamiento.repository.PersonalRepository;
import com.estetica.agendamiento.repository.TratamientoRepository;
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


    private final TratamientoRepository tratamientoRepository;
    private final PersonalRepository personalRepository;
    private final EquipoRepository equipoRepository;

    public TratamientoController(TratamientoRepository tratamientoRepository,
            PersonalRepository personalRepository, EquipoRepository equipoRepository) {
        this.tratamientoRepository = tratamientoRepository;
        this.personalRepository = personalRepository;
        this.equipoRepository = equipoRepository;
    }

    // ===========================
    // PERSONAL que aplica un tratamiento
    // ===========================
    @GetMapping("/{id}/personal")
    public List<PersonalDTO> getPersonalPorTratamiento(@PathVariable Long id) {
        Tratamiento tratamiento = tratamientoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tratamiento no encontrado"));

        List<Personal> lista = personalRepository.findByTratamientos_Id(tratamiento.getId());

        return lista.stream().map(p -> new PersonalDTO(p.getId(), p.getNombre(), p.getApellido()))
                .toList();
    }

    // ===========================
    // EQUIPOS que aplica un tratamiento
    // ===========================
    @GetMapping("/{id}/equipos")
    public List<EquipoDTO> getEquiposPorTratamiento(@PathVariable Long id) {
        Tratamiento tratamiento = tratamientoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tratamiento no encontrado"));

        List<Equipo> lista = equipoRepository.findByTratamientos_Id(tratamiento.getId());

        return lista.stream().map(e -> new EquipoDTO(e.getId(), e.getNombre())).toList();
    }

    // ✅ Nuevo endpoint para búsqueda por nombre (parcial o exacto)
    /**
     * Endpoint para el orquestador/IA. Devuelve SOLO el ID (Long) del tratamiento si se encuentra;
     * en caso contrario, null (200 OK con body = null).
     *
     * Ej: GET /api/tratamientos/search?nombre=criolipolisis
     */
    @GetMapping("/search")
    public ResponseEntity<Long> buscarPorNombre(@RequestParam("nombre") String nombre) {
        Long id = tratamientoService.buscarIdPorNombre(nombre);
        return ResponseEntity.ok(id);
    }

    // DTOs para frontend
    public record PersonalDTO(Long id, String nombre, String apellido) {
    }
    public record EquipoDTO(Long id, String nombre) {
    }

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
