package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.Cliente;
import com.estetica.agendamiento.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository repo) {
        this.clienteRepository = repo;
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public Optional<Cliente> obtenerCliente(Long id) {
        return clienteRepository.findById(id);
    }

    public Cliente guardarCliente(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    public void eliminarCliente(Long id) {
        clienteRepository.deleteById(id);
    }
}
