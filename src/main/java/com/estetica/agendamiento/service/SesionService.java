package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Sesion;
import com.estetica.agendamiento.repository.SesionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SesionService {

    @Autowired
    private SesionRepository sesionRepository;

    public List<Sesion> listarSesiones() {
        return sesionRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public Sesion guardarSesiones(Sesion cita) {
        return sesionRepository.save(cita);
    }

    public void eliminarSesion(Long id) {
        sesionRepository.deleteById(id);
    }
}
