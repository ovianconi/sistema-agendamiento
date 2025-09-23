package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.ClientePaquete;
import com.estetica.agendamiento.repository.ClientePaqueteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientePaqueteService {

    private final ClientePaqueteRepository clientePaqueteRepository;

    public List<ClientePaquete> listar() {
        return clientePaqueteRepository.findAll();
    }

    public ClientePaquete crear(ClientePaquete nuevo) {
        return clientePaqueteRepository.save(nuevo);
    }

    public ClientePaquete actualizar(Long id, ClientePaquete datos) {
        return clientePaqueteRepository.findById(id).map(existing -> {
            existing.setCliente(datos.getCliente());
            existing.setPaquete(datos.getPaquete());
            existing.setFechaCompra(datos.getFechaCompra());
            existing.setFechaValidez(datos.getFechaValidez());

            // 🔑 Solución: No reemplazamos la lista, la actualizamos
            existing.getTratamientos().clear();
            if (datos.getTratamientos() != null) {
                existing.getTratamientos().addAll(datos.getTratamientos());
            }

            return clientePaqueteRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("Asignación no encontrada"));
    }

    public void eliminar(Long id) {
        clientePaqueteRepository.deleteById(id);
    }
}
