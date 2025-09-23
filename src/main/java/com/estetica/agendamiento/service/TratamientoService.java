package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Tratamiento;
import com.estetica.agendamiento.repository.TratamientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TratamientoService {

    @Autowired
    private TratamientoRepository tratamientoRepository;

    public List<Tratamiento> findAll() {
        return tratamientoRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public Optional<Tratamiento> findById(Long id) {
        return tratamientoRepository.findById(id);
    }

    public Tratamiento save(Tratamiento tratamiento) {
        return tratamientoRepository.save(tratamiento);
    }

    public void delete(Long id) {
        tratamientoRepository.deleteById(id);
    }
}
