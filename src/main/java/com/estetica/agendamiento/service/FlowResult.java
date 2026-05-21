package com.estetica.agendamiento.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FlowResult {
    private String respuesta;
    private boolean finalizarFlujo;
}