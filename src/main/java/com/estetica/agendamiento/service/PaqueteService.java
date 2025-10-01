package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.*;
import com.estetica.agendamiento.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PaqueteService {

    private final PaqueteRepository paqueteRepository;
    private final TratamientoRepository tratamientoRepository;
    private final PaqueteTratamientoRepository paqueteTratamientoRepository;

    public List<Paquete> findAll() {
        return paqueteRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public Optional<Paquete> findById(Long id) {
        return paqueteRepository.findById(id);
    }

    @Transactional
    public Paquete create(String nombre, Integer duracion, List<ItemReq> items) {
        Paquete p =
                Paquete.builder().nombre(nombre).duracion(duracion != null ? duracion : 0).build();
        p.setItems(new LinkedHashSet<>());
        Paquete saved = paqueteRepository.save(p);

        if (items != null) {
            for (ItemReq it : items) {
                Tratamiento t = tratamientoRepository.findById(it.tratamientoId())
                        .orElseThrow(() -> new RuntimeException(
                                "Tratamiento no encontrado: " + it.tratamientoId()));
                PaqueteTratamiento pt = PaqueteTratamiento.builder().paquete(saved).tratamiento(t)
                        .sesiones(it.sesiones()).build();
                paqueteTratamientoRepository.save(pt);
                saved.getItems().add(pt);
            }
        }
        return saved;
    }

    @Transactional
    public Paquete update(Long id, String nombre, Integer duracion, List<ItemReq> items) {
        Paquete p = paqueteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paquete no encontrado"));

        p.setNombre(nombre);
        p.setDuracion(duracion != null ? duracion : p.getDuracion());

        // limpiar items previos (orphanRemoval = true en Paquete)
        p.getItems().clear();
        paqueteRepository.save(p);

        if (items != null) {
            for (ItemReq it : items) {
                Tratamiento t = tratamientoRepository.findById(it.tratamientoId())
                        .orElseThrow(() -> new RuntimeException(
                                "Tratamiento no encontrado: " + it.tratamientoId()));
                PaqueteTratamiento pt = PaqueteTratamiento.builder().paquete(p).tratamiento(t)
                        .sesiones(it.sesiones()).build();
                paqueteTratamientoRepository.save(pt);
                p.getItems().add(pt);
            }
        }
        return p;
    }

    public void delete(Long id) {
        paqueteRepository.deleteById(id);
    }

    // ===== Modelos de request ligeros (DTOs in-file) =====
    public record ItemReq(Long tratamientoId, Integer sesiones) {
    }
}
