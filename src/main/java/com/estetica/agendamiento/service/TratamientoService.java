package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Tratamiento;
import com.estetica.agendamiento.repository.TratamientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TratamientoService {

    @Autowired
    private TratamientoRepository tratamientoRepository;

    public List<Tratamiento> listarTratamientos() {
        return tratamientoRepository.findAll();
    }

    public Optional<Tratamiento> obtenerTratamiento(Long id) {
        return tratamientoRepository.findById(id);
    }

    public Tratamiento guardarTratamiento(Tratamiento tratamiento) {
        return tratamientoRepository.save(tratamiento);
    }

    public void eliminarTratamiento(Long id) {
        tratamientoRepository.deleteById(id);
    }
}
