package com.estetica.agendamiento.security;

import com.estetica.agendamiento.model.RolVista;
import com.estetica.agendamiento.service.VistaService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final VistaService vistaService;
    private final CorsConfigurationSource corsConfigurationSource; // ✅ inyectamos el bean del CorsConfig

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            AuthenticationProvider authenticationProvider)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> {

                    // ==============================
                    // RUTAS PÚBLICAS
                    // ==============================
                    auth.requestMatchers(
                            "/",
                            "/login",
                            "/error",
                            "/privacidad",

                            // 🔥 AUTH
                            "/api/auth/**",

                            // 🔥 WEBHOOKS (LOCAL Y PRODUCCIÓN)
                            "/webhooks/whatsapp/**",
                            "/api/webhooks/whatsapp/**",

                            // 🔥 RUTAS PÚBLICAS EXISTENTES
                            "/api/tratamientos/search/**",
                            "/api/sesiones/dto/**",
                            "/api/clientes/*/tratamientos/*/sesiones-restantes",
                            "/api/clientes/*/tratamientos-disponibles",
                            "/api/clientes/**",
                            "/api/clientes/by-telefono/**",
                            "/api/clientes/by-documento/**",
                            "/api/clientes/*/tratamientos/*/sesiones/*/cancelar",
                            "/api/clientes/*/sesiones/*/*/cancelar",
                            "/api/sesiones/cliente/*/por-fecha/**").permitAll();

                    // ==============================
                    // 🔐 RUTAS DINÁMICAS SEGÚN ROLES
                    // ==============================
                    vistaService.listar().forEach(v -> {
                        String[] roles = v.getRolesPermitidos().stream()
                                .map(RolVista::getRol)
                                .toArray(String[]::new);
                        if (roles.length > 0) {
                            auth.requestMatchers(v.getPath()).hasAnyAuthority(roles);
                        }
                    });

                    // ==============================
                    // CUALQUIER OTRA REQUIERE JWT
                    // ==============================
                    auth.anyRequest().authenticated();
                });

        http.authenticationProvider(authenticationProvider);

        // Filtro JWT antes del filtro de UsernamePasswordAuthentication
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
