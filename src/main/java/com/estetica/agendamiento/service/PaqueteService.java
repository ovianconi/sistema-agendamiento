package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Paquete;
import com.estetica.agendamiento.repository.PaqueteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaqueteService {

    @Autowired
    private PaqueteRepository paqueteRepository;

    public List<Paquete> listarPaquetes() {
        return paqueteRepository.findAll();
    }

    public Optional<Paquete> obtenerPaquete(Long id) {
        return paqueteRepository.findById(id);
    }

    public Paquete guardarPaquete(Paquete paquete) {
        return paqueteRepository.save(paquete);
    }

    public void eliminarPaquete(Long id) {
        paqueteRepository.deleteById(id);
    }
}
