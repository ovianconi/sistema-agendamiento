package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Rol;
import com.estetica.agendamiento.model.RolVista;
import com.estetica.agendamiento.model.Vista;
import com.estetica.agendamiento.repository.RolRepository;
import com.estetica.agendamiento.repository.RolVistaRepository;
import com.estetica.agendamiento.repository.VistaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RolService {
    private final RolRepository rolRepository;
    private final VistaRepository vistaRepository;
    private final RolVistaRepository rolVistaRepository;

    public List<Rol> findAll() {
        return rolRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public Rol findById(Long id) {
        return rolRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));
    }

    public Rol save(Rol rol) {
        return rolRepository.save(rol);
    }

    public Rol update(Long id, Rol rol) {
        Rol existing = findById(id);
        existing.setNombre(rol.getNombre());
        return rolRepository.save(existing);
    }

    public void delete(Long id) {
        rolRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Vista> obtenerVistasPorRol(Long rolId) {
        return rolVistaRepository.findByRolId(rolId).stream().map(RolVista::getVista).toList();
    }

    @Transactional
    public void asignarVistas(Long rolId, List<Long> vistaIds) {
        Rol rol = rolRepository.findById(rolId)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));
        String roleName = rol.getNombre().trim(); // "ROLE_USER", etc.

        // Actuales del rol
        List<RolVista> actuales = rolVistaRepository.findByRol(roleName);
        Set<Long> actualesIds = actuales.stream().map(rv -> rv.getVista().getId())
                .collect(java.util.stream.Collectors.toSet());

        Set<Long> nuevasIds = new java.util.HashSet<>(vistaIds);

        // A eliminar: estaban y ya no vienen
        java.util.Set<Long> aEliminar = new java.util.HashSet<>(actualesIds);
        aEliminar.removeAll(nuevasIds);
        if (!aEliminar.isEmpty()) {
            rolVistaRepository.deleteByRolAndVistaIds(roleName, aEliminar);
        }

        // A insertar: vienen nuevas que no estaban
        java.util.Set<Long> aInsertar = new java.util.HashSet<>(nuevasIds);
        aInsertar.removeAll(actualesIds);
        for (Long idVista : aInsertar) {
            Vista vista = vistaRepository.findById(idVista)
                    .orElseThrow(() -> new RuntimeException("Vista no encontrada: " + idVista));
            RolVista rv = RolVista.builder().rol(roleName).vista(vista).build();
            rolVistaRepository.save(rv);
        }
    }
}
