package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Equipo;
import com.estetica.agendamiento.model.Tratamiento;
import com.estetica.agendamiento.repository.EquipoRepository;
import com.estetica.agendamiento.repository.TratamientoRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TratamientoService {

    private final TratamientoRepository tratamientoRepository;
    private final EquipoRepository equipoRepository;

    public TratamientoService(TratamientoRepository tratamientoRepository, EquipoRepository equipoRepository) {
        this.tratamientoRepository = tratamientoRepository;
        this.equipoRepository = equipoRepository;
    }

    public List<Tratamiento> findAll() {
        return tratamientoRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public Tratamiento findById(Long id) {
        return tratamientoRepository.findById(id).orElseThrow(() -> new RuntimeException("Tratamiento no encontrado"));
    }

    public Tratamiento save(Tratamiento tratamiento, Long equipoId) {
        if (equipoId != null) {
            Equipo equipo = equipoRepository.findById(equipoId)
                    .orElseThrow(() -> new RuntimeException("Equipo no encontrado"));
            tratamiento.setEquipo(equipo);
        } else {
            tratamiento.setEquipo(null);
        }
        return tratamientoRepository.save(tratamiento);
    }

    public Tratamiento update(Long id, Tratamiento tratamientoDetails, Long equipoId) {
        Tratamiento tratamiento = findById(id);
        tratamiento.setNombre(tratamientoDetails.getNombre());

        if (equipoId != null) {
            Equipo equipo = equipoRepository.findById(equipoId)
                    .orElseThrow(() -> new RuntimeException("Equipo no encontrado"));
            tratamiento.setEquipo(equipo);
        } else {
            tratamiento.setEquipo(null);
        }

        return tratamientoRepository.save(tratamiento);
    }

    public void delete(Long id) {
        tratamientoRepository.deleteById(id);
    }
}
