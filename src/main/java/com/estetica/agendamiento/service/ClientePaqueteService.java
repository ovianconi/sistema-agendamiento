package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Cliente;
import com.estetica.agendamiento.model.ClientePaquete;
import com.estetica.agendamiento.model.ClientePaqueteTratamiento;
import com.estetica.agendamiento.model.Paquete;
import com.estetica.agendamiento.repository.ClientePaqueteRepository;
import com.estetica.agendamiento.repository.ClienteRepository;
import com.estetica.agendamiento.repository.PaqueteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientePaqueteService {

    private final ClientePaqueteRepository clientePaqueteRepository;
    private final ClienteRepository clienteRepository;
    private final PaqueteRepository paqueteRepository;

    public List<ClientePaquete> listar() {
        return clientePaqueteRepository.findAll();
    }

    @Transactional
    public ClientePaquete crear(ClientePaquete nuevo) {
        // 🔑 Verificamos cliente y paquete antes de crear
        Cliente cliente = clienteRepository.findById(nuevo.getCliente().getId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        Paquete paquete = paqueteRepository.findById(nuevo.getPaquete().getId())
                .orElseThrow(() -> new RuntimeException("Paquete no encontrado"));

        nuevo.setCliente(cliente);
        nuevo.setPaquete(paquete);

        // 🔑 Generar tratamientos para este cliente-paquete usando builder
        List<ClientePaqueteTratamiento> tratamientos = paquete.getItems().stream()
                .map(pt -> ClientePaqueteTratamiento.builder().clientePaquete(nuevo)
                        .tratamiento(pt.getTratamiento()).sesionesRestantes(pt.getSesiones())
                        .sesionesUsadas(0).build())
                .toList();

        nuevo.setTratamientos(tratamientos);

        return clientePaqueteRepository.save(nuevo);
    }

    @Transactional
    public ClientePaquete actualizar(Long id, ClientePaquete datos) {
        return clientePaqueteRepository.findById(id).map(existing -> {
            existing.setCliente(datos.getCliente());
            existing.setPaquete(datos.getPaquete());
            existing.setFechaCompra(datos.getFechaCompra());
            existing.setFechaValidez(datos.getFechaValidez());

            // 🔑 Actualizar tratamientos sin perder integridad
            existing.getTratamientos().clear();
            if (datos.getTratamientos() != null) {
                datos.getTratamientos().forEach(t -> t.setClientePaquete(existing));
                existing.getTratamientos().addAll(datos.getTratamientos());
            }

            return clientePaqueteRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("Asignación no encontrada"));
    }

    public void eliminar(Long id) {
        clientePaqueteRepository.deleteById(id);
    }
}
