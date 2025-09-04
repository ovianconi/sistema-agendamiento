package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Rol;
import com.estetica.agendamiento.service.RolService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RolController {
    private final RolService rolService;

    public RolController(RolService rolService) {
        this.rolService = rolService;
    }

    @GetMapping
    public List<Rol> getAll() {
        return rolService.findAll();
    }

    @GetMapping("/{id}")
    public Rol getById(@PathVariable Long id) {
        return rolService.findById(id);
    }

    @PostMapping
    public Rol create(@RequestBody Rol rol) {
        return rolService.save(rol);
    }

    @PutMapping("/{id}")
    public Rol update(@PathVariable Long id, @RequestBody Rol rol) {
        return rolService.update(id, rol);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        rolService.delete(id);
    }
}
