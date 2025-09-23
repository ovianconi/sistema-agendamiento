package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.repository.PersonalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonalService {

    private final PersonalRepository personalRepository;

    public Page<Personal> findAll(Pageable pageable) {
        return personalRepository.findAll(pageable);
    }

    public Personal save(Personal personal) {
        return personalRepository.save(personal);
    }

    public Personal update(Long id, Personal personal) {
        Personal existente = personalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Personal no encontrado"));
        existente.setNombre(personal.getNombre());
        existente.setApellido(personal.getApellido());
        existente.setCorreo(personal.getCorreo());
        existente.setTelefono(personal.getTelefono());
        existente.setTratamientos(personal.getTratamientos()); // actualizar relaciones
        return personalRepository.save(existente);
    }

    public void delete(Long id) {
        personalRepository.deleteById(id);
    }

    public Personal findById(Long id) {
        return personalRepository.findById(id).orElse(null);
    }
}
