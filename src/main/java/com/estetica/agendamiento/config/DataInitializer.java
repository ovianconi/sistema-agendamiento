package com.estetica.agendamiento.config;

import com.estetica.agendamiento.model.Rol;
import com.estetica.agendamiento.repository.RolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RolRepository rolRepository;

    public DataInitializer(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    @Override
    public void run(String... args) {
        String[] roles = { "ROLE_ADMIN", "ROLE_USER" };
        for (String roleName : roles) {
            Optional<Rol> rol = rolRepository.findByNombre(roleName);
            if (rol.isEmpty()) {
                rolRepository.save(new Rol(null, roleName));
            }
        }
    }
}
