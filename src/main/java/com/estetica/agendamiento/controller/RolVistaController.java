package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.RolVista;
import com.estetica.agendamiento.service.RolVistaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rol-vista")
@RequiredArgsConstructor
public class RolVistaController {

    private final RolVistaService rolVistaService;

    @GetMapping
    public List<RolVista> listar() {
        return rolVistaService.listar();
    }

    @PostMapping
    public RolVista crear(@RequestBody RolVista rv) {
        return rolVistaService.crear(rv);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        rolVistaService.eliminar(id);
    }

    @GetMapping("/rol/{rol}")
    public List<RolVista> listarPorRol(@PathVariable String rol) {
        return rolVistaService.listarPorRol(rol);
    }

    @GetMapping("/vista/{vistaId}")
    public List<RolVista> listarPorVista(@PathVariable Long vistaId) {
        return rolVistaService.listarPorVista(vistaId);
    }
}
