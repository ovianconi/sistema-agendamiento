package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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
    public Map<String, String> register(@RequestBody Map<String, String> userData) {
        String username = userData.get("username");
        String password = userData.get("password");
        String role = userData.get("role");

        if (usuarioRepository.findByUsername(username).isPresent()) {
            return Map.of("error", "El usuario ya existe");
        }

        Rol userRole = rolRepository.findByNombre("ROLE_" + role.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRoles(Collections.singleton(userRole));

        usuarioRepository.save(usuario);

        return Map.of("message", "Usuario registrado con éxito");
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> credentials) {
        String token = authService.authenticate(credentials.get("username"), credentials.get("password"));
        return token != null ? Map.of("token", token) : Map.of("error", "Credenciales inválidas");
    }
}
