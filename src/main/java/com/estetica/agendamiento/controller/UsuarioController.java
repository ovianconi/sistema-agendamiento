package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Usuario;
import com.estetica.agendamiento.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "http://localhost:5173")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public List<Usuario> getAllUsuarios() {
        return usuarioService.findAll();
    }

    @PostMapping
    public ResponseEntity<Usuario> createUsuario(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        Long rolId = Long.valueOf(body.get("rolId").toString());

        return ResponseEntity.ok(usuarioService.createUsuario(username, password, rolId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> updateUsuario(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        Long rolId = body.containsKey("rolId") ? Long.valueOf(body.get("rolId").toString()) : null;

        return ResponseEntity.ok(usuarioService.updateUsuario(id, username, password, rolId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUsuario(@PathVariable Long id) {
        usuarioService.deleteUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
