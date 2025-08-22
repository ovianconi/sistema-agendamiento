package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.repository.PersonalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PersonalService {

    @Autowired
    private PersonalRepository personalRepository;

    public List<Personal> listarPersonal() {
        return personalRepository.findAll();
    }

    public Optional<Personal> obtenerPersonal(Long id) {
        return personalRepository.findById(id);
    }

    public Personal guardarPersonal(Personal personal) {
        return personalRepository.save(personal);
    }

    public void eliminarPersonal(Long id) {
        personalRepository.deleteById(id);
    }
}
