package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.RolVista;
import com.estetica.agendamiento.model.Vista;
import com.estetica.agendamiento.repository.RolVistaRepository;
import com.estetica.agendamiento.repository.VistaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RolVistaService {

    private final RolVistaRepository rolVistaRepository;
    private final VistaRepository vistaRepository;

    public List<RolVista> listar() {
        return rolVistaRepository.findAll();
    }

    public RolVista crear(RolVista nuevo) {
        Vista vista = vistaRepository.findById(nuevo.getVista().getId())
                .orElseThrow(() -> new RuntimeException("Vista no encontrada"));
        nuevo.setVista(vista);
        return rolVistaRepository.save(nuevo);
    }

    public void eliminar(Long id) {
        rolVistaRepository.deleteById(id);
    }

    public List<RolVista> listarPorRol(String rol) {
        return rolVistaRepository.findByRol(rol);
    }

    public List<RolVista> listarPorVista(Long vistaId) {
        return rolVistaRepository.findByVistaId(vistaId);
    }
}
