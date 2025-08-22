package com.estetica.agendamiento.repository;

import com.estetica.agendamiento.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Cliente findByDocumento(String documento);
}
