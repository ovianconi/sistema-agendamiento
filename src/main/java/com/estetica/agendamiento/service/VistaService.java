package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Vista;
import com.estetica.agendamiento.repository.VistaRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VistaService {

    private final VistaRepository vistaRepository;

    public List<Vista> listar() {
        return vistaRepository.findAllWithRoles();
    }

    public Vista obtenerPorId(Long id) {
        return vistaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vista no encontrada"));
    }

    public Vista crear(Vista vista) {
        if (vistaRepository.existsByNombre(vista.getNombre()))
            throw new RuntimeException("Ya existe una vista con ese nombre");
        if (vistaRepository.existsByPath(vista.getPath()))
            throw new RuntimeException("Ya existe una vista con ese path");
        return vistaRepository.save(vista);
    }

    @Transactional
    public Vista actualizar(Long id, Vista vista) {
        Vista existente = vistaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vista no encontrada"));

        // Actualizar solo campos básicos
        existente.setNombre(vista.getNombre());
        existente.setDescripcion(vista.getDescripcion());
        existente.setIcono(vista.getIcono());
        existente.setPath(vista.getPath());

        // 👇 No tocar rolesPermitidos
        return vistaRepository.save(existente);
    }

    public void eliminar(Long id) {
        vistaRepository.deleteById(id);
    }

    /**
     * Devuelve solo las vistas que el usuario puede ver, comparando los roles exactos (ej:
     * "ROLE_ADMIN").
     */
    @Transactional
    public List<Vista> listarPorRoles(Collection<String> rolesUsuario) {
        return vistaRepository.findAllWithRoles().stream().filter(v -> v.getRolesPermitidos()
                .stream().anyMatch(rv -> rolesUsuario.contains(rv.getRol()))).toList();
    }

}
