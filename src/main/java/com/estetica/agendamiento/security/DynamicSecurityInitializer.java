package com.estetica.agendamiento.security;

import com.estetica.agendamiento.service.VistaService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DynamicSecurityInitializer implements ApplicationListener<ContextRefreshedEvent> {

    private final HttpSecurity http;
    private final VistaService vistaService;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            http.authorizeHttpRequests(auth -> {
                // 🔓 Rutas públicas
                auth.requestMatchers("/api/auth/**").permitAll();

                // 🔑 Reglas dinámicas desde BD
                vistaService.listar().forEach(v -> {
                    String[] roles = v.getRolesPermitidos().stream().map(rv -> rv.getRol()) // ya
                                                                                            // trae
                                                                                            // "ROLE_ADMIN",
                                                                                            // "ROLE_USER",
                                                                                            // etc.
                            .toArray(String[]::new);

                    if (roles.length > 0) {
                        auth.requestMatchers(v.getPath()).hasAnyAuthority(roles);
                    }
                });

                // 🔒 Fallback
                auth.anyRequest().authenticated();
            });
        } catch (Exception e) {
            throw new RuntimeException("Error inicializando reglas de seguridad dinámicas", e);
        }
    }
}
