package com.estetica.agendamiento.whatsapp;

import com.estetica.agendamiento.ai.IntentResult;
import com.estetica.agendamiento.ai.LlmClient;
import com.estetica.agendamiento.dto.ClienteResponseDTO;
import com.estetica.agendamiento.dto.SesionResponseDTO;
import com.estetica.agendamiento.dto.SesionesRestantesDTO;
import com.estetica.agendamiento.dto.WhatsappMessageDTO;
import com.estetica.agendamiento.util.FechaHoraRelativaUtil;
import com.estetica.agendamiento.util.FechaRelativaUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.time.DayOfWeek;
import java.time.Duration;

@Component
public class NluOrchestrator {

    private final LlmClient llm;
    private final RestTemplate http = new RestTemplate();

    @Value("${whatsapp.access-token}")
    private String whatsappToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    // Contexto conversacional por teléfono
    private final Map<String, ConversacionContexto> contextos = new ConcurrentHashMap<>();

    // cada 10 minutos elimina contextos inactivos (sin actividad en los últimos 15 minutos)
    @Scheduled(fixedRate = 600000)
    public void limpiarContextosAntiguos() {
        Instant ahora = Instant.now();
        contextos.entrySet().removeIf(entry -> {
            ConversacionContexto ctx = entry.getValue();
            return ctx.ultimoAcceso != null
                    && Duration.between(ctx.ultimoAcceso, ahora).toMinutes() > 15;
        });
        System.out.println(
                "🧹 Contextos antiguos eliminados automáticamente (" + Instant.now() + ")");
    }

    @Autowired
    public NluOrchestrator(@Qualifier("openAiLlmClient") LlmClient llm) {
        this.llm = llm;
    }


    // ===============================================================
    // Procesamiento principal
    // ===============================================================
    public void processIncomingMessage(WhatsappMessageDTO msg) {
        try {
            final String telefono = msg.getTelefono();
            System.out.println("💬 Mensaje recibido: " + msg.getTexto());
            System.out.println("📱 Telefono: " + telefono);

            String textoOriginal = msg.getTexto();
            String textoNormalizado = normalizarAcentos(textoOriginal);
            msg.setTexto(textoNormalizado); // opcional, si querés conservarlo limpio

            System.out.println("🧹 Texto normalizado: " + textoNormalizado);
            System.out.println("🧹 Texto msg: " + msg.getTexto());

            // ------- PRIORIDAD DE CONTEXTO: si hay agendamiento en curso, no paso por LLM -------
            final String lower = normalizarAcentos(msg.getTexto());
            ConversacionContexto ctx = contextos.getOrDefault(telefono, new ConversacionContexto());

            if ("agendar_sesion".equals(ctx.ultimaIntencion) && ctx.tratamientoPendiente != null) {
                // 2.1 Confirmación directa: "sí", "ok", "confirmo" → agendar YA con lo que hay en
                // el contexto
                if (esAfirmacion(lower)) {
                    System.out.println(
                            "[CTX] Confirmación afirmativa detectada SIN LLM. Agendando con contexto…");
                    System.out.println("     → ultratamiento usada: " + ctx.ultimoTratamiento);
                    System.out.println("     → tratamientopend usada: " + ctx.tratamientoPendiente);
                    System.out.println("     → Fecha usada: " + ctx.fechaPendiente);
                    System.out.println("     → Hora usada: " + ctx.horaPendiente);
                    LocalDate f =
                            (ctx.fechaPendiente != null) ? ctx.fechaPendiente : ctx.ultimaFecha;
                    LocalTime h = (ctx.horaPendiente != null) ? ctx.horaPendiente : ctx.ultimaHora;

                    if (f == null || h == null) {
                        // si por algo faltan, re-preguntamos
                        sendWhatsappMessage(telefono,
                                "Necesito la fecha y hora para confirmar. ¿Me repetís ambas?");
                        return;
                    }

                    RespuestaOp res = crearSesionDesdeIA_R(ctx.clienteId, ctx.tratamientoPendiente,
                            f.toString(), h.toString());

                    // limpiar modo confirmación si agendó o fue error no-capacidad
                    ctx.esperandoConfirmacion = false;
                    if (res.texto.toLowerCase().contains("agendé")
                            || res.texto.toLowerCase().contains("agende")) {
                        ctx.ultimoTratamiento = ctx.tratamientoPendiente;
                        ctx.ultimaFecha = f;
                        ctx.ultimaHora = h;
                    }
                    ctx.tratamientoPendiente = null;
                    ctx.fechaPendiente = null;
                    ctx.horaPendiente = null;
                    contextos.put(telefono, ctx);

                    sendWhatsappMessage(telefono, res.texto);
                    return;
                }

                // 2.2 Corrección en la misma frase (ej: “no, mejor el jueves a las 13”)
                if (esNegacion(lower) || lower.contains("mejor") || lower.contains("cambi")) {
                    Correccion corr = extraerCorreccionFechaHora(lower);
                    if (corr != null) {
                        if (corr.fecha() != null)
                            ctx.fechaPendiente = corr.fecha();
                        if (corr.hora() != null)
                            ctx.horaPendiente = corr.hora();

                        System.out.printf("[CTX] Corrección detectada SIN LLM → fecha=%s hora=%s%n",
                                ctx.fechaPendiente, ctx.horaPendiente);

                        // preguntar confirmación con los nuevos datos
                        String fStr = (ctx.fechaPendiente != null) ? ctx.fechaPendiente.toString()
                                : "¿qué día?";
                        String hStr = (ctx.horaPendiente != null)
                                ? ctx.horaPendiente.toString().substring(0, 5)
                                : "¿qué hora?";
                        sendWhatsappMessage(telefono, "Entonces sería *" + ctx.tratamientoPendiente
                                + "* el *" + fStr + "* a las *" + hStr + "*, ¿confirmo?");
                        ctx.esperandoConfirmacion = true;
                        contextos.put(telefono, ctx);
                        return;
                    }

                    // Si dijo “no” pero no dio datos, pedimos qué cambiar
                    sendWhatsappMessage(telefono,
                            "Perfecto 😊. Decime qué querés cambiar — la *fecha*, la *hora* o el *tratamiento*.");
                    ctx.esperandoConfirmacion = true;
                    contextos.put(telefono, ctx);
                    return;
                }
            }
            // ------- fin prioridad de contexto -------


            IntentResult ir = llm.extractIntent(msg.getTexto(), "es-PY");
            System.out.println("🧠 Intent detectado: " + ir);
            System.out.println("📚 Contexto actual: " + ctx);

            // Si el LLM dijo “saludo” pero hay flujo de confirmación pendiente → forzar
            // confirmación
            if ("saludo".equals(ir.getIntent()) && "agendar_sesion".equals(ctx.ultimaIntencion)
                    && ctx.tratamientoPendiente != null) {
                System.out.println(
                        "[CTX] Override: LLM=saludo pero hay confirmación pendiente → tratar como confirmación.");
                // Simula afirmación y ejecuta el mismo bloque de arriba:
                LocalDate f = (ctx.fechaPendiente != null) ? ctx.fechaPendiente : ctx.ultimaFecha;
                LocalTime h = (ctx.horaPendiente != null) ? ctx.horaPendiente : ctx.ultimaHora;
                if (f == null || h == null) {
                    sendWhatsappMessage(telefono,
                            "Necesito la fecha y hora para confirmar. ¿Me repetís ambas?");
                    return;
                }
                RespuestaOp res = crearSesionDesdeIA_R(ctx.clienteId, ctx.tratamientoPendiente,
                        f.toString(), h.toString());

                ctx.esperandoConfirmacion = false;
                if (res.texto.toLowerCase().contains("agendé")
                        || res.texto.toLowerCase().contains("agende")) {
                    ctx.ultimoTratamiento = ctx.tratamientoPendiente;
                    ctx.ultimaFecha = f;
                    ctx.ultimaHora = h;
                }
                ctx.tratamientoPendiente = null;
                ctx.fechaPendiente = null;
                ctx.horaPendiente = null;
                contextos.put(telefono, ctx);

                sendWhatsappMessage(telefono, res.texto);
                return;
            }

            ir.setFecha(normalizarAcentos(ir.getFecha()));
            ir.setHora(normalizarAcentos(ir.getHora()));

            // Cliente
            ClienteResponseDTO cliente = identificarCliente(msg, ir);
            if (cliente == null) {
                sendWhatsappMessage(telefono,
                        "No pude identificarte todavía. ¿Podés decirme tu nombre o documento?");
                return;
            }
            msg.setCliente(cliente);

            // -------- Variables efectivas (no mutamos ir)
            String tratamientoEff = firstNonNull(safeTrimOrNull(ir.getTratamiento()),
                    ctx.tratamientoPendiente, ctx.ultimoTratamiento);
            String fechaEffTxt = safeTrimOrNull(ir.getFecha());
            String horaEffTxt = safeTrimOrNull(ir.getHora());

            // -------- Si hay contexto esperando confirmación y el usuario responde con “sí” o da
            // nueva fecha/hora
            String lower2 = msg.getTexto().toLowerCase(Locale.forLanguageTag("es-PY"));
            boolean confirma = ctx.esperandoConfirmacion
                    && lower2.matches(".*\\b(sí|si|dale|ok|perfecto)\\b.*");
            boolean mencionaHora = lower2.matches(".*\\d{1,2}([:.]\\d{2})?\\s*(am|pm)?\\b.*");
            boolean mencionaDia = contieneDiaRelativoOLiteral(lower2);

            LocalDate fechaCtx =
                    (ctx.fechaPendiente != null) ? ctx.fechaPendiente : ctx.ultimaFecha;
            LocalTime horaCtx = (ctx.horaPendiente != null) ? ctx.horaPendiente : ctx.ultimaHora;

            if (ctx.esperandoConfirmacion && (confirma || mencionaHora || mencionaDia)) {
                if (mencionaHora) {
                    String htxt = lower2; // parsea texto completo: soporta “2pm”, “2 pm”, “14”,
                                          // “8hs”
                    LocalTime tmp = parseHoraFlexible(htxt);
                    if (tmp != null)
                        horaCtx = tmp;
                }
                if (mencionaDia) {
                    LocalDate tmp = FechaRelativaUtil.parseFecha(lower2);
                    if (tmp != null)
                        fechaCtx = tmp;
                }

                if ("agendar_sesion".equals(ctx.ultimaIntencion)) {
                    RespuestaOp res = crearSesionDesdeIA_R(ctx.clienteId,
                            firstNonNull(tratamientoEff, ctx.tratamientoPendiente,
                                    ctx.ultimoTratamiento),
                            (fechaCtx != null ? fechaCtx.toString() : null),
                            (horaCtx != null ? horaCtx.toString() : null));

                    if (res.conflictoCapacidad) {
                        // seguimos esperando confirmación, actualizamos pendientes
                        ctx.tratamientoPendiente = firstNonNull(tratamientoEff,
                                ctx.tratamientoPendiente, ctx.ultimoTratamiento);
                        ctx.fechaPendiente = fechaCtx;
                        ctx.horaPendiente = horaCtx;
                        ctx.ultimoTratamiento = ctx.tratamientoPendiente;
                        ctx.ultimaFecha = fechaCtx;
                        ctx.ultimaHora = horaCtx;
                        ctx.esperandoConfirmacion = true;
                        contextos.put(telefono, ctx);
                    } else {
                        // si agendó ok o fue otro error no de capacidad → limpiar modo confirmación
                        ctx.esperandoConfirmacion = false;
                        ctx.tratamientoPendiente = null;
                        ctx.fechaPendiente = null;
                        ctx.horaPendiente = null;
                        if (res.texto.toLowerCase().contains("agendé")
                                || res.texto.toLowerCase().contains("agende")) {
                            ctx.ultimoTratamiento =
                                    firstNonNull(tratamientoEff, ctx.ultimoTratamiento);
                            ctx.ultimaFecha = fechaCtx;
                            ctx.ultimaHora = horaCtx;
                        }
                        contextos.put(telefono, ctx);
                    }

                    sendWhatsappMessage(telefono, res.texto);
                    return;
                }
            }


            // =======================================================
            // ✅ Confirmación de agendamiento pendiente (v2 robusta y depurable)
            // =======================================================
            if (ctx.esperandoConfirmacionAgendamiento && ctx.tieneDatosCompletosParaAgendar()) {
                String texto = msg.getTexto().toLowerCase(Locale.forLanguageTag("es-PY"));
                System.out.println("🧩 [DEBUG] Confirmación pendiente detectada:");
                System.out.println("     → Texto recibido: " + texto);
                System.out.println("     → Contexto antes: fecha=" + ctx.fechaPendiente + ", hora="
                        + ctx.horaPendiente);

                boolean contieneNegacion =
                        texto.matches(".*\\b(no|cambiar|otra|distinta|modificar|mejor)\\b.*");
                boolean contieneDia = texto.matches(
                        ".*(lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo|mañana|maana|pasado).*");
                boolean contieneHora = texto.matches(
                        ".*(\\b\\d{1,2}([:.]\\d{2})?\\s*(am|pm)?\\b|a las \\d{1,2}([:.]\\d{2})?)");

                // 🧩 Caso 1: mensaje contiene negación y nueva fecha/hora
                if (contieneNegacion && (contieneDia || contieneHora)) {
                    System.out.println("🧠 [DEBUG] Frase negativa con corrección detectada.");

                    LocalDate nuevaFecha = FechaRelativaUtil.parseFecha(texto);

                    // ⚙️ Extraer solo el fragmento probable de hora
                    String horaFragmento = null;
                    var matcher =
                            Pattern.compile("(\\d{1,2}([:.]\\d{2})?\\s*(am|pm)?)(?=\\b|\\s|$)")
                                    .matcher(texto);
                    if (matcher.find())
                        horaFragmento = matcher.group(1);
                    LocalTime nuevaHora = parseHoraFlexible(horaFragmento);

                    System.out.println("     → Fragmento de hora detectado: " + horaFragmento);
                    System.out.println("     → Nueva fecha parseada: " + nuevaFecha);
                    System.out.println("     → Nueva hora parseada: " + nuevaHora);

                    if (nuevaFecha != null)
                        ctx.fechaPendiente = nuevaFecha;
                    if (nuevaHora != null)
                        ctx.horaPendiente = nuevaHora;

                    ctx.esperandoConfirmacionAgendamiento = true;
                    contextos.put(telefono, ctx);

                    sendWhatsappMessage(telefono, String.format(
                            "Perfecto 👍. Entonces sería *%s* el *%s* a las *%s*, ¿confirmo?",
                            ctx.tratamientoPendiente, ctx.fechaPendiente, ctx.horaPendiente));
                    System.out.println("✅ [DEBUG] Contexto actualizado tras corrección → fecha="
                            + ctx.fechaPendiente + ", hora=" + ctx.horaPendiente);
                    return;
                }

                // 🧩 Caso 2: confirmación afirmativa → guardar en BD
                if (texto.matches(".*\\b(sí|si|dale|ok|correcto|perfecto|confirmo)\\b.*")) {
                    System.out.println(
                            "✅ [DEBUG] Confirmación afirmativa detectada. Creando sesión...");
                    System.out.println("     → Fecha usada: " + ctx.fechaPendiente);
                    System.out.println("     → Hora usada: " + ctx.horaPendiente);

                    RespuestaOp res = crearSesionDesdeIA_R(ctx.clienteId, ctx.tratamientoPendiente,
                            ctx.fechaPendiente != null ? ctx.fechaPendiente.toString() : "null",
                            ctx.horaPendiente != null ? ctx.horaPendiente.toString() : "null");

                    System.out.println("📦 [DEBUG] Body enviado al backend: cliente="
                            + ctx.clienteId + ", trat=" + ctx.tratamientoPendiente + ", fecha="
                            + ctx.fechaPendiente + ", hora=" + ctx.horaPendiente);
                    System.out.println("📦 [DEBUG] Resultado backend: " + res.texto);
                    sendWhatsappMessage(telefono, res.texto);
                    contextos.remove(telefono);
                    return;
                }

                // 🧩 Caso 3: negación sin especificar cambio
                if (contieneNegacion) {
                    System.out.println("🧠 [DEBUG] Negación sin detalle detectada.");
                    ctx.esperandoConfirmacionAgendamiento = false;
                    contextos.put(telefono, ctx);
                    sendWhatsappMessage(telefono,
                            "Perfecto 😊. Decime qué querés cambiar — la fecha, la hora o el tratamiento.");
                    return;
                }

                // 🧩 Caso 4: cambio directo (sin “no”)
                if (contieneDia || contieneHora) {
                    LocalDate nuevaFecha = FechaRelativaUtil.parseFecha(texto);

                    String horaFragmento = null;
                    var matcher =
                            Pattern.compile("(\\d{1,2}([:.]\\d{2})?\\s*(am|pm)?)(?=\\b|\\s|$)")
                                    .matcher(texto);
                    if (matcher.find())
                        horaFragmento = matcher.group(1);
                    LocalTime nuevaHora = parseHoraFlexible(horaFragmento);

                    System.out.println("🧠 [DEBUG] Cambio directo detectado.");
                    System.out.println("     → Fragmento de hora detectado: " + horaFragmento);
                    System.out.println("     → Nueva fecha parseada: " + nuevaFecha);
                    System.out.println("     → Nueva hora parseada: " + nuevaHora);

                    if (nuevaFecha != null)
                        ctx.fechaPendiente = nuevaFecha;
                    if (nuevaHora != null)
                        ctx.horaPendiente = nuevaHora;

                    ctx.esperandoConfirmacionAgendamiento = true;
                    contextos.put(telefono, ctx);

                    sendWhatsappMessage(telefono,
                            String.format("Entonces sería *%s* el *%s* a las *%s*, ¿confirmo?",
                                    ctx.tratamientoPendiente, ctx.fechaPendiente,
                                    ctx.horaPendiente));
                    System.out.println("✅ [DEBUG] Contexto actualizado → fecha="
                            + ctx.fechaPendiente + ", hora=" + ctx.horaPendiente);
                    return;
                }

                // 🧩 Caso 5: sin coincidencias válidas
                sendWhatsappMessage(telefono,
                        "Solo necesito que confirmes si está bien agendar esa sesión 😊 (responde *sí* o *no*).");
                System.out.println(
                        "⚠️ [DEBUG] No se detectó acción válida, esperando siguiente mensaje.");
                return;
            }



            // =======================================================
            // Procesamiento principal con valores efectivos
            // =======================================================
            String respuesta;
            switch (ir.getIntent()) {
                case "consultar_sesiones_restantes" -> {
                    respuesta = consultarSesionesRestantes(cliente.getId(), tratamientoEff);
                }
                case "cancelar_por_fecha_hora", "cancelar_por_tratamiento_fecha", "cancelar_sesion" -> {
                    respuesta = cancelarSesionFlexible(cliente.getId(), tratamientoEff, fechaEffTxt,
                            horaEffTxt);
                }
                case "agendar_sesion" -> {
                    ctx.ultimaIntencion = "agendar_sesion";
                    ctx.tratamientoPendiente = tratamientoEff;
                    ctx.fechaPendiente = FechaRelativaUtil.parseFecha(fechaEffTxt);
                    ctx.horaPendiente = (horaEffTxt != null && !horaEffTxt.isBlank())
                            ? parseHoraFlexible(horaEffTxt)
                            : LocalTime.of(8, 0);
                    ctx.clienteId = cliente.getId();

                    if (!ctx.tieneDatosCompletosParaAgendar()) {
                        contextos.put(telefono, ctx);
                        respuesta =
                                "Necesito algunos datos más 😊. Decime qué tratamiento querés y para qué día.";
                        break;
                    }

                    ctx.esperandoConfirmacionAgendamiento = true;
                    contextos.put(telefono, ctx);

                    respuesta = String.format("Confirmo, ¿querés agendar *%s* el *%s* a las *%s*?",
                            ctx.tratamientoPendiente, ctx.fechaPendiente, ctx.horaPendiente);
                    break;
                }
                case "agradecimiento" -> respuesta = "De nada 😊 ¡Que tengas un excelente día!";
                case "saludo" -> respuesta = "¡Hola " + cliente.getNombre()
                        + "! Soy el asistente de la clínica. ¿Querés consultar tus sesiones, cancelar o agendar una nueva?";
                default -> respuesta = "No estoy seguro de cómo ayudarte con eso todavía 🤔.";
            }

            sendWhatsappMessage(telefono, respuesta);

        } catch (Exception e) {
            e.printStackTrace();
            sendWhatsappMessage(msg.getTelefono(),
                    "Lo siento, hubo un error al procesar tu mensaje 😔. Intenta de nuevo más tarde.");
        }
    }

    // ===============================================================
    // Identificación de cliente
    // ===============================================================
    private ClienteResponseDTO identificarCliente(WhatsappMessageDTO msg, IntentResult ir) {
        try {
            String baseUrl = "http://localhost:8080/api/clientes";
            String ident = (ir.getClienteIdent() != null) ? ir.getClienteIdent() : "por_telefono";

            switch (ident) {
                case "por_documento" -> {
                    String doc = extraerDocumento(msg.getTexto());
                    if (doc != null)
                        return http.getForObject(baseUrl + "/by-documento/" + doc,
                                ClienteResponseDTO.class);
                }
                case "por_nombre" -> {
                    String nombre = extraerNombre(msg.getTexto());
                    if (nombre != null)
                        return http.getForObject(baseUrl + "/by-nombre/" + nombre,
                                ClienteResponseDTO.class);
                }
                default -> {
                    String telefono = msg.getTelefono().replace("+", "").replace(" ", "");
                    return http.getForObject(baseUrl + "/by-telefono/" + telefono,
                            ClienteResponseDTO.class);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error identificando cliente: " + e.getMessage());
        }
        return null;
    }

    // ===============================================================
    // Utilidades varias
    // ===============================================================
    private String extraerDocumento(String texto) {
        var matcher = Pattern.compile("\\b\\d{5,}\\b").matcher(texto);
        return matcher.find() ? matcher.group() : null;
    }

    private String extraerNombre(String texto) {
        texto = texto.toLowerCase(Locale.forLanguageTag("es-PY"));
        if (texto.contains("soy "))
            return texto.substring(texto.indexOf("soy ") + 4).trim();
        if (texto.contains("me llamo "))
            return texto.substring(texto.indexOf("me llamo ") + 9).trim();
        return null;
    }

    private static String safeTrimOrNull(String s) {
        if (s == null)
            return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static <T> T firstNonNull(T... vals) {
        for (T v : vals)
            if (v != null)
                return v;
        return null;
    }

    private boolean contieneDiaRelativoOLiteral(String lower) {
        return lower.contains("lunes") || lower.contains("martes") || lower.contains("miércoles")
                || lower.contains("miercoles") || lower.contains("jueves")
                || lower.contains("viernes") || lower.contains("sábado") || lower.contains("sabado")
                || lower.contains("domingo") || lower.contains("mañana") || lower.contains("maana")
                || lower.contains("pasado mañana") || lower.contains("pasado maana")
                || lower.matches(".*\\b(hoy|mañana|pasado)\\b.*");
    }

    // ===============================================================
    // Consultas
    // ===============================================================
    private String consultarSesionesRestantes(Long clienteId, String tratamiento) {
        if (clienteId == null)
            return "No encontré tu registro. ¿Podés confirmar tu número?";
        Long tratamientoId = buscarTratamientoIdPorNombre(tratamiento);
        if (tratamientoId == null)
            return "No identifiqué el tratamiento. ¿Podés repetir el nombre?";
        try {
            String url = "http://localhost:8080/api/clientes/%d/tratamientos/%d/sesiones-restantes"
                    .formatted(clienteId, tratamientoId);
            SesionesRestantesDTO dto = http.getForObject(url, SesionesRestantesDTO.class);
            if (dto == null)
                return "No pude obtener información sobre ese tratamiento.";
            return switch (dto.getEstado()) {
                case "vigente" -> "Te quedan " + dto.getSesionesRestantes() + " sesiones de "
                        + tratamiento + ".";
                case "agotado" -> "Ya no te quedan sesiones de " + tratamiento + ".";
                case "no_tiene" -> reinterpretarConIA("No encontré el tratamiento " + tratamiento
                        + " entre ninguno de tus paquetes adquiridos.");
                default -> "No pude determinar tu estado para " + tratamiento + ".";
            };
        } catch (Exception e) {
            return "Error consultando sesiones restantes: " + e.getMessage();
        }
    }

    // ===============================================================
    // Cancelación unificada
    // ===============================================================
    private String cancelarSesionFlexible(Long clienteId, String tratamiento, String fechaTexto,
            String horaTexto) {
        try {
            if (fechaTexto == null || fechaTexto.isBlank())
                return "¿Podés decirme de qué día querés cancelar la sesión?";

            // 1️⃣ Parsear fecha con los utilitarios + fallback IA
            var parsed = FechaHoraRelativaUtil.parse(((fechaTexto != null) ? fechaTexto : "") + " "
                    + ((horaTexto != null) ? horaTexto : ""));
            LocalDate fecha = (parsed != null) ? parsed.fechaInicio()
                    : FechaRelativaUtil.parseFecha(fechaTexto);
            String horaStr = (parsed != null && parsed.hora() != null) ? parsed.hora() : horaTexto;

            if (fecha == null)
                fecha = parseNthWeekdayOfMonth(fechaTexto, LocalDate.now());
            if (fecha == null)
                fecha = resolverFechaConIA(fechaTexto);

            if (fecha == null)
                return "No entendí la fecha '" + fechaTexto
                        + "'. ¿Podés indicarme una fecha específica?";

            // 2️⃣ Si el usuario no indicó hora, buscar sesión automáticamente
            if (horaStr == null || horaStr.isBlank()) {
                try {
                    String urlBuscar = "http://localhost:8080/api/clientes/%d/sesiones/por-fecha/%s"
                            .formatted(clienteId, fecha);
                    // Este endpoint debería devolver lista o la única sesión del día
                    var sesiones = http.getForObject(urlBuscar, SesionResponseDTO[].class);
                    if (sesiones != null && sesiones.length == 1) {
                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
                        horaStr = sesiones[0].getHoraInicio() != null
                                ? sesiones[0].getHoraInicio().format(fmt)
                                : null;
                    } else if (sesiones != null && sesiones.length > 1) {
                        return "Tenés más de una sesión ese día 😅. ¿Podés decirme la hora o el tratamiento?";
                    }
                } catch (Exception e) {
                    // si no existe el endpoint, seguimos con fallback a 08:00
                }
            }

            if (horaStr == null)
                horaStr = "08:00";
            String horaNorm = normalizarHora(horaStr);

            // 3️⃣ Construir la URL según tenga tratamiento o no
            String url;
            if (tratamiento != null && !tratamiento.isBlank()) {
                Long tratId = buscarTratamientoIdPorNombre(tratamiento);
                url = String.format(
                        "http://localhost:8080/api/clientes/%d/tratamientos/%d/sesiones/%s/cancelar",
                        clienteId, tratId, fecha);
            } else {
                url = String.format("http://localhost:8080/api/clientes/%d/sesiones/%s/%s/cancelar",
                        clienteId, fecha, horaNorm);
            }

            http.exchange(url, HttpMethod.PUT, null, String.class);

            // 4️⃣ Mensaje adaptado: solo incluir hora si se conoce realmente
            String horaTextoOut =
                    (horaTexto == null || horaTexto.isBlank()) ? "" : " a las " + horaNorm;
            return "Tu sesión" + (tratamiento != null ? " de " + tratamiento : "") + " del " + fecha
                    + horaTextoOut + " fue cancelada correctamente.";

        } catch (HttpClientErrorException e) {
            return extraerMensajeError(e.getResponseBodyAsString());
        } catch (Exception e) {
            return extraerMensajeError(e.getMessage());
        }
    }

    // ===============================================================
    // Agendamiento (versión que devuelve señal de conflicto)
    // ===============================================================
    private RespuestaOp crearSesionDesdeIA_R(Long clienteId, String tratamiento, String fechaTexto,
            String horaTexto) {
        try {
            Long tratamientoId = buscarTratamientoIdPorNombre(tratamiento);
            if (tratamientoId == null)
                return new RespuestaOp("No identifiqué el tratamiento. ¿Podés repetir el nombre?",
                        false);

            // 1) Intento con FechaHoraRelativaUtil (puede no traer hora si no hay “a las …”)
            var parsed = FechaHoraRelativaUtil.parse(((fechaTexto != null) ? fechaTexto : "") + " "
                    + ((horaTexto != null) ? horaTexto : ""));

            LocalDate fecha = (parsed != null) ? parsed.fechaInicio()
                    : FechaRelativaUtil.parseFecha(fechaTexto);
            LocalTime hora = null;
            if (parsed != null && parsed.hora() != null) {
                hora = parseHoraFlexible(parsed.hora()); // robusto: “14”, “2pm”, “8hs”
            }
            // 2) Si aún no hay hora, intentamos con horaTexto (el LLM suele poner “14” sin “a las”)
            if (hora == null) {
                hora = parseHoraFlexible(horaTexto);
            }

            if (hora == null) {
                System.out.println("🧠 [LAST_RESORT] HORA");
                hora = LocalTime.of(8, 0); // último respaldo
            }
            if (fecha == null) {
                fecha = parseNthWeekdayOfMonth(((fechaTexto != null) ? fechaTexto : "") + " "
                        + ((horaTexto != null) ? horaTexto : ""), LocalDate.now());
            }

            // 🧠 Fallback IA si seguimos sin fecha
            if (fecha == null) {
                fecha = resolverFechaConIA(((fechaTexto != null) ? fechaTexto : "") + " "
                        + ((horaTexto != null) ? horaTexto : ""));
            }

            var body = Map.of("clienteId", clienteId, "tratamientoId", tratamientoId, "fecha",
                    fecha.toString(), "horaInicio", hora.toString());

            var resp = http.postForEntity("http://localhost:8080/api/sesiones/dto", body,
                    String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                String horaStr = hora.toString().substring(0, 5);
                return new RespuestaOp("Listo, agendé tu sesión de " + tratamiento + " para el "
                        + fecha + " a las " + horaStr + ".", false);
            }
            String texto = extraerMensajeError(resp.getBody());
            return new RespuestaOp(texto, esConflictoCapacidad(texto));

        } catch (HttpClientErrorException e) {
            String texto = extraerMensajeError(e.getResponseBodyAsString());
            return new RespuestaOp(texto, esConflictoCapacidad(texto));
        } catch (Exception e) {
            String texto = extraerMensajeError(e.getMessage());
            return new RespuestaOp(texto, false);
        }
    }

    // ===============================================================
    // Detección de mensajes de conflicto/recursos del backend
    // ===============================================================
    private boolean esConflictoCapacidad(String msg) {
        if (msg == null)
            return false;
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("ocupad") || m.contains("no disponible") || m.contains("no hay recursos")
                || m.contains("ya tiene otra sesión") || m.contains("ya tiene otra sesion")
                || m.contains("superpone") || m.contains("solapada")
                || m.contains("equipo no disponible") || m.contains("terapista no disponible");
    }

    // ===============================================================
    // Errores del backend + IA
    // ===============================================================
    private String extraerMensajeError(String json) {
        if (json == null || json.isBlank())
            return "Error desconocido";
        try {
            var m = java.util.regex.Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(json);
            if (m.find())
                return reinterpretarConIA(m.group(1));
        } catch (Exception ignored) {
        }
        return reinterpretarConIA(json);
    }

    // ===============================================================
    // Hora flexible
    // ===============================================================
    private String normalizarHora(String texto) {
        LocalTime t = parseHoraFlexible(texto);
        return (t != null) ? t.toString() : "08:00";
    }

    private LocalTime parseHoraFlexible(String texto) {
        if (texto == null || texto.isBlank())
            return null;

        try {
            String t = texto.trim().toLowerCase(Locale.forLanguageTag("es-PY")).replace("hs", "")
                    .replace("h.", "").replace("h", "").replace(".", ":").replace(",", ":")
                    .replaceAll("\\s+", " ").trim();

            // 💬 Casos literales
            if (t.contains("mediodia") || t.contains("medio dia"))
                return LocalTime.of(12, 0);
            if (t.contains("medianoche"))
                return LocalTime.MIDNIGHT;

            // 💬 “de la tarde / noche / mañana” → AM/PM
            boolean isPM = t.contains("pm") || t.contains("tarde") || t.contains("noche");
            boolean isAM = t.contains("am") || t.contains("mañana") || t.contains("manana");

            // limpiamos palabras no numéricas
            t = t.replace("pm", "").replace("am", "").replace("de la tarde", "")
                    .replace("de la noche", "").replace("de la mañana", "")
                    .replace("de la manana", "").trim();

            // si tiene solo número → agregamos minutos
            if (!t.contains(":") && t.matches("\\d+"))
                t += ":00";

            // completamos si falta cero adelante
            if (t.matches("^\\d:\\d{2}$"))
                t = "0" + t;

            LocalTime hora = LocalTime.parse(t);

            // ajustes según AM/PM
            if (isPM && hora.getHour() < 12)
                hora = hora.plusHours(12);
            if (isAM && hora.getHour() == 12)
                hora = hora.minusHours(12);

            return hora;
        } catch (Exception e) {
            System.err.println("⚠️ Error al interpretar hora: " + texto + " → " + e.getMessage());
            return null;
        }
    }


    // ===============================================================
    // Buscar tratamiento
    // ===============================================================
    private Long buscarTratamientoIdPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank())
            return null;
        try {
            String url = "http://localhost:8080/api/tratamientos/search?nombre=" + nombre;
            return http.getForObject(url, Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ===============================================================
    // Envío a WhatsApp
    // ===============================================================
    private void sendWhatsappMessage(String telefonoDestino, String texto) {
        try {
            String url = "https://graph.facebook.com/v18.0/" + phoneNumberId + "/messages";
            Map<String, Object> payload = Map.of("messaging_product", "whatsapp", "to",
                    telefonoDestino, "type", "text", "text", Map.of("body", texto));

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(whatsappToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            http.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            System.err.println("❌ Error enviando mensaje a WhatsApp: " + e.getMessage());
        }
    }

    // ===============================================================
    // Reformulación IA (se mantiene tu versión)
    // ===============================================================
    private String reinterpretarConIA(String mensajeBackend) {
        if (mensajeBackend == null || mensajeBackend.isBlank())
            return "Ocurrió un error procesando tu solicitud 😕.";
        try {
            String prompt = """
                    Reformulá este mensaje técnico o del sistema en un texto breve, amable y natural
                    para enviar por WhatsApp a un cliente de una clínica de estética.
                    Debe sonar cercano, empático y variado, sin repetir siempre lo mismo.
                    No sugieras alternativas ni continúes la conversación.
                    Mensaje original: "%s"
                    """.formatted(mensajeBackend);
            String respuesta = llm.generateResponse(prompt, "es-PY", 0.3);
            if (respuesta == null || respuesta.isBlank())
                return "Ups 😅, algo salió mal. Podés intentar de nuevo en un momento.";
            return respuesta.trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "Tuvimos un inconveniente procesando tu solicitud 😔. Intentá más tarde.";
        }
    }

    // ===============================================================
    // Tipos de apoyo
    // ===============================================================
    private static class RespuestaOp {
        final String texto;
        final boolean conflictoCapacidad;

        RespuestaOp(String t, boolean c) {
            this.texto = t;
            this.conflictoCapacidad = c;
        }
    }

    private static class ConversacionContexto {
        public String ultimaIntencion;
        public String tratamientoPendiente;
        public LocalDate fechaPendiente;
        public LocalTime horaPendiente;
        public Long clienteId;
        public boolean esperandoConfirmacion;

        public Instant ultimoAcceso = Instant.now();

        // memoria suave
        public String ultimoTratamiento;
        public LocalDate ultimaFecha;
        public LocalTime ultimaHora;

        @Override
        public String toString() {
            return "[intent=" + ultimaIntencion + ", tratamientoPend=" + tratamientoPendiente
                    + ", fechaPend=" + fechaPendiente + ", horaPend=" + horaPendiente
                    + ", ultimoTrat=" + ultimoTratamiento + ", ultFecha=" + ultimaFecha
                    + ", ultHora=" + ultimaHora + ", clienteId=" + clienteId
                    + ", esperandoConfirmacion=" + esperandoConfirmacion + "]";
        }

        public boolean esperandoConfirmacionAgendamiento;

        public boolean tieneDatosCompletosParaAgendar() {
            return clienteId != null && tratamientoPendiente != null && fechaPendiente != null
                    && horaPendiente != null;
        }

    }

    private String normalizarAcentos(String texto) {
        if (texto == null)
            return null;
        return texto.replace("á", "a").replace("à", "a").replace("ä", "a").replace("é", "e")
                .replace("è", "e").replace("ë", "e").replace("í", "i").replace("ì", "i")
                .replace("ï", "i").replace("ó", "o").replace("ò", "o").replace("ö", "o")
                .replace("ú", "u").replace("ù", "u").replace("ü", "u").replace("ñ", "n")
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.forLanguageTag("es-PY"));
    }

    // ===== Helpers de fecha avanzada =====

    private static final Map<String, DayOfWeek> ES_DIA = Map.ofEntries(
            Map.entry("lunes", DayOfWeek.MONDAY), Map.entry("martes", DayOfWeek.TUESDAY),
            Map.entry("miercoles", DayOfWeek.WEDNESDAY),
            Map.entry("miércoles", DayOfWeek.WEDNESDAY), Map.entry("jueves", DayOfWeek.THURSDAY),
            Map.entry("viernes", DayOfWeek.FRIDAY), Map.entry("sabado", DayOfWeek.SATURDAY),
            Map.entry("sábado", DayOfWeek.SATURDAY), Map.entry("domingo", DayOfWeek.SUNDAY));

    private static final Map<String, Integer> ES_MES = Map.ofEntries(Map.entry("enero", 1),
            Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
            Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7),
            Map.entry("agosto", 8), Map.entry("septiembre", 9), Map.entry("setiembre", 9),
            Map.entry("octubre", 10), Map.entry("noviembre", 11), Map.entry("diciembre", 12));

    private static final Map<String, Integer> ES_ORDINAL = Map.of("primer", 1, "primero", 1,
            "segundo", 2, "tercer", 3, "tercero", 3, "cuarto", 4, "quinto", 5);

    /** "primer viernes de noviembre" → LocalDate (si ya pasó este año, lo mueve al próximo). */
    private LocalDate parseNthWeekdayOfMonth(String texto, LocalDate hoy) {
        if (texto == null)
            return null;
        String t = normalizarAcentos(texto);
        var p = java.util.regex.Pattern.compile(
                "\\b(primer|primero|segundo|tercer|tercero|cuarto|quinto)\\s+(lunes|martes|miercoles|miércoles|jueves|viernes|sabado|sábado|domingo)\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\b");
        var m = p.matcher(t);
        if (!m.find())
            return null;

        int ordinal = ES_ORDINAL.getOrDefault(m.group(1), 0);
        DayOfWeek dow = ES_DIA.get(m.group(2));
        Integer mes = ES_MES.get(m.group(3));
        if (ordinal < 1 || dow == null || mes == null)
            return null;

        int year = hoy.getYear();
        LocalDate base = LocalDate.of(year, mes, 1);
        int shift = (dow.getValue() - base.getDayOfWeek().getValue() + 7) % 7;
        LocalDate fecha = base.plusDays(shift).plusWeeks(ordinal - 1);

        // si ya pasó este año y el texto no dice explícitamente "del año que viene", pásalo al
        // próximo año
        if (fecha.isBefore(hoy)) {
            fecha = fecha.plusYears(1);
        }
        return fecha;
    }

    /** Fallback vía IA: pide una fecha ISO yyyy-MM-dd (sin texto extra). */
    private LocalDate resolverFechaConIA(String texto) {
        if (texto == null || texto.isBlank())
            return null;
        try {
            String hoy = LocalDate.now().toString();
            String prompt =
                    """
                            Dada esta frase de fecha en español y la fecha de hoy, responde SOLO la fecha resultante en formato ISO yyyy-MM-dd, sin palabras extra.
                            Hoy es: %s
                            Frase: "%s"
                            """
                            .formatted(hoy, texto);

            // bajar la temperatura para ser determinista
            String out = llm.generateResponse(prompt, "es-PY", 0.2);
            if (out == null)
                return null;

            var m = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(out);
            if (m.find()) {
                return LocalDate.parse(m.group(1));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ========= Helpers de intención contextual (sin pasar por LLM) =========
    private boolean esAfirmacion(String lower) {
        return lower.matches(".*\\b(s[ií]|sí|si|dale|ok|okay|de una|perfecto|confirmo)\\b.*");
    }

    private boolean esNegacion(String lower) {
        return lower.matches(".*\\b(no|nop|negativo|mejor no)\\b.*");
    }

    // Detecta fecha/hora en un texto libre para “corrección en la misma frase”
    private static record Correccion(LocalDate fecha, LocalTime hora) {
    }

    private Correccion extraerCorreccionFechaHora(String lower) {
        // 1) intenta fecha+hora con tu parser mixto
        var mix = FechaHoraRelativaUtil.parse(lower);
        LocalDate f = (mix != null) ? mix.fechaInicio() : FechaRelativaUtil.parseFecha(lower);

        LocalTime h = null;
        if (mix != null && mix.hora() != null) {
            h = parseHoraFlexible(mix.hora());
        }
        if (h == null) {
            // busca “2pm”, “2 pm”, “14”, “8hs”, “a las 15”, “13:30” etc.
            h = parseHoraFlexible(lower);
        }

        if (f == null && h == null)
            return null;
        return new Correccion(f, h);
    }


}
