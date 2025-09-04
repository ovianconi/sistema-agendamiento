package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Equipo;
import com.estetica.agendamiento.repository.EquipoRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EquipoService {

    private final EquipoRepository equipoRepository;

    public EquipoService(EquipoRepository equipoRepository) {
        this.equipoRepository = equipoRepository;
    }

    public List<Equipo> findAll() {
        return equipoRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public Equipo findById(Long id) {
        return equipoRepository.findById(id).orElseThrow(() -> new RuntimeException("Equipo no encontrado"));
    }

    public Equipo save(Equipo equipo) {
        return equipoRepository.save(equipo);
    }

    public Equipo update(Long id, Equipo equipo) {
        Equipo existente = findById(id);
        existente.setNombre(equipo.getNombre());
        existente.setCodigo(equipo.getCodigo());
        return equipoRepository.save(existente);
    }

    public void delete(Long id) {
        equipoRepository.deleteById(id);
    }
}
