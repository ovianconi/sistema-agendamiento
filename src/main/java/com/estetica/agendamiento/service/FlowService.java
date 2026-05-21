package com.estetica.agendamiento.service;

import com.estetica.agendamiento.ai.ConversationAiResult;
import com.estetica.agendamiento.model.ChatConversationState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class FlowService {

    private final ChatContextService chatContextService;

    @Transactional
    public FlowResult manejar(String telefono, ChatConversationState estado, ConversationAiResult ai) {

        if (ai == null) {
            return responderGeneral(telefono, estado,
                    "No pude interpretar bien tu mensaje 😕. ¿Podés repetirlo?");
        }

        // Si hay flujo activo, respetamos el flujo antes que una intención nueva.
        if (estado.tieneFlujoActivo() && estado.estaEsperandoDato()) {
            return continuarFlujoActivo(telefono, estado, ai);
        }

        String intent = normalizar(ai.getIntent());

        return switch (intent) {
            case "agendar_sesion" -> iniciarAgendamiento(telefono, estado, ai);
            case "consultar_sesiones_restantes" -> iniciarConsultaSesiones(telefono, estado, ai);
            case "cancelar_sesion" -> iniciarCancelacion(telefono, estado, ai);
            case "saludo" -> responderGeneral(telefono, estado,
                    textoONulo(ai.getRespuestaSugerida(),
                            "¡Hola! 😊 Puedo ayudarte a consultar sesiones, agendar o cancelar una cita."));
            case "agradecimiento" -> responderGeneral(telefono, estado,
                    textoONulo(ai.getRespuestaSugerida(),
                            "¡De nada! 😊 Si querés, puedo ayudarte a consultar, agendar o cancelar una sesión."));
            default -> manejarConversacionGeneral(telefono, estado, ai);
        };
    }

    private FlowResult iniciarAgendamiento(String telefono, ChatConversationState estado, ConversationAiResult ai) {
        estado.setFlujoActivo("agendar_sesion");
        estado.setIntent("agendar_sesion");

        aplicarDatosDetectados(estado, ai);

        if (estaVacio(estado.getTratamiento())) {
            estado.setEsperando("tratamiento");
            estado.setAccionPendiente("tratamiento");
            estado.setLastBotQuestion("¿Qué tratamiento querés agendar?");
            chatContextService.guardarEstado(estado);
            return new FlowResult("Claro 😊. ¿Qué tratamiento querés agendar?", false);
        }

        if (estado.getFecha() == null && estado.getHora() == null) {
            estado.setEsperando("fecha_hora");
            estado.setAccionPendiente("fecha_hora");
            estado.setLastBotQuestion("¿Para qué fecha y hora querés agendar?");
            chatContextService.guardarEstado(estado);
            return new FlowResult("Perfecto. ¿Para qué fecha y hora querés agendar " + estado.getTratamiento() + "?", false);
        }

        if (estado.getFecha() == null) {
            estado.setEsperando("fecha");
            estado.setAccionPendiente("fecha");
            estado.setLastBotQuestion("¿Para qué fecha querés agendar?");
            chatContextService.guardarEstado(estado);
            return new FlowResult("¿Para qué fecha querés agendar " + estado.getTratamiento() + "?", false);
        }

        if (estado.getHora() == null) {
            estado.setEsperando("hora");
            estado.setAccionPendiente("hora");
            estado.setLastBotQuestion("¿A qué hora querés agendar?");
            chatContextService.guardarEstado(estado);
            return new FlowResult("¿A qué hora querés agendar " + estado.getTratamiento() + "?", false);
        }

        estado.setEsperando("confirmacion");
        estado.setAccionPendiente("confirmacion");
        estado.setEsperandoConfirmacion(true);
        estado.setLastBotQuestion("Confirmación de agendamiento");
        chatContextService.guardarEstado(estado);

        return new FlowResult(
                "Me confirmás, ¿querés agendar *" + estado.getTratamiento()
                        + "* el *" + formatearFecha(estado.getFecha())
                        + "* a las *" + formatearHora(estado.getHora()) + "*?",
                false
        );
    }

    private FlowResult continuarFlujoActivo(String telefono, ChatConversationState estado, ConversationAiResult ai) {
        String flujo = normalizar(estado.getFlujoActivo());
        String esperando = normalizar(estado.getEsperando());

        // Confirmación o negación
        if ("confirmacion".equals(esperando)) {
            if (Boolean.TRUE.equals(ai.getConfirmacion()) || "confirmar".equals(normalizar(ai.getIntent()))) {
                return new FlowResult("Perfecto. En la siguiente etapa conectamos esto con la creación real de la sesión.", false);
            }

            if (Boolean.FALSE.equals(ai.getConfirmacion()) || "negar".equals(normalizar(ai.getIntent()))) {
                estado.setEsperando("campo_a_corregir");
                estado.setAccionPendiente("campo_a_corregir");
                estado.setEsperandoConfirmacion(false);
                estado.setLastBotQuestion("¿Qué dato querés cambiar?");
                chatContextService.guardarEstado(estado);

                return new FlowResult("Sin problema 😊. ¿Qué querés cambiar: tratamiento, fecha u hora?", false);
            }
        }

        if ("agendar_sesion".equals(flujo)) {
            aplicarDatosSegunDatoEsperado(estado, ai, esperando);
            return iniciarAgendamiento(telefono, estado, ai);
        }

        if ("consultar_sesiones_restantes".equals(flujo)) {
            aplicarDatosSegunDatoEsperado(estado, ai, esperando);

            if (estaVacio(estado.getTratamiento())) {
                estado.setEsperando("tratamiento");
                estado.setAccionPendiente("tratamiento");
                chatContextService.guardarEstado(estado);
                return new FlowResult("¿De qué tratamiento querés consultar tus sesiones?", false);
            }

            return new FlowResult("En la siguiente etapa conectamos esta consulta con tus sesiones reales de "
                    + estado.getTratamiento() + ".", false);
        }

        if ("cancelar_sesion".equals(flujo)) {
            aplicarDatosSegunDatoEsperado(estado, ai, esperando);
            return iniciarCancelacion(telefono, estado, ai);
        }

        return manejarConversacionGeneral(telefono, estado, ai);
    }

    private FlowResult iniciarConsultaSesiones(String telefono, ChatConversationState estado, ConversationAiResult ai) {
        estado.setFlujoActivo("consultar_sesiones_restantes");
        estado.setIntent("consultar_sesiones_restantes");

        aplicarDatosDetectados(estado, ai);

        if (estaVacio(estado.getTratamiento())) {
            estado.setEsperando("tratamiento");
            estado.setAccionPendiente("tratamiento");
            estado.setLastBotQuestion("¿De qué tratamiento querés consultar tus sesiones?");
            chatContextService.guardarEstado(estado);
            return new FlowResult("¿De qué tratamiento querés consultar tus sesiones?", false);
        }

        chatContextService.guardarEstado(estado);

        return new FlowResult("En la siguiente etapa conectamos la consulta real de sesiones para "
                + estado.getTratamiento() + ".", false);
    }

    private FlowResult iniciarCancelacion(String telefono, ChatConversationState estado, ConversationAiResult ai) {
        estado.setFlujoActivo("cancelar_sesion");
        estado.setIntent("cancelar_sesion");

        aplicarDatosDetectados(estado, ai);

        if (estado.getFecha() == null) {
            estado.setEsperando("fecha");
            estado.setAccionPendiente("fecha");
            estado.setLastBotQuestion("¿De qué fecha querés cancelar la sesión?");
            chatContextService.guardarEstado(estado);
            return new FlowResult("¿De qué fecha querés cancelar la sesión?", false);
        }

        if (estado.getHora() == null && estaVacio(estado.getTratamiento())) {
            estado.setEsperando("hora_o_tratamiento");
            estado.setAccionPendiente("hora_o_tratamiento");
            estado.setLastBotQuestion("¿Qué sesión de ese día querés cancelar?");
            chatContextService.guardarEstado(estado);
            return new FlowResult("Decime la hora o el tratamiento de la sesión que querés cancelar.", false);
        }

        chatContextService.guardarEstado(estado);

        return new FlowResult("En la siguiente etapa conectamos la cancelación real con fecha/tratamiento/hora.", false);
    }

    private FlowResult manejarConversacionGeneral(String telefono, ChatConversationState estado, ConversationAiResult ai) {
        estado.setTemaGeneral(textoONulo(ai.getTemaGeneral(), ai.getIntent()));
        estado.setLastBotQuestion(null);
        chatContextService.guardarEstado(estado);

        String respuesta = textoONulo(ai.getRespuestaSugerida(),
                "Puedo ayudarte principalmente a consultar sesiones, agendar o cancelar una cita 😊.");

        return new FlowResult(respuesta, false);
    }

    private FlowResult responderGeneral(String telefono, ChatConversationState estado, String respuesta) {
        estado.setTemaGeneral("conversacion_general");
        estado.setLastBotQuestion(null);
        chatContextService.guardarEstado(estado);
        return new FlowResult(respuesta, false);
    }

    private void aplicarDatosSegunDatoEsperado(ChatConversationState estado, ConversationAiResult ai, String esperando) {
        if ("tratamiento".equals(esperando) && !estaVacio(ai.getTratamiento())) {
            estado.setTratamiento(ai.getTratamiento());
            return;
        }

        if ("fecha".equals(esperando) && !estaVacio(ai.getFecha())) {
            estado.setFecha(parseFecha(ai.getFecha()));
            return;
        }

        if ("hora".equals(esperando) && !estaVacio(ai.getHora())) {
            estado.setHora(parseHora(ai.getHora()));
            return;
        }

        if ("fecha_hora".equals(esperando)) {
            if (!estaVacio(ai.getFecha())) estado.setFecha(parseFecha(ai.getFecha()));
            if (!estaVacio(ai.getHora())) estado.setHora(parseHora(ai.getHora()));
            return;
        }

        if ("hora_o_tratamiento".equals(esperando)) {
            if (!estaVacio(ai.getHora())) estado.setHora(parseHora(ai.getHora()));
            if (!estaVacio(ai.getTratamiento())) estado.setTratamiento(ai.getTratamiento());
            return;
        }

        if ("campo_a_corregir".equals(esperando)) {
            String campo = normalizar(ai.getCampoACorregir());

            if ("fecha".equals(campo)) {
                estado.setEsperando("fecha");
                estado.setAccionPendiente("fecha");
            } else if ("hora".equals(campo)) {
                estado.setEsperando("hora");
                estado.setAccionPendiente("hora");
            } else if ("tratamiento".equals(campo)) {
                estado.setEsperando("tratamiento");
                estado.setAccionPendiente("tratamiento");
            }
        }
    }

    private void aplicarDatosDetectados(ChatConversationState estado, ConversationAiResult ai) {
        if (!estaVacio(ai.getTratamiento())) estado.setTratamiento(ai.getTratamiento());
        if (!estaVacio(ai.getFecha())) estado.setFecha(parseFecha(ai.getFecha()));
        if (!estaVacio(ai.getHora())) estado.setHora(parseHora(ai.getHora()));
    }

    private LocalDate parseFecha(String fecha) {
        try {
            if (estaVacio(fecha)) return null;
            return LocalDate.parse(fecha.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime parseHora(String hora) {
        try {
            if (estaVacio(hora)) return null;

            String h = hora.trim();

            if (h.matches("^\\d{1,2}$")) {
                return LocalTime.of(Integer.parseInt(h), 0);
            }

            if (h.matches("^\\d{1,2}:\\d{2}$")) {
                if (h.length() == 4) h = "0" + h;
                return LocalTime.parse(h);
            }

            return LocalTime.parse(h);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatearFecha(LocalDate fecha) {
        if (fecha == null) return "?";
        return fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String formatearHora(LocalTime hora) {
        if (hora == null) return "?";
        return hora.toString().substring(0, 5);
    }

    private String normalizar(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private boolean estaVacio(String s) {
        return s == null || s.isBlank();
    }

    private String textoONulo(String valor, String fallback) {
        return estaVacio(valor) ? fallback : valor;
    }
}