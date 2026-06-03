package com.estetica.agendamiento.service;

import com.estetica.agendamiento.ai.ConversationAiResult;
import com.estetica.agendamiento.model.ChatConversationState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.estetica.agendamiento.dto.SesionesRestantesDTO;
import com.estetica.agendamiento.dto.SesionRequestDTO;
import com.estetica.agendamiento.model.Sesion;
import java.util.List;
import com.estetica.agendamiento.dto.SesionResponseDTO;
import com.estetica.agendamiento.dto.TratamientoDisponibleDTO;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class FlowService {

    private final ChatContextService chatContextService;
    private final TratamientoService tratamientoService;
    private final SesionService sesionService;
    private final ClienteService clienteService;

    public FlowResult manejar(String telefono, String mensajeOriginal, ChatConversationState estado,
            ConversationAiResult ai) {

        if (ai == null) {
            return responderGeneral(telefono, estado,
                    "No pude interpretar bien tu mensaje 😕. ¿Podés repetirlo?");
        }

        if (estado.tieneFlujoActivo()
                && ("abandonar_flujo".equals(normalizar(ai.getIntent()))
                        || pareceAbandonoEvidente(mensajeOriginal))) {

            estado.limpiarFlujo();
            chatContextService.guardarEstado(estado);

            return new FlowResult(
                    "Entendido, dejamos de lado este proceso. Si necesitás algo más, estoy para ayudarte.",
                    true);
        }

        if (!estado.tieneFlujoActivo() && parecePedidoMultiple(mensajeOriginal)) {
            estado.setFlujoActivo("consulta_multiple");
            estado.setIntent("consulta_multiple");
            estado.setEsperando("accion_inicial");
            estado.setAccionPendiente("accion_inicial");
            estado.setLastBotQuestion("¿Por cuál acción querés empezar?");
            chatContextService.guardarEstado(estado);

            return new FlowResult(
                    "Puedo ayudarte con más de una cosa 😊, pero vamos paso a paso para evitar errores. ¿Querés que empecemos por agendar, cancelar o consultar sesiones?",
                    false);
        }

        // Si hay flujo activo, respetamos el flujo antes que una intención nueva.
        if (estado.tieneFlujoActivo() && estado.estaEsperandoDato()) {
            if (pideCambioExplicitoDeFlujo(mensajeOriginal, ai, estado)) {
                estado.limpiarFlujo();
                chatContextService.guardarEstado(estado);
            } else {
                return continuarFlujoActivo(telefono, estado, ai);
            }
        }

        String intent = normalizar(ai.getIntent());

        return switch (intent) {
            case "agendar_sesion" -> iniciarAgendamiento(telefono, estado, ai);
            case "consultar_sesiones_restantes" -> iniciarConsultaSesiones(telefono, estado, ai);
            case "cancelar_sesion" -> iniciarCancelacion(telefono, estado, ai);
            case "reprogramar_sesion" -> iniciarReprogramacion(telefono, estado, ai);
            case "consultar_tratamientos_disponibles" -> consultarTratamientosDisponibles(estado);

            case "abandonar_flujo" -> abandonarFlujo(estado);

            case "saludo" -> responderGeneral(telefono, estado,
                    textoONulo(ai.getRespuestaSugerida(),
                            "¡Hola! 😊 Puedo ayudarte a consultar sesiones, agendar o cancelar una cita."));

            case "agradecimiento" -> responderGeneral(telefono, estado,
                    textoONulo(ai.getRespuestaSugerida(),
                            "¡De nada! 😊 Si querés, puedo ayudarte a consultar, agendar o cancelar una sesión."));

            default -> manejarConversacionGeneral(telefono, estado, ai);
        };
    }

    private boolean pareceAbandonoEvidente(String texto) {
        String m = normalizar(texto);

        return m.contains("deja nomas")
                || m.contains("dejá nomás")
                || m.contains("dejalo")
                || m.contains("déjalo")
                || m.contains("no importa")
                || m.contains("ya no quiero")
                || m.contains("gracias igual");
    }

    private FlowResult abandonarFlujo(ChatConversationState estado) {
        estado.limpiarFlujo();
        chatContextService.guardarEstado(estado);

        return new FlowResult(
                "Entendido, dejamos de lado este proceso. Si necesitás algo más, estoy para ayudarte.",
                true);
    }

    private void prepararAgendamientoSinCliente(ChatConversationState estado) {
        if (estado.getCliente() != null && estado.getCliente().getId() != null) {
            return;
        }

        estado.setTratamiento(null);
        estado.setFecha(null);
        estado.setHora(null);
        estado.setEsperandoConfirmacion(false);
        estado.setAccionPendiente("documento");
        estado.setEsperando("documento");
    }

    // ver el tema de respuesta sugerida por la ia y sacar respuesta en duro
    private FlowResult iniciarAgendamiento(String telefono, ChatConversationState estado, ConversationAiResult ai) {
        estado.setFlujoActivo("agendar_sesion");
        estado.setIntent("agendar_sesion");

        if (estado.getCliente() == null || estado.getCliente().getId() == null) {
            prepararAgendamientoSinCliente(estado);
            chatContextService.guardarEstado(estado);

            return new FlowResult(
                    "Para agendar una sesión necesito identificarte. Decime tu número de documento, por favor.",
                    false);
        }

        aplicarDatosDetectados(estado, ai);

        if (estaVacio(estado.getTratamiento())) {
            estado.setEsperando("tratamiento");
            estado.setAccionPendiente("tratamiento");
            estado.setLastBotQuestion("¿Qué tratamiento querés agendar?");
            chatContextService.guardarEstado(estado);
            return new FlowResult(ai.getRespuestaSugerida(), false);
        }

        if (estado.getFecha() == null && estado.getHora() == null) {
            estado.setEsperando("fecha_hora");
            estado.setAccionPendiente("fecha_hora");
            estado.setLastBotQuestion("¿Para qué fecha y hora querés agendar?");
            chatContextService.guardarEstado(estado);
            return new FlowResult("Perfecto. ¿Para qué fecha y hora querés agendar " + estado.getTratamiento() + "?",
                    false);
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
                ai.getRespuestaSugerida(),
                false);
    }

    private FlowResult continuarFlujoActivo(String telefono, ChatConversationState estado, ConversationAiResult ai) {
        String flujo = normalizar(estado.getFlujoActivo());
        String esperando = normalizar(estado.getEsperando());

        if (esInterrupcionConversacional(ai)) {
            return responderInterrupcionYRetomar(estado, ai);
        }

        if ("reprogramar_sesion".equals(flujo)) {
            if (Boolean.TRUE.equals(ai.getConfirmacion()) || "confirmar".equals(normalizar(ai.getIntent()))) {
                estado.limpiarFlujo();
                chatContextService.guardarEstado(estado);

                estado.setFlujoActivo("cancelar_sesion");
                estado.setIntent("cancelar_sesion");
                estado.setEsperando("fecha");
                estado.setAccionPendiente("fecha");
                estado.setLastBotQuestion("¿De qué fecha es la sesión que querés cancelar?");
                chatContextService.guardarEstado(estado);

                return new FlowResult("Perfecto. ¿De qué fecha es la sesión que querés cancelar?", false);
            }

            if (Boolean.FALSE.equals(ai.getConfirmacion()) || "negar".equals(normalizar(ai.getIntent()))) {
                estado.limpiarFlujo();
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "Entendido 😊. No hago ningún cambio. Si querés, puedo ayudarte a consultar, agendar o cancelar una sesión.",
                        true);
            }

            return new FlowResult("¿Querés empezar cancelando la sesión actual?", false);
        }

        if ("consultar_tratamientos_disponibles".equals(flujo)) {
            return consultarTratamientosDisponibles(estado);
        }

        // Confirmación o negación
        if ("confirmacion".equals(esperando)) {
            if (Boolean.TRUE.equals(ai.getConfirmacion()) || "confirmar".equals(normalizar(ai.getIntent()))) {
                if ("agendar_sesion".equals(normalizar(estado.getFlujoActivo()))) {
                    return confirmarAgendamiento(telefono, estado);
                }

                return new FlowResult("Confirmado.", false);
            }

            if (Boolean.FALSE.equals(ai.getConfirmacion()) || "negar".equals(normalizar(ai.getIntent()))) {
                estado.setEsperando("campo_a_corregir");
                estado.setAccionPendiente("campo_a_corregir");
                estado.setEsperandoConfirmacion(false);
                estado.setLastBotQuestion("¿Qué dato querés cambiar?");
                chatContextService.guardarEstado(estado);

                return new FlowResult(ai.getRespuestaSugerida(), false);
            }
        }

        if ("consulta_multiple".equals(flujo)) {
            String intent = normalizar(ai.getIntent());

            estado.limpiarFlujo();
            chatContextService.guardarEstado(estado);

            if ("agendar_sesion".equals(intent)) {
                return iniciarAgendamiento(telefono, estado, ai);
            }

            if ("cancelar_sesion".equals(intent)) {
                return iniciarCancelacion(telefono, estado, ai);
            }

            if ("consultar_sesiones_restantes".equals(intent)) {
                return iniciarConsultaSesiones(telefono, estado, ai);
            }

            estado.setFlujoActivo("consulta_multiple");
            estado.setIntent("consulta_multiple");
            estado.setEsperando("accion_inicial");
            estado.setAccionPendiente("accion_inicial");
            chatContextService.guardarEstado(estado);

            return new FlowResult("Decime por cuál querés empezar: agendar, cancelar o consultar sesiones.", false);
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

            return consultarSesionesRestantes(estado);
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

        return consultarSesionesRestantes(estado);
    }

    private FlowResult iniciarCancelacion(String telefono, ChatConversationState estado, ConversationAiResult ai) {
        estado.setFlujoActivo("cancelar_sesion");
        estado.setIntent("cancelar_sesion");

        aplicarDatosDetectados(estado, ai);
        normalizarEsperaCancelacion(estado);

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

        return cancelarSesion(estado);
    }

    private FlowResult manejarConversacionGeneral(String telefono, ChatConversationState estado,
            ConversationAiResult ai) {
        String intent = normalizar(ai.getIntent());
        String tema = normalizar(textoONulo(ai.getTemaGeneral(), intent));

        estado.setTemaGeneral(textoONulo(ai.getTemaGeneral(), ai.getIntent()));
        estado.setLastBotQuestion(null);
        chatContextService.guardarEstado(estado);

        if ("pregunta_recomendacion_estetica".equals(intent)
                || "pregunta_recomendacion_estetica".equals(tema)) {
            String respuestaIA = textoONulo(ai.getRespuestaSugerida(), "");

            return new FlowResult(respuestaIA, false);
        }

        if ("pregunta_sobre_tratamientos".equals(intent)
                || "pregunta_sobre_tratamientos".equals(tema)) {
            String respuesta = textoONulo(ai.getRespuestaSugerida(),
                    "Puedo darte una orientación general sobre tratamientos, pero la indicación correcta depende de una evaluación profesional. Si querés, puedo ayudarte a agendar una sesión.");

            return new FlowResult(respuesta, false);
        }

        if ("pregunta_fuera_de_alcance".equals(intent)
                || "pregunta_fuera_de_alcance".equals(tema)) {
            return new FlowResult(
                    "No tengo información suficiente para responder eso con precisión 😊. Pero puedo ayudarte con tus sesiones de la clínica: consultar sesiones restantes, agendar o cancelar una cita.",
                    false);
        }

        if ("consulta_multiple".equals(intent)) {
            estado.setFlujoActivo("consulta_multiple");
            estado.setIntent("consulta_multiple");
            estado.setEsperando("accion_inicial");
            estado.setAccionPendiente("accion_inicial");
            estado.setLastBotQuestion("¿Por cuál acción querés empezar?");
            chatContextService.guardarEstado(estado);

            return new FlowResult(
                    "Puedo ayudarte con más de una cosa 😊, pero vamos paso a paso para evitar errores. ¿Querés que empecemos por agendar, cancelar o consultar sesiones?",
                    false);
        }

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

    private void aplicarDatosSegunDatoEsperado(ChatConversationState estado, ConversationAiResult ai,
            String esperando) {
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
            if (!estaVacio(ai.getFecha()))
                estado.setFecha(parseFecha(ai.getFecha()));
            if (!estaVacio(ai.getHora()))
                estado.setHora(parseHora(ai.getHora()));
            return;
        }

        if ("hora_o_tratamiento".equals(esperando)) {
            if (!estaVacio(ai.getHora()))
                estado.setHora(parseHora(ai.getHora()));
            if (!estaVacio(ai.getTratamiento()))
                estado.setTratamiento(ai.getTratamiento());
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
        if (!estaVacio(ai.getTratamiento()))
            estado.setTratamiento(ai.getTratamiento());
        if (!estaVacio(ai.getFecha()))
            estado.setFecha(parseFecha(ai.getFecha()));
        if (!estaVacio(ai.getHora()))
            estado.setHora(parseHora(ai.getHora()));
    }

    private void normalizarEsperaCancelacion(ChatConversationState estado) {
        if (!"cancelar_sesion".equals(normalizar(estado.getFlujoActivo())))
            return;

        if (estado.getFecha() == null) {
            estado.setEsperando("fecha");
            estado.setAccionPendiente("fecha");
            return;
        }

        if (estado.getHora() == null && estaVacio(estado.getTratamiento())) {
            estado.setEsperando("hora_o_tratamiento");
            estado.setAccionPendiente("hora_o_tratamiento");
            return;
        }

        if (estado.getHora() == null && !estaVacio(estado.getTratamiento())) {
            estado.setEsperando("hora");
            estado.setAccionPendiente("hora");
        }
    }

    private LocalDate parseFecha(String fecha) {
        try {
            if (estaVacio(fecha))
                return null;
            return LocalDate.parse(fecha.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalTime parseHora(String hora) {
        try {
            if (estaVacio(hora))
                return null;

            String h = hora.trim();

            if (h.matches("^\\d{1,2}$")) {
                return LocalTime.of(Integer.parseInt(h), 0);
            }

            if (h.matches("^\\d{1,2}:\\d{2}$")) {
                if (h.length() == 4)
                    h = "0" + h;
                return LocalTime.parse(h);
            }

            return LocalTime.parse(h);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatearFecha(LocalDate fecha) {
        if (fecha == null)
            return "?";
        return fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String formatearHora(LocalTime hora) {
        if (hora == null)
            return "?";
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

    private FlowResult confirmarAgendamiento(String telefono, ChatConversationState estado) {
        try {
            if (estado.getCliente() == null || estado.getCliente().getId() == null) {
                estado.setEsperando("documento");
                estado.setAccionPendiente("documento");
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "Necesito identificarte antes de agendar. ¿Podés decirme tu documento?",
                        false);
            }

            if (estaVacio(estado.getTratamiento())) {
                estado.setEsperando("tratamiento");
                estado.setAccionPendiente("tratamiento");
                chatContextService.guardarEstado(estado);
                return new FlowResult("¿Qué tratamiento querés agendar?", false);
            }

            if (estado.getFecha() == null) {
                estado.setEsperando("fecha");
                estado.setAccionPendiente("fecha");
                chatContextService.guardarEstado(estado);
                return new FlowResult("¿Para qué fecha querés agendar?", false);
            }

            if (estado.getHora() == null) {
                estado.setEsperando("hora");
                estado.setAccionPendiente("hora");
                chatContextService.guardarEstado(estado);
                return new FlowResult("¿A qué hora querés agendar?", false);
            }

            Long tratamientoId = tratamientoService.buscarIdPorNombre(estado.getTratamiento());

            if (tratamientoId == null) {
                estado.setEsperando("tratamiento");
                estado.setAccionPendiente("tratamiento");
                chatContextService.guardarEstado(estado);
                return new FlowResult("No identifiqué el tratamiento *" + estado.getTratamiento()
                        + "*. ¿Podés repetir el nombre?", false);
            }

            SesionRequestDTO dto = new SesionRequestDTO();
            dto.setClienteId(estado.getCliente().getId());
            dto.setTratamientoId(tratamientoId);
            dto.setFecha(estado.getFecha());
            dto.setHoraInicio(estado.getHora());
            dto.setHoraFin(null);
            dto.setPersonalId(null);
            dto.setEquipoId(null);

            Sesion sesion = sesionService.crearSesionDesdeDTO(dto);

            String tratamiento = estado.getTratamiento();
            String fecha = estado.getFecha().toString();
            String hora = estado.getHora().toString().substring(0, 5);

            estado.limpiarFlujo();
            chatContextService.guardarEstado(estado);

            return new FlowResult("Listo, agendé tu sesión de " + tratamiento
                    + " para el " + fecha + " a las " + hora + ".", true);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Error desconocido";
            String lower = msg.toLowerCase();

            if (lower.contains("no tienes sesiones restantes")
                    || lower.contains("no tenes sesiones restantes")
                    || lower.contains("paquete no tiene sesiones")) {

                estado.setEsperando("tratamiento");
                estado.setAccionPendiente("tratamiento");
                estado.setEsperandoConfirmacion(false);
                estado.setFecha(null);
                estado.setHora(null);
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "No tenés sesiones restantes para " + estado.getTratamiento()
                                + " 😕. ¿Querés intentar con otro tratamiento?",
                        false);
            }

            if (lower.contains("personal") || lower.contains("ocupado") || lower.contains("disponible")) {
                estado.setEsperando("hora");
                estado.setAccionPendiente("hora");
                estado.setEsperandoConfirmacion(false);
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "En ese horario no hay disponibilidad 😕. ¿Querés probar otra hora?",
                        false);
            }

            return new FlowResult("No pude agendar la sesión: " + msg, false);
        }
    }

    private FlowResult consultarSesionesRestantes(ChatConversationState estado) {
        try {
            if (estado.getCliente() == null || estado.getCliente().getId() == null) {
                estado.setEsperando("documento");
                estado.setAccionPendiente("documento");
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "Necesito identificarte antes de consultar tus sesiones. ¿Podés decirme tu documento?",
                        false);
            }

            if (estaVacio(estado.getTratamiento())) {
                estado.setFlujoActivo("consultar_sesiones_restantes");
                estado.setIntent("consultar_sesiones_restantes");
                estado.setEsperando("tratamiento");
                estado.setAccionPendiente("tratamiento");
                chatContextService.guardarEstado(estado);

                return new FlowResult("¿De qué tratamiento querés consultar tus sesiones?", false);
            }

            Long tratamientoId = tratamientoService.buscarIdPorNombre(estado.getTratamiento());

            if (tratamientoId == null) {
                estado.setEsperando("tratamiento");
                estado.setAccionPendiente("tratamiento");
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "No identifiqué el tratamiento *" + estado.getTratamiento()
                                + "*. ¿Podés repetir el nombre?",
                        false);
            }

            SesionesRestantesDTO dto = clienteService.obtenerSesionesRestantes(
                    estado.getCliente().getId(),
                    tratamientoId);

            String tratamiento = estado.getTratamiento();

            estado.limpiarFlujo();
            chatContextService.guardarEstado(estado);

            if (dto == null) {
                return new FlowResult(
                        "No pude obtener información sobre ese tratamiento.",
                        true);
            }

            return switch (dto.getEstado()) {
                case "vigente" -> new FlowResult(
                        "Te quedan " + dto.getSesionesRestantes()
                                + " sesiones de " + tratamiento + ".",
                        true);

                case "agotado" -> new FlowResult(
                        "Ya no te quedan sesiones de " + tratamiento + ".",
                        true);

                case "no_tiene" -> new FlowResult(
                        "No encontré el tratamiento " + tratamiento
                                + " entre tus paquetes adquiridos.",
                        true);

                default -> new FlowResult(
                        "No pude determinar tu estado para " + tratamiento + ".",
                        true);
            };

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Error desconocido";

            return new FlowResult(
                    "No pude consultar tus sesiones: " + msg,
                    false);
        }
    }

    private FlowResult cancelarSesion(ChatConversationState estado) {
        try {
            if (estado.getCliente() == null || estado.getCliente().getId() == null) {
                estado.setEsperando("documento");
                estado.setAccionPendiente("documento");
                chatContextService.guardarEstado(estado);
                return new FlowResult("Necesito identificarte antes de cancelar. ¿Podés decirme tu documento?", false);
            }

            Long clienteId = estado.getCliente().getId();

            if (estado.getFecha() == null) {
                estado.setEsperando("fecha");
                estado.setAccionPendiente("fecha");
                chatContextService.guardarEstado(estado);
                return new FlowResult("¿De qué fecha querés cancelar la sesión?", false);
            }

            if (estado.getHora() != null) {
                Sesion sesion = sesionService.cancelarSesionPorFechaHora(
                        clienteId,
                        estado.getFecha(),
                        estado.getHora());

                String tratamiento = sesion.getTratamiento().getNombre();
                String fecha = formatearFecha(estado.getFecha());
                String hora = formatearHora(estado.getHora());

                estado.limpiarFlujo();
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "Tu sesión de " + tratamiento + " del " + fecha + " a las " + hora
                                + " fue cancelada correctamente.",
                        true);
            }

            if (!estaVacio(estado.getTratamiento())) {
                Long tratamientoId = tratamientoService.buscarIdPorNombre(estado.getTratamiento());

                if (tratamientoId == null) {
                    estado.setEsperando("tratamiento");
                    estado.setAccionPendiente("tratamiento");
                    chatContextService.guardarEstado(estado);
                    return new FlowResult("No identifiqué el tratamiento. ¿Podés repetir el nombre?", false);
                }

                List<SesionResponseDTO> sesiones = sesionService.findByClienteAndFecha(clienteId, estado.getFecha());

                List<SesionResponseDTO> coincidencias = sesiones.stream()
                        .filter(s -> tratamientoId.equals(s.getTratamientoId()))
                        .toList();

                if (coincidencias.isEmpty()) {
                    String tratamiento = estado.getTratamiento();
                    String fecha = formatearFecha(estado.getFecha());

                    estado.limpiarFlujo();
                    chatContextService.guardarEstado(estado);

                    return new FlowResult(
                            "No encontré una sesión pendiente de " + tratamiento
                                    + " para el " + fecha + ".",
                            true);
                }

                if (coincidencias.size() > 1) {
                    estado.setEsperando("hora");
                    estado.setAccionPendiente("hora");
                    estado.setLastBotQuestion(
                            "Hay más de una sesión de ese tratamiento. ¿Cuál horario querés cancelar?");
                    chatContextService.guardarEstado(estado);

                    return new FlowResult(
                            "Ese día tenés más de una sesión de " + estado.getTratamiento()
                                    + ". ¿Cuál horario querés cancelar?",
                            false);
                }

                SesionResponseDTO s = coincidencias.get(0);
                Sesion sesion = sesionService.cancelarSesion(s.getId());

                String tratamiento = sesion.getTratamiento().getNombre();
                String fecha = formatearFecha(sesion.getFecha());
                String hora = formatearHora(sesion.getHoraInicio());

                estado.limpiarFlujo();
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "Tu sesión de " + tratamiento + " del " + fecha + " a las " + hora
                                + " fue cancelada correctamente.",
                        true);
            }

            List<SesionResponseDTO> sesiones = sesionService.findByClienteAndFecha(clienteId, estado.getFecha());

            if (sesiones == null || sesiones.isEmpty()) {
                estado.limpiarFlujo();
                chatContextService.guardarEstado(estado);
                return new FlowResult("No encontré ninguna sesión agendada para ese día.", true);
            }

            if (sesiones.size() == 1) {
                SesionResponseDTO s = sesiones.get(0);

                Sesion sesion = sesionService.cancelarSesion(s.getId());

                String tratamiento = sesion.getTratamiento().getNombre();
                String fecha = formatearFecha(sesion.getFecha());
                String hora = formatearHora(sesion.getHoraInicio());

                estado.limpiarFlujo();
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "Tu sesión de " + tratamiento + " del " + fecha + " a las " + hora
                                + " fue cancelada correctamente.",
                        true);
            }

            estado.setEsperando("hora_o_tratamiento");
            estado.setAccionPendiente("hora_o_tratamiento");
            estado.setLastBotQuestion("Ese día tenés más de una sesión. ¿Cuál querés cancelar?");
            chatContextService.guardarEstado(estado);

            return new FlowResult(
                    "Ese día tenés más de una sesión. ¿Podés decirme la hora o el tratamiento de la que querés cancelar?",
                    false);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Error desconocido";
            String lower = msg.toLowerCase();

            if (lower.contains("solo puede cancelarse")) {
                estado.limpiarFlujo();
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "La sesión solo puede cancelarse hasta 2 horas antes del horario agendado.",
                        true);
            }

            if (lower.contains("no se encontró sesión pendiente")
                    || lower.contains("no se encontro sesion pendiente")
                    || lower.contains("no se encontró sesion pendiente")
                    || lower.contains("no se encontro sesión pendiente")) {

                estado.limpiarFlujo();
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "No encontré una sesión pendiente en esa fecha y hora.",
                        true);
            }

            return new FlowResult("No pude cancelar la sesión: " + msg, false);
        }
    }

    private boolean esInterrupcionConversacional(ConversationAiResult ai) {
        String intent = normalizar(ai.getIntent());
        String tema = normalizar(ai.getTemaGeneral());

        return intent.equals("conversacion_general")
                || intent.equals("pregunta_fuera_de_alcance")
                || intent.equals("pregunta_sobre_tratamientos")
                || intent.equals("pregunta_recomendacion_estetica")
                || tema.equals("conversacion_general")
                || tema.equals("pregunta_fuera_de_alcance")
                || tema.equals("pregunta_sobre_tratamientos")
                || tema.equals("pregunta_recomendacion_estetica");
    }

    private FlowResult responderInterrupcionYRetomar(ChatConversationState estado, ConversationAiResult ai) {
        String respuestaInterrupcion;

        String intent = normalizar(ai.getIntent());
        String tema = normalizar(ai.getTemaGeneral());

        if ("pregunta_recomendacion_estetica".equals(intent)
                || "pregunta_recomendacion_estetica".equals(tema)) {
            respuestaInterrupcion = "Puedo orientarte de forma general 😊, pero una recomendación estética adecuada debe hacerla un profesional de la clínica.";
        } else if ("pregunta_sobre_tratamientos".equals(intent)
                || "pregunta_sobre_tratamientos".equals(tema)) {
            respuestaInterrupcion = textoONulo(ai.getRespuestaSugerida(),
                    "Puedo darte una orientación general sobre tratamientos, pero la indicación correcta depende de una evaluación profesional.");
        } else if ("pregunta_fuera_de_alcance".equals(intent)
                || "pregunta_fuera_de_alcance".equals(tema)) {
            respuestaInterrupcion = "No tengo información en tiempo real sobre eso 😊.";
        } else {
            respuestaInterrupcion = textoONulo(ai.getRespuestaSugerida(),
                    "Entiendo 😊.");
        }

        String retomada = obtenerPreguntaDeRetomada(estado);

        if (yaIncluyeRetomada(respuestaInterrupcion, estado)) {
            return new FlowResult(respuestaInterrupcion, false);
        }

        return new FlowResult(respuestaInterrupcion + "\n\n" + retomada, false);
    }

    private String obtenerPreguntaDeRetomada(ChatConversationState estado) {
        String flujo = normalizar(estado.getFlujoActivo());
        String esperando = normalizar(estado.getEsperando());

        if ("agendar_sesion".equals(flujo)) {
            return switch (esperando) {
                case "tratamiento" -> "Seguimos con tu agendamiento: ¿qué tratamiento querés agendar?";
                case "fecha", "fecha_hora" -> "Seguimos con tu agendamiento: ¿para qué fecha querés agendar?";
                case "hora" -> "Seguimos con tu agendamiento: ¿a qué hora querés agendar?";
                case "confirmacion" -> "Seguimos con tu agendamiento: ¿confirmás la reserva?";
                case "campo_a_corregir" -> "Seguimos con la corrección: ¿querés cambiar tratamiento, fecha u hora?";
                default -> "Seguimos con tu agendamiento.";
            };
        }

        if ("consultar_sesiones_restantes".equals(flujo)) {
            return switch (esperando) {
                case "tratamiento" -> "Seguimos con la consulta: ¿de qué tratamiento querés consultar tus sesiones?";
                default -> "Seguimos con la consulta de tus sesiones.";
            };
        }

        if ("cancelar_sesion".equals(flujo)) {
            return switch (esperando) {
                case "fecha" -> "Seguimos con la cancelación: ¿de qué fecha querés cancelar la sesión?";
                case "hora" -> "Seguimos con la cancelación: ¿a qué hora era la sesión?";
                case "hora_o_tratamiento" ->
                    "Seguimos con la cancelación: decime la hora o el tratamiento de la sesión.";
                case "tratamiento" -> "Seguimos con la cancelación: ¿qué tratamiento querés cancelar?";
                default -> "Seguimos con la cancelación.";
            };
        }

        return "Podés decirme si querés consultar sesiones, agendar o cancelar una cita.";
    }

    private boolean yaIncluyeRetomada(String respuesta, ChatConversationState estado) {
        if (respuesta == null)
            return false;

        String r = normalizar(respuesta);
        String flujo = normalizar(estado.getFlujoActivo());
        String esperando = normalizar(estado.getEsperando());

        if ("agendar_sesion".equals(flujo)) {
            if ("tratamiento".equals(esperando)) {
                return r.contains("qué tratamiento")
                        || r.contains("que tratamiento")
                        || r.contains("tratamiento queres")
                        || r.contains("tratamiento querés");
            }

            if ("fecha".equals(esperando) || "fecha_hora".equals(esperando)) {
                return r.contains("qué fecha")
                        || r.contains("que fecha")
                        || r.contains("para qué fecha")
                        || r.contains("para que fecha");
            }

            if ("hora".equals(esperando)) {
                return r.contains("qué hora")
                        || r.contains("que hora")
                        || r.contains("a qué hora")
                        || r.contains("a que hora");
            }

            if ("confirmacion".equals(esperando)) {
                return r.contains("confirm")
                        || r.contains("confirmás")
                        || r.contains("confirmas");
            }
        }

        return false;
    }

    private boolean pideCambioExplicitoDeFlujo(
            String mensajeOriginal,
            ConversationAiResult ai,
            ChatConversationState estado) {
        if (ai == null || estado == null || !estado.tieneFlujoActivo()) {
            return false;
        }

        String intentNuevo = normalizar(ai.getIntent());
        String flujoActual = normalizar(estado.getFlujoActivo());
        String msg = normalizar(mensajeOriginal);

        if (intentNuevo.isBlank() || intentNuevo.equals(flujoActual)) {
            return false;
        }

        boolean intentPrincipal = intentNuevo.equals("agendar_sesion")
                || intentNuevo.equals("cancelar_sesion")
                || intentNuevo.equals("consultar_sesiones_restantes")
                || intentNuevo.equals("consultar_tratamientos_disponibles");

        if (!intentPrincipal) {
            return false;
        }

        boolean cambioIA = Boolean.TRUE.equals(ai.isCambiarFlujo());

        boolean cambioSeguroPorIntent = cambioIA && !intentNuevo.equals(flujoActual);

        boolean cambioPorFraseObvia = mensajePareceCambioExplicito(msg);

        return cambioSeguroPorIntent || cambioPorFraseObvia;
    }

    private boolean mensajePareceCambioExplicito(String msg) {
        if (estaVacio(msg))
            return false;

        return msg.contains("quiero agendar")
                || msg.contains("quiero reservar")
                || msg.contains("quiero cancelar")
                || msg.contains("quiero consultar")
                || msg.contains("cuantas sesiones")
                || msg.contains("cuántas sesiones")
                || msg.contains("que tratamientos tengo")
                || msg.contains("qué tratamientos tengo")
                || msg.contains("que puedo usar")
                || msg.contains("qué puedo usar")
                || msg.contains("mis tratamientos")
                || msg.contains("mis sesiones")
                || msg.contains("mejor quiero")
                || msg.contains("dejemos eso")
                || msg.contains("olvida eso")
                || msg.contains("olvidá eso");
    }

    private boolean parecePedidoMultiple(String texto) {
        if (texto == null)
            return false;

        String t = normalizar(texto);

        boolean pideAgendar = t.contains("agendar")
                || t.contains("reservar")
                || t.contains("turno");

        boolean pideCancelar = t.contains("cancelar")
                || t.contains("anular")
                || t.contains("suspender");

        boolean pideConsultar = t.contains("cuanto")
                || t.contains("cuánto")
                || t.contains("sesiones")
                || t.contains("me queda");

        int count = 0;
        if (pideAgendar)
            count++;
        if (pideCancelar)
            count++;
        if (pideConsultar)
            count++;

        return count >= 2;
    }

    private FlowResult iniciarReprogramacion(String telefono, ChatConversationState estado, ConversationAiResult ai) {
        estado.setFlujoActivo("reprogramar_sesion");
        estado.setIntent("reprogramar_sesion");
        estado.setEsperando("confirmar_cancelacion_previa");
        estado.setAccionPendiente("confirmar_cancelacion_previa");
        estado.setEsperandoConfirmacion(false);
        estado.setLastBotQuestion("¿Querés empezar cancelando la sesión actual?");
        chatContextService.guardarEstado(estado);

        return new FlowResult(
                "Puedo ayudarte a reprogramar 😊. Para evitar errores, primero cancelamos la sesión actual y luego agendamos una nueva. ¿Querés empezar cancelando la sesión que querés mover?",
                false);
    }

    private FlowResult consultarTratamientosDisponibles(ChatConversationState estado) {
        try {
            if (estado.getCliente() == null || estado.getCliente().getId() == null) {
                estado.setFlujoActivo("consultar_tratamientos_disponibles");
                estado.setIntent("consultar_tratamientos_disponibles");
                estado.setEsperando("documento");
                estado.setAccionPendiente("documento");
                chatContextService.guardarEstado(estado);

                return new FlowResult(
                        "Necesito identificarte antes de consultar tus tratamientos. ¿Podés decirme tu documento?",
                        false);
            }

            List<TratamientoDisponibleDTO> disponibles = clienteService
                    .obtenerTratamientosDisponibles(estado.getCliente().getId());

            estado.limpiarFlujo();
            chatContextService.guardarEstado(estado);

            if (disponibles == null || disponibles.isEmpty()) {
                return new FlowResult(
                        "No encontré tratamientos disponibles con sesiones restantes vigentes para tu cuenta. "
                                + "Puede ser que no tengas paquetes activos, que estén vencidos o que ya no tengan sesiones restantes. "
                                + "Si creés que esto es un error, comunicate con la clínica para verificar tus datos.",
                        true);
            }

            String lista = disponibles.stream()
                    .map(t -> "• " + t.getNombre() + ": " + t.getSesionesRestantes() + " sesiones")
                    .collect(java.util.stream.Collectors.joining("\n"));

            return new FlowResult(
                    "Tenés disponibles:\n" + lista,
                    true);

        } catch (Exception e) {
            return new FlowResult(
                    "No pude consultar tus tratamientos disponibles: " + e.getMessage(),
                    false);
        }
    }

}