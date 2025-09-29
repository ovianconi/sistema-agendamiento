package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Vista;
import com.estetica.agendamiento.service.VistaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/vistas")
@RequiredArgsConstructor
public class VistaController {

    private final VistaService vistaService;

    @GetMapping
    public List<Vista> listar() {
        return vistaService.listar();
    }

    @GetMapping("/{id}")
    public Vista obtener(@PathVariable Long id) {
        return vistaService.obtenerPorId(id);
    }

    @PostMapping
    public Vista crear(@RequestBody Vista vista) {
        return vistaService.crear(vista);
    }

    @PutMapping("/{id}")
    public Vista actualizar(@PathVariable Long id, @RequestBody Vista vista) {
        return vistaService.actualizar(id, vista);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        vistaService.eliminar(id);
    }

    // 🔑 Endpoint dinámico: devuelve solo las vistas que el usuario puede ver
    @GetMapping("/disponibles")
    public List<Vista> vistasDisponibles(Authentication auth) {
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        List<String> rolesUsuario = authorities.stream().map(GrantedAuthority::getAuthority) // Ej:
                                                                                             // "ROLE_ADMIN"
                .toList();

        return vistaService.listarPorRoles(rolesUsuario);
    }
}
