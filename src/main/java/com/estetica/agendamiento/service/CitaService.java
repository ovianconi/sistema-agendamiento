package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Cita;
import com.estetica.agendamiento.repository.CitaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CitaService {

    @Autowired
    private CitaRepository citaRepository;

    public List<Cita> listarCitas() {
        return citaRepository.findAll();
    }

    public Cita guardarCita(Cita cita) {
        return citaRepository.save(cita);
    }

    public void eliminarCita(Long id) {
        citaRepository.deleteById(id);
    }
}
