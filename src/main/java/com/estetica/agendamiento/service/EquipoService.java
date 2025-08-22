package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Equipo;
import com.estetica.agendamiento.repository.EquipoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EquipoService {

    @Autowired
    private EquipoRepository equipoRepository;

    public List<Equipo> listarEquipos() {
        return equipoRepository.findAll();
    }

    public Optional<Equipo> obtenerEquipo(Long id) {
        return equipoRepository.findById(id);
    }

    public Equipo guardarEquipo(Equipo equipo) {
        return equipoRepository.save(equipo);
    }

    public void eliminarEquipo(Long id) {
        equipoRepository.deleteById(id);
    }
}
