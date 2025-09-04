package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.estetica.agendamiento.model.Rol;
import com.estetica.agendamiento.model.Usuario;
import com.estetica.agendamiento.repository.RolRepository;
import com.estetica.agendamiento.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<Usuario> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String role = body.getOrDefault("role", "USER");

        Rol userRole = rolRepository.findByNombre("ROLE_" + role.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRoles(Collections.singletonList(userRole));

        Usuario saved = usuarioRepository.save(usuario);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> credentials) {
        String token = authService.authenticate(credentials.get("username"), credentials.get("password"));

        if (token != null) {
            return Map.of("token", token);
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
    }
}
