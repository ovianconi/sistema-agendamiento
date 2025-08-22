package com.estetica.agendamiento.config;

import com.estetica.agendamiento.model.Rol;
import com.estetica.agendamiento.repository.RolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initRoles(RolRepository rolRepository) {
        return args -> {
            createRoleIfNotExists("ROLE_ADMIN", rolRepository);
            createRoleIfNotExists("ROLE_CLIENTE", rolRepository);
        };
    }

    private void createRoleIfNotExists(String roleName, RolRepository rolRepository) {
        Optional<Rol> rol = rolRepository.findByNombre(roleName);
        if (rol.isEmpty()) {
            Rol newRole = new Rol();
            newRole.setNombre(roleName);
            rolRepository.save(newRole);
            System.out.println("Rol creado: " + roleName);
        }
    }
}
