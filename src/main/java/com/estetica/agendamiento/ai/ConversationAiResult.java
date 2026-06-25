package com.estetica.agendamiento.ai;

import lombok.Data;

@Data
public class ConversationAiResult {

    private String intent;
    private String flujoActivo;
    private String esperando;

    private String tratamiento;
    private String fecha;
    private String hora;

    private Boolean confirmacion;
    private String campoACorregir;

    private String temaGeneral;
    private String respuestaSugerida;

    private boolean cambiarFlujo;

    private String tipoReferencia;
}