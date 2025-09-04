package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Rol;
import com.estetica.agendamiento.repository.RolRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RolService {
    private final RolRepository rolRepository;

    public RolService(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    public List<Rol> findAll() {
        return rolRepository.findAll();
    }

    public Rol findById(Long id) {
        return rolRepository.findById(id).orElseThrow(() -> new RuntimeException("Rol no encontrado"));
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
}
