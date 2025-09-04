package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Rol;
import com.estetica.agendamiento.model.Usuario;
import com.estetica.agendamiento.repository.RolRepository;
import com.estetica.agendamiento.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<Usuario> findAll() {
        return usuarioRepository.findAll();
    }

    public Usuario createUsuario(String username, String password, Long rolId) {
        Rol rol = rolRepository.findById(rolId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRoles(Collections.singletonList(rol));

        return usuarioRepository.save(usuario);
    }

    public Usuario updateUsuario(Long id, String username, String password, Long rolId) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        usuario.setUsername(username);
        if (password != null && !password.isBlank()) {
            usuario.setPassword(passwordEncoder.encode(password));
        }

        if (rolId != null) {
            Rol rol = rolRepository.findById(rolId)
                    .orElseThrow(() -> new RuntimeException("Rol no encontrado"));
            usuario.setRoles(new ArrayList<>(Collections.singletonList(rol))); // ✅ Aquí el fix
        }

        return usuarioRepository.save(usuario);
    }

    public void deleteUsuario(Long id) {
        usuarioRepository.deleteById(id);
    }
}
