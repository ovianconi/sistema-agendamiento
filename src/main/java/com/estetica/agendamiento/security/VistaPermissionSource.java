package com.estetica.agendamiento.security;

import com.estetica.agendamiento.model.Vista;
import com.estetica.agendamiento.service.VistaService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VistaPermissionSource {

    private final VistaService vistaService;
    private List<Vista> vistas = new ArrayList<>();

    @PostConstruct
    public void init() {
        this.vistas = vistaService.listar();
    }

    public List<Vista> getVistas() {
        return vistas;
    }
}
