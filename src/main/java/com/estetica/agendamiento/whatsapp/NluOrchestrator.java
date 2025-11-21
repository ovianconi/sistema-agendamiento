package com.estetica.agendamiento.whatsapp;

import com.estetica.agendamiento.ai.IntentResult;
import com.estetica.agendamiento.ai.LlmClient;
import com.estetica.agendamiento.dto.ClienteResponseDTO;
import com.estetica.agendamiento.dto.SesionResponseDTO;
import com.estetica.agendamiento.dto.SesionesRestantesDTO;
import com.estetica.agendamiento.dto.WhatsappMessageDTO;
import com.estetica.agendamiento.util.FechaHoraRelativaUtil;
import com.estetica.agendamiento.util.FechaNaturalService;
import com.estetica.agendamiento.util.FechaRelativaUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;

@Component
public class NluOrchestrator {

    private final LlmClient llm;
    private final RestTemplate http = new RestTemplate();
    private final FechaNaturalService fechaNaturalService;

    @Value("${whatsapp.access-token}")
    private String whatsappToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${app.base-url}")
    private String baseUrl;

    // Contexto conversacional por teléfono
    private static final Map<String, ConversacionContexto> contextos = new ConcurrentHashMap<>();

    // cada 10 minutos elimina contextos inactivos (sin actividad en los últimos 15
    // minutos)
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
    public NluOrchestrator(@Qualifier("openAiLlmClient") LlmClient llm,
            FechaNaturalService fechaNaturalService // ← NUEVO
    ) {
        this.llm = llm;
        this.fechaNaturalService = fechaNaturalService;
    }

    // ===============================
    // PROCESAMIENTO PRINCIPAL FINAL
    // ===============================
    public void processIncomingMessage(WhatsappMessageDTO msg) {
        try {
            final String telefono = msg.getTelefono();
            final String textoOriginal = msg.getTexto();
            System.out.println("💬 Mensaje recibido: " + textoOriginal);
            System.out.println("📱 Telefono: " + telefono);

            // Normalizar texto
            final String lower = normalizarAcentos(textoOriginal);
            ConversacionContexto ctx = contextos.computeIfAbsent(telefono, t -> new ConversacionContexto());
            ctx.ultimoAcceso = Instant.now();

            System.out.println("🧹 Texto normalizado: " + lower);
            System.out.println("🧩 Contexto actual al iniciar: " + ctx);

            // ==========================================================
            // 🔁 AJUSTE DE HORARIO POST-CONFLICTO (no había personal)
            // ==========================================================
            // Caso: venimos de "no hay personal", marcamos ctx.ajustandoHorario = true
            // y el usuario responde "sí", "a las 9", "mejor 9:30", "21", etc.
            // 👉 No hay que volver a pedir tratamiento ni fecha; SOLO actualizamos la hora
            // y reintentamos crear la sesión con (clienteId, tratamientoPendiente,
            // fechaPendiente, horaNueva).
            if (ctx.ajustandoHorario
                    && "agendar_sesion".equals(ctx.ultimaIntencion)
                    && ctx.clienteId != null
                    && ctx.tratamientoPendiente != null
                    && ctx.fechaPendiente != null) {

                System.out.println("ENTROOOOOOOOOOOO: 1  [AJUSTE POST-CONFLICTO]");

                // 1) Intentar extraer nueva HORA del texto actual
                LocalTime nuevaHora = null;

                // a) Parser natural robusto (soporta "a las 9", "9", "9:30", "3pm", etc.)
                var parsedHora = fechaNaturalService.parse(lower); // usa tu FechaNaturalService
                if (parsedHora != null && parsedHora.hora() != null) {
                    nuevaHora = parsedHora.hora();
                    System.out.println("ENTROOOOOOOOOOOO: 1.1  hora (FechaNaturalService) = " + nuevaHora);
                }

                // b) Fallback si no detectó con el servicio natural, usamos tu helper flexible
                if (nuevaHora == null) {
                    nuevaHora = parseHoraFlexible(textoOriginal);
                    System.out.println("ENTROOOOOOOOOOOO: 1.2  hora (parseHoraFlexible) = " + nuevaHora);
                }

                // c) Si detectamos nueva hora, actualizamos; si no, mantenemos la anterior.
                if (nuevaHora != null) {
                    ctx.horaPendiente = nuevaHora;
                }

                // 2. En este punto DEBEMOS tener:
                // - ctx.tratamientoPendiente
                // - ctx.fechaPendiente
                // - ctx.horaPendiente (la original o la nueva)
                // - ctx.clienteId
                if (ctx.clienteId != null
                        && ctx.tratamientoPendiente != null
                        && ctx.fechaPendiente != null
                        && ctx.horaPendiente != null) {

                    System.out.println("💡 [AJUSTE HORARIO] Reintento con:");
                    System.out.println("   tratamiento=" + ctx.tratamientoPendiente);
                    System.out.println("   fecha=" + ctx.fechaPendiente);
                    System.out.println("   hora=" + ctx.horaPendiente);

                    // reintentamos agendar DIRECTO, sin volver a preguntar tratamiento/fecha
                    RespuestaOp res = crearSesionDesdeIA_R(
                            ctx.clienteId,
                            ctx.tratamientoPendiente,
                            ctx.fechaPendiente.toString(),
                            ctx.horaPendiente.toString());

                    // ¿Sigue sin haber personal?
                    if (res.conflictoCapacidad) {
                        System.out.println("💡 [AJUSTE HORARIO] Sigue sin personal. Mantengo ajustandoHorario=true");
                        // seguimos en modo ajuste, NO limpiamos nada
                        contextos.put(telefono, ctx);

                        sendWhatsappMessage(telefono,
                                "A esa hora tampoco tenemos personal disponible 😕. " +
                                        "Probemos otra hora para *" + ctx.tratamientoPendiente + "* el *" +
                                        ctx.fechaPendiente + "*. Decime solo la hora.");
                        return;
                    }

                    // Si LLEGAMOS ACÁ, o bien:
                    // - se pudo agendar directo
                    // - o el backend devolvió un mensaje de confirmación tipo
                    // "Te agendé..." / "Quedó agendado..." (tu patrón res.texto)

                    // Ya no estamos ajustando, ya tomamos una decisión
                    ctx.ajustandoHorario = false;
                    ctx.esperandoConfirmacion = false;
                    ctx.esperandoConfirmacionAgendamiento = false;

                    // Si realmente se agendó, guardamos en memoria histórica y limpiamos
                    String resLower = res.texto.toLowerCase();
                    if (resLower.contains("agendé")
                            || resLower.contains("agende")
                            || resLower.contains("agendada")
                            || resLower.contains("agendado")) {

                        ctx.ultimoTratamiento = ctx.tratamientoPendiente;
                        ctx.ultimaFecha = ctx.fechaPendiente;
                        ctx.ultimaHora = ctx.horaPendiente;

                        ctx.tratamientoPendiente = null;
                        ctx.fechaPendiente = null;
                        ctx.horaPendiente = null;
                    } else {
                        // Si no se agendó aún (por ejemplo si el backend responde con
                        // "Me confirmás... ?"), entonces seguimos esperando confirmación normal
                        ctx.esperandoConfirmacionAgendamiento = true;
                    }

                    contextos.put(telefono, ctx);
                    sendWhatsappMessage(telefono, res.texto);
                    return;
                }

                // Si por algún motivo NO tenemos fecha u hora todavía, pedimos lo que falta
                // pero IMPORTANTÍSIMO: NO preguntamos el tratamiento otra vez.
                if (ctx.horaPendiente == null) {
                    contextos.put(telefono, ctx);
                    sendWhatsappMessage(telefono,
                            "¿A qué hora querés el mismo turno ese día? (por ejemplo 'a las 9')");
                    return;
                }
                if (ctx.fechaPendiente == null) {
                    contextos.put(telefono, ctx);
                    sendWhatsappMessage(telefono,
                            "¿Para qué día querés? (por ejemplo 'el lunes')");
                    return;
                }
            }

            // ==========================================================
            // 0️⃣ ATAJOS CONTEXTUALES (confirmaciones, correcciones, etc.)
            // ==========================================================
            if ("agendar_sesion".equals(ctx.ultimaIntencion) && ctx.tratamientoPendiente != null) {

                // 0.1 Confirmación directa
                if (esAfirmacion(lower)) {
                    System.out.println("[CTX] Confirmación afirmativa detectada (sin LLM).");
                    System.out.println("ENTROOOOOOOOOOOO: 2.1");

                    if (ctx.fechaPendiente == null || ctx.horaPendiente == null) {
                        System.out.println("ENTROOOOOOOOOOOO: 3");
                        pedirFaltantes(telefono, ctx);
                        contextos.put(telefono, ctx);
                        return;
                    }

                    System.out.println("🧩 Contexto ANTES IA: " + ctx);
                    RespuestaOp res;
                    try {
                        res = crearSesionDesdeIA_R(
                                ctx.clienteId,
                                ctx.tratamientoPendiente,
                                ctx.fechaPendiente.toString(),
                                ctx.horaPendiente.toString());
                        System.out.println("🧩 Antes de Explotar en IA: " + ctx.ajustandoHorario);
                    } catch (RuntimeException ex) {
                        System.out.println("🧩 Explota en IA: " + ctx.ajustandoHorario);
                        if (ex.getMessage().contains("No hay personal disponible")) {
                            System.out.println("⚠️ [CTX] Capturado conflicto de recursos");
                            ctx.ajustandoHorario = true;
                            ctx.esperandoConfirmacion = false;
                            ctx.esperandoConfirmacionAgendamiento = false;
                            contextos.put(telefono, ctx);
                            sendWhatsappMessage(telefono,
                                    "En ese horario no hay personal disponible 😕. " +
                                            "¿Querés que probemos otra hora? Decime solo la hora, por ejemplo: *a las 9*.");
                            return;
                        }
                        System.out.println("🧩 Explota en IA otra cosa: " + ctx.ajustandoHorario);
                        throw ex; // otros errores reales
                    }
                    System.out.println("🧩 Contexto DESPUES IA: " + ctx);
                    System.out.println("🧩 CONFLICTOOOOOOOOO: " + res.conflictoCapacidad);
                    System.out.println("🧩 AjusteHorario despues que Explota en IA: " + ctx.ajustandoHorario);

                    // ⛔️ CASO: SIN PERSONAL → entrar en modo "ajuste de horario" y cortar acá
                    if (res.conflictoCapacidad) {
                        System.out.println("ENTROOOOOOOOOOOO: 4");
                        System.out.println("[CTX] No hay personal disponible. Entrando en modo ajuste de horario.");

                        // NO limpiar tratamiento/fecha/hora; solo marcar flags:
                        ctx.ajustandoHorario = true;
                        ctx.esperandoConfirmacion = false;
                        ctx.esperandoConfirmacionAgendamiento = false;

                        // Persistir ANTES de responder
                        contextos.put(telefono, ctx);

                        sendWhatsappMessage(telefono,
                                "En ese horario no tenemos personal disponible 😕. " +
                                        "¿Querés que probemos otro horario para *" + ctx.tratamientoPendiente + "* el *"
                                        +
                                        ctx.fechaPendiente + "*? Decime solo la hora.");
                        return; // ← IMPORTANTÍSIMO: no seguir con ningún flujo debajo
                    }

                    // ✅ Se pudo agendar (o ya quedó listo para confirmar)
                    System.out.println("ENTROOOOOOOOOOOO: 4.1");
                    ctx.esperandoConfirmacion = false;
                    ctx.esperandoConfirmacionAgendamiento = false;
                    ctx.ajustandoHorario = false;

                    String resLower = res.texto.toLowerCase();
                    if (resLower.contains("agendé") || resLower.contains("agende")
                            || resLower.contains("agendada") || resLower.contains("agendado")) {
                        System.out.println("ENTROOOOOOOOOOOO: 4.2");

                        // memoria histórica
                        ctx.ultimoTratamiento = ctx.tratamientoPendiente;
                        ctx.ultimaFecha = ctx.fechaPendiente;
                        ctx.ultimaHora = ctx.horaPendiente;

                        // limpiar porque ya quedó
                        ctx.tratamientoPendiente = null;
                        ctx.fechaPendiente = null;
                        ctx.horaPendiente = null;
                    } else {
                        // Si el backend devolvió un "¿confirmo?" mantené confirmación pendiente
                        ctx.esperandoConfirmacionAgendamiento = true;
                    }

                    contextos.put(telefono, ctx);
                    sendWhatsappMessage(telefono, res.texto);
                    return;
                }

                // 0.2 Corrección tipo “no, mejor el jueves a las 13”
                if (esNegacion(lower) || lower.contains("mejor") || lower.contains("cambi")) {
                    System.out.println("ENTROOOOOOOOOOOO: 5");
                    var corr = extraerCorreccionFechaHora(lower);
                    if (corr != null) {
                        System.out.println("ENTROOOOOOOOOOOO: 6");
                        if (corr.fecha() != null)
                            ctx.fechaPendiente = corr.fecha();
                        if (corr.hora() != null)
                            ctx.horaPendiente = corr.hora();

                        String fStr = (ctx.fechaPendiente != null)
                                ? ctx.fechaPendiente.toString()
                                : "¿qué día?";
                        String hStr = (ctx.horaPendiente != null)
                                ? ctx.horaPendiente.toString().substring(0, 5)
                                : "¿qué hora?";

                        sendWhatsappMessage(telefono, "Perfecto 👍. Entonces sería *"
                                + ctx.tratamientoPendiente + "* el *" + fStr + "* a las *" + hStr
                                + "*, ¿confirmo?");
                        ctx.esperandoConfirmacionAgendamiento = true;
                        contextos.put(telefono, ctx);
                        return;
                    }

                    sendWhatsappMessage(telefono,
                            "Genial 😊. Decime qué querés cambiar — la *fecha*, la *hora* o el *tratamiento*.");
                    ctx.esperandoConfirmacionAgendamiento = true;
                    contextos.put(telefono, ctx);
                    return;
                }
                // 0.3 Mensaje puramente temporal en medio del flujo (ej: “el primer domingo de
                // noviembre”)
                if (esTemporal(lower)) {
                    System.out.println("ENTROOOOOOOOOOOO: 7");
                    var p = fechaNaturalService.parse(lower);
                    if (p != null) {
                        System.out.println("ENTROOOOOOOOOOOO: 8");
                        System.out.println("ENTROOOOOOOOOOOO: fecha natural " + p.fecha());
                        System.out.println("ENTROOOOOOOOOOOO: hora natural " + p.hora());
                        if (p.fecha() != null)
                            ctx.fechaPendiente = p.fecha();
                        if (p.hora() != null)
                            ctx.horaPendiente = p.hora();
                        if (ctx.fechaPendiente == null || ctx.horaPendiente == null) {
                            pedirFaltantes(telefono, ctx);
                            contextos.put(telefono, ctx);
                            return;
                        }
                        sendWhatsappMessage(telefono,
                                "Entonces sería *" + ctx.tratamientoPendiente + "* el *"
                                        + ctx.fechaPendiente + "* a las *"
                                        + ctx.horaPendiente.toString().substring(0, 5)
                                        + "*, ¿confirmo?");
                        ctx.esperandoConfirmacionAgendamiento = true;
                        ctx.ajustandoHorario = false; // <<< NUEVO
                        contextos.put(telefono, ctx);
                        return;
                    }
                }
            }
            // ==========================================================
            // 1) PARSER LOCAL PRE-LMM (si la frase parece solo temporal)
            // ==========================================================
            if (esTemporal(lower)) {
                System.out.println("ENTROOOOOOOOOOOO: 9");
                var p = fechaNaturalService.parse(lower);
                if (p != null && p.fecha() != null) {
                    System.out.println("[PARSER] Fecha detectada localmente → " + p.fecha());
                    // si había intención previa de agendar, seguimos
                    if ("agendar_sesion".equals(ctx.ultimaIntencion)
                            && ctx.tratamientoPendiente != null) {
                        System.out.println("ENTROOOOOOOOOOOO: 9.1");
                        if (p.fecha() != null) {
                            System.out.println("ENTROOOOOOOOOOOO: 9.2");
                            ctx.fechaPendiente = p.fecha();
                        }
                        if (p.hora() != null) {
                            System.out.println("ENTROOOOOOOOOOOO: 9.3");
                            ctx.horaPendiente = p.hora();
                        }
                        if (!ctx.tieneDatosCompletosParaAgendar()) {
                            System.out.println("ENTROOOOOOOOOOOO: 9.4");
                            pedirFaltantes(telefono, ctx);
                            contextos.put(telefono, ctx);
                            return;
                        }
                        sendWhatsappMessage(telefono,
                                "Perfecto 👍. Entonces sería *" + ctx.tratamientoPendiente
                                        + "* el *" + ctx.fechaPendiente + "* a las *"
                                        + ctx.horaPendiente.toString().substring(0, 5)
                                        + "*, ¿confirmo?");
                        ctx.esperandoConfirmacionAgendamiento = true;
                        ctx.ajustandoHorario = false; // <<< NUEVO
                        contextos.put(telefono, ctx);
                        return;
                    }
                }
            }

            // ==========================================================
            // 🧠 LLM
            // ==========================================================
            IntentResult ir = llm.extractIntent(textoOriginal, "es-PY");
            System.out.println("🧠 Intent detectado: " + ir);

            // Si estamos ajustando horario → no analizar otra intención
            if (ctx.ajustandoHorario) {
                System.out.println("🛑 Evitando override del LLM: estamos ajustando horario");
                return;
            }

            // ==========================================================
            // Identificación de cliente (solo teléfono o documento)
            // ==========================================================
            ClienteResponseDTO cliente = identificarCliente(msg, ir, ctx);

            // ✅ Si encontramos cliente y no estaba en contexto
            if (cliente != null && ctx.clienteId == null) {
                ctx.clienteId = cliente.getId();
                contextos.put(telefono, ctx);
            }

            // 🚨 Si NO se encontró cliente por teléfono/documento y tampoco teníamos uno en
            // contexto
            if (cliente == null && ctx.clienteId == null) {
                // Guardamos TODO lo que el usuario ya dijo antes de pedir documento
                ctx.ultimaIntencionPendiente = ir.getIntent(); // ej. agendar_sesion
                ctx.tratPendienteConsulta = ir.getTratamiento();

                // 🔹 Guardar también la fecha y hora ya detectadas (si las había)
                if (ir.getFecha() != null) {
                    var parsed = fechaNaturalService.parse(ir.getFecha());
                    if (parsed != null && parsed.fecha() != null)
                        ctx.fechaPendiente = parsed.fecha();
                }
                if (ir.getHora() != null) {
                    var parsed = fechaNaturalService.parse(ir.getHora());
                    if (parsed != null && parsed.hora() != null)
                        ctx.horaPendiente = parsed.hora();
                }

                contextos.put(telefono, ctx);
                sendWhatsappMessage(telefono, "No pude identificarte todavía. ¿Podés decirme tu documento?");
                return;
            }

            // ✅ Si todavía no tenemos cliente en memoria pero ya lo encontramos por
            // documento
            if (cliente != null && ctx.clienteId == null) {
                ctx.clienteId = cliente.getId();
                contextos.put(telefono, ctx);
            }

            // ✅ Si NO tenemos cliente cargado en variable pero sí en contexto → leer de
            // backend
            if (cliente == null && ctx.clienteId != null) {
                try {
                    cliente = http.getForObject(
                            baseUrl + "/api/clientes/" + ctx.clienteId,
                            ClienteResponseDTO.class);
                } catch (Exception e) {
                    System.err.println("❌ Error releyendo cliente por id: " + e.getMessage());
                }
            }

            // 🔹 Asignamos cliente al mensaje y al contexto
            if (cliente != null) {
                msg.setCliente(cliente);
                ctx.clienteId = cliente.getId();
                System.out.println("✅ Cliente identificado: " + cliente.getNombre() + " (ID " + cliente.getId() + ")");
            } else {
                // si aún no hay cliente, salimos
                sendWhatsappMessage(telefono, "Necesito identificarte antes de continuar 😊. Decime tu documento.");
                return;
            }
            msg.setCliente(cliente);
            ctx.clienteId = cliente.getId();

            // ==========================================================
            // INTENCIÓN
            // ==========================================================
            // Si había intención pendiente → la retomamos
            if (ctx.ultimaIntencionPendiente != null && ctx.clienteId != null) {
                System.out.println("ENTROOOOOOOOOOOO: 10");
                System.out.println("🔁 Reprocesando intención pendiente: "
                        + ctx.ultimaIntencionPendiente);
                IntentResult irPend = new IntentResult();
                irPend.setIntent(ctx.ultimaIntencionPendiente);
                irPend.setTratamiento(ctx.tratPendienteConsulta);
                irPend.setFecha(ir.getFecha());
                irPend.setHora(ir.getHora());

                // ✅ Recuperar también fecha/hora que estaban pendientes antes de pedir
                // documento
                if (ctx.fechaPendiente != null)
                    irPend.setFecha(ctx.fechaPendiente.toString());
                if (ctx.horaPendiente != null)
                    irPend.setHora(ctx.horaPendiente.toString());

                // Limpiamos banderas para no repetir
                ctx.ultimaIntencionPendiente = null;
                ctx.tratPendienteConsulta = null;
                contextos.put(telefono, ctx);

                // Sustituimos el intent actual por el pendiente
                ir = irPend;
            }
            String intent = (ir.getIntent() != null) ? ir.getIntent() : "";
            switch (intent) {
                case "agendar_sesion" -> {
                    System.out.println("ENTROOOOOOOOOOOO: 11");

                    // 👇 Si ya estábamos ajustando horario, NO toques nada acá,
                    // porque ese caso debió salir antes en el bloque de arriba
                    if (ctx.ajustandoHorario) {
                        System.out.println("ENTROOOOOOOOOOOO: 12");
                        // seguridad defensiva: esto no debería pasar porque el bloque AJUSTE retorna
                        // antes,
                        // pero por si acaso:
                        contextos.put(telefono, ctx);
                        sendWhatsappMessage(telefono,
                                "Sigamos buscando otro horario para *" + ctx.tratamientoPendiente +
                                        "* el *" + ctx.fechaPendiente + "*. Decime otra hora libre que te sirva 😊.");
                        return;
                    }

                    ctx.ultimaIntencion = "agendar_sesion";
                    ctx.tratamientoPendiente = firstNonNull(
                            safeTrimOrNull(ir.getTratamiento()),
                            ctx.tratamientoPendiente, ctx.ultimoTratamiento);
                    ctx.clienteId = cliente.getId();
                    System.out.println("ENTROOOOOOOOOOOO: 12.1. " + ctx.tratamientoPendiente);
                    var parsed = fechaNaturalService.parse(
                            (ir.getFecha() != null ? ir.getFecha() : "")
                                    + " " + (ir.getHora() != null ? ir.getHora() : ""));
                    if (parsed != null) {
                        System.out.println("ENTROOOOOOOOOOOO: 13");
                        if (parsed.fecha() != null) {
                            System.out.println("ENTROOOOOOOOOOOO: 14. " + parsed.fecha());
                            ctx.fechaPendiente = parsed.fecha();
                        }
                        if (parsed.hora() != null) {
                            System.out.println("ENTROOOOOOOOOOOO: 15. " + parsed.hora());
                            ctx.horaPendiente = parsed.hora();
                        }
                    }

                    if (!ctx.tieneDatosCompletosParaAgendar()) {
                        pedirFaltantes(telefono, ctx);
                        System.out.println("ENTROOOOOOOOOOOO: 16");
                        // esto DEBE quedar en false acá porque es la captura normal
                        ctx.ajustandoHorario = false;

                        contextos.put(telefono, ctx);
                        return;
                    }

                    // ya tengo todo → pedir confirmación
                    ctx.esperandoConfirmacionAgendamiento = true;
                    ctx.ajustandoHorario = false; // todavía no falló capacidad
                    contextos.put(telefono, ctx);
                    sendWhatsappMessage(telefono,
                            String.format(
                                    "Me confirmás, ¿querés agendar *%s* el *%s* a las *%s*?",
                                    ctx.tratamientoPendiente,
                                    ctx.fechaPendiente,
                                    ctx.horaPendiente.toString().substring(0, 5)));
                    return;
                }

                case "consultar_sesiones_restantes" -> {
                    String respuesta = consultarSesionesRestantes(
                            cliente.getId(), ir.getTratamiento());
                    sendWhatsappMessage(telefono, respuesta);
                    return;
                }

                case "cancelar_sesion", "cancelar_por_tratamiento_fecha",
                        "cancelar_por_fecha_hora" -> {
                    String respuesta = cancelarSesionFlexible(
                            cliente.getId(), ir.getTratamiento(),
                            ir.getFecha(), ir.getHora());
                    sendWhatsappMessage(telefono, respuesta);
                    return;
                }

                case "saludo" -> {
                    sendWhatsappMessage(telefono,
                            "¡Hola " + cliente.getNombre()
                                    + "! Soy el asistente de la clínica 😊. ¿Querés consultar tus sesiones, cancelar o agendar una nueva?");
                    return;
                }

                default -> {
                    sendWhatsappMessage(telefono,
                            "No estoy seguro de cómo ayudarte con eso todavía 🤔.");
                    return;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendWhatsappMessage(msg.getTelefono(),
                    "Lo siento, hubo un error al procesar tu mensaje 😔. Intentá de nuevo más tarde.");
        }
    }

    // ===============================================================
    // Identificación de cliente simplificada: teléfono → documento
    // ===============================================================
    private ClienteResponseDTO identificarCliente(WhatsappMessageDTO msg, IntentResult ir, ConversacionContexto ctx) {

        final String telefonoCrudo = msg.getTelefono();
        final String normalizado = normalizePhone(telefonoCrudo);
        final String posibleDocumento = extraerDocumento(msg.getTexto());

        System.out.println("? identificarCliente()");
        System.out.println("   ? telefonoCrudo  = " + telefonoCrudo);
        System.out.println("   ? telefonoNorm   = " + normalizado);
        System.out.println("   ? posibleDocumento = " + posibleDocumento);

        // 1) si ya tenemos cliente en contexto, devolvemos ese
        if (ctx != null && ctx.clienteId != null) {
            try {
                String url = baseUrl + "/api/clientes/" + ctx.clienteId;
                System.out.println("   ? Ya había cliente en contexto. GET " + url);
                return http.getForObject(url, ClienteResponseDTO.class);
            } catch (Exception e) {
                System.err.println("   ?? Error releyendo cliente por contexto: " + e.getMessage());
            }
        }

        // 2) intentar por teléfono normalizado
        try {
            String urlTel = baseUrl + "/api/clientes/by-telefono/" + normalizado;
            System.out.println("   ? GET telefono: " + urlTel);
            ClienteResponseDTO c = http.getForObject(urlTel, ClienteResponseDTO.class);
            if (c != null) {
                System.out.println("   ✅ Cliente encontrado por teléfono: id=" + c.getId());
                return c;
            }
        } catch (Exception e) {
            System.err.println("   ?? Error por teléfono: " + e.getMessage());
        }

        // 3) intentar por documento SOLO si el mensaje actual parece ser un documento
        // (solo dígitos, >=5 chars)
        if (posibleDocumento != null) {
            try {
                String urlDoc = baseUrl + "/api/clientes/by-documento/" + posibleDocumento;
                System.out.println("   ? GET documento: " + urlDoc);
                ClienteResponseDTO c = http.getForObject(urlDoc, ClienteResponseDTO.class);
                if (c != null) {
                    System.out.println("   ✅ Cliente encontrado por documento: id=" + c.getId());
                    return c;
                }
            } catch (Exception e) {
                System.err.println("   ?? Error por documento: " + e.getMessage());
            }
        }

        System.out.println("   ? No se pudo identificar al cliente.");
        return null;
    }

    // encode helper para el nombre en la URL (espacios, acentos)
    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    // ---------------------------------------------------------------
    // Helpers para sacar documento / nombre del texto libre del usuario
    // ---------------------------------------------------------------

    private String extraerDocumento(String texto) {
        // documento tipo 3395069, 1234567, etc. (>=5 dígitos)
        var m = java.util.regex.Pattern.compile("\\b\\d{5,}\\b").matcher(texto);
        return m.find() ? m.group() : null;
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
            String url = baseUrl + "/api/clientes/%d/tratamientos/%d/sesiones-restantes"
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
            if (fechaTexto == null || fechaTexto.isBlank()) {
                return "¿Podés decirme de qué día querés cancelar la sesión?";
            }

            // 1️⃣ Parsear fecha con los utilitarios + fallback IA
            var parsed = FechaHoraRelativaUtil.parse(((fechaTexto != null) ? fechaTexto : "") + " "
                    + ((horaTexto != null) ? horaTexto : ""));
            LocalDate fecha = (parsed != null) ? parsed.fechaInicio()
                    : FechaRelativaUtil.parseFecha(fechaTexto);
            String horaStr = (parsed != null && parsed.hora() != null) ? parsed.hora() : horaTexto;

            if (fecha == null) {
                // Fallback IA solo para FECHA
                fecha = resolverFechaConIA(((fechaTexto != null) ? fechaTexto : ""));
                if (fecha == null) {
                    return "No entendí la fecha. ¿Podés indicarme para qué día exacto querés cancelar?";
                }
            }

            // 2️⃣ Si NO hay hora: tratemos de identificar sesión única del día
            if (horaStr == null || horaStr.isBlank()) {
                // Endpoint: /api/sesiones/cliente/{clienteId}/por-fecha/{fecha} (tu endpoint
                // nuevo)
                SesionResponseDTO[] sesiones = http.getForObject(
                        baseUrl + "/api/sesiones/cliente/{cid}/por-fecha/{f}",
                        SesionResponseDTO[].class, clienteId, fecha.toString());

                if (sesiones == null || sesiones.length == 0) {
                    return "No encontré ninguna sesión agendada para ese día.";
                }
                if (sesiones.length > 1) {
                    return "Ese día tenés más de una sesión. ¿Podés decirme la hora (y si querés también el tratamiento) de la que querés cancelar?";
                }

                // Única sesión → usamos su hora real
                horaStr = sesiones[0].getHoraInicio().toString();
            }

            // 3️⃣ Construir la URL según tenga tratamiento o no
            String horaNorm = normalizarHora(horaStr);
            if (tratamiento != null && !tratamiento.isBlank()) {
                Long tratId = buscarTratamientoIdPorNombre(tratamiento);
                if (tratId == null)
                    return "No identifiqué el tratamiento. ¿Podés repetir el nombre?";

                String url = baseUrl + "/api/clientes/%d/tratamientos/%d/sesiones/%s/cancelar"
                        .formatted(clienteId, tratId, fecha);
                http.exchange(url, HttpMethod.PUT, null, String.class);

                return "Tu sesión de " + tratamiento + " del " + fecha + " a las " + horaNorm
                        + " fue cancelada correctamente.";
            } else {
                String url = baseUrl + "/api/clientes/%d/sesiones/%s/%s/cancelar"
                        .formatted(clienteId, fecha, horaNorm);
                http.exchange(url, HttpMethod.PUT, null, String.class);

                return "Tu sesión del " + fecha + " a las " + horaNorm
                        + " fue cancelada correctamente.";
            }

        } catch (HttpClientErrorException e) {
            return extraerMensajeError(e.getResponseBodyAsString());
        } catch (Exception e) {
            return extraerMensajeError(e.getMessage());
        }
    }

    // ===============================================================
    // Agendamiento (versión híbrida optimizada, con detección de conflicto de
    // capacidad)
    // ===============================================================
    private RespuestaOp crearSesionDesdeIA_R(Long clienteId, String tratamiento, String fechaTexto,
            String horaTexto) {
        System.out.println("ENTROOOOOOOOOOOO: IA");
        try {
            Long tratamientoId = buscarTratamientoIdPorNombre(tratamiento);
            if (tratamientoId == null) {
                return new RespuestaOp("No identifiqué el tratamiento. ¿Podés repetir el nombre?",
                        false);
            }

            LocalTime hora = null;

            System.out.println("─────────────── 🧩 [DEBUG crearSesionDesdeIA_R] ────────────────");
            System.out.println(
                    "🧾 Texto recibido → fecha='" + fechaTexto + "', hora='" + horaTexto + "'");

            // 1️⃣ Parser de expresiones ordinales: “primer domingo de noviembre”
            LocalDate fecha = parseNthWeekdayOfMonth((fechaTexto != null ? fechaTexto : "") + " "
                    + (horaTexto != null ? horaTexto : ""), LocalDate.now());
            if (fecha != null)
                System.out.println("⏰ [FechaRelativa] Reconocido ordinal/mes → " + fecha);

            // 2️⃣ Parser mixto local (fecha + hora en la misma frase)
            var parsed = FechaHoraRelativaUtil.parse((fechaTexto != null ? fechaTexto : "") + " "
                    + (horaTexto != null ? horaTexto : ""));
            if (parsed != null && parsed.fechaInicio() != null && fecha == null) {
                fecha = parsed.fechaInicio();
                System.out.println("⏰ [FechaHoraRelativaUtil] Fecha detectada → " + fecha);
            }

            if (parsed != null && parsed.hora() != null) {
                hora = parseHoraFlexible(parsed.hora());
                System.out.println("🕓 [FechaHoraRelativaUtil] Hora detectada → " + hora);
            }

            // 3️⃣ Parser básico de fechas relativas simples (“mañana”, “el viernes”)
            if (fecha == null) {
                LocalDate tmp = FechaRelativaUtil.parseFecha(fechaTexto);
                if (tmp != null) {
                    fecha = tmp;
                    System.out.println("⏰ [FechaRelativaUtil] Fecha detectada → " + fecha);
                }
            }

            // 4️⃣ Fallback IA si seguimos sin fecha
            if (fecha == null) {
                System.out.println("🤖 [IA] Activando fallback IA para resolver fecha...");
                fecha = resolverFechaConIA((fechaTexto != null ? fechaTexto : ""));
                System.out.println("🤖 [IA] Fecha devuelta por IA → " + fecha);
            }

            // 5️⃣ Intentar deducir hora desde texto libre si no vino del parser
            if (hora == null && horaTexto != null && !horaTexto.isBlank()) {
                hora = parseHoraFlexible(horaTexto);
                System.out.println("🕓 [parseHoraFlexible] Hora detectada → " + hora);
            }

            // 6️⃣ Validación final: no seguimos si falta hora o fecha
            if (fecha == null) {
                return new RespuestaOp(
                        "Necesito la fecha exacta. ¿Para qué día querés agendar?",
                        false);
            }
            if (hora == null) {
                return new RespuestaOp(
                        "Perfecto. ¿A qué hora te viene bien esa sesión? (por ejemplo: 13, 13:30, 1pm)",
                        false);
            }

            System.out.println("📅 Fecha final → " + fecha + " | 🕓 Hora final → " + hora);

            // 7️⃣ Construir body
            var body = Map.of(
                    "clienteId", clienteId,
                    "tratamientoId", tratamientoId,
                    "fecha", fecha.toString(),
                    "horaInicio", hora.toString());

            // 8️⃣ Configurar RestTemplate para lanzar excepción en 400
            RestTemplate tmpHttp = new RestTemplate();
            tmpHttp.setErrorHandler(new DefaultResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) throws IOException {
                    return response.getStatusCode().value() >= 400;
                }
            });

            // 9️⃣ Enviar al backend y manejar respuesta
            try {
                var resp = tmpHttp.postForEntity(baseUrl + "/api/sesiones/dto", body, String.class);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    String horaStr = hora.toString().substring(0, 5);
                    System.out.println("✅ Sesión creada correctamente en backend");
                    return new RespuestaOp("Listo, agendé tu sesión de " + tratamiento + " para el "
                            + fecha + " a las " + horaStr + ".", false);
                }

                // Si no fue 2xx, procesamos cuerpo (por seguridad)
                String texto = extraerMensajeError(resp.getBody());
                return new RespuestaOp(texto, esConflictoCapacidad(texto));

            } catch (HttpClientErrorException e) {
                String bodyError = e.getResponseBodyAsString();
                String texto = extraerMensajeError(bodyError);

                // ✅ NUEVO: si es 400 y menciona falta de personal, marcar conflicto
                if (e.getStatusCode() == HttpStatus.BAD_REQUEST &&
                        bodyError.toLowerCase().contains("no hay personal disponible")) {
                    System.out.println("⚠️ [crearSesionDesdeIA_R] Conflicto de personal detectado (400)");
                    return new RespuestaOp(texto, true);
                }

                return new RespuestaOp(texto, esConflictoCapacidad(texto));
            }

        } catch (Exception e) {
            System.out.println("❌ [crearSesionDesdeIA_R] Excepción general → " + e.getMessage());
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
        if (texto == null)
            return null;

        try {
            String t = texto.toLowerCase(Locale.ROOT).trim();

            // 🔹 Limpieza de expresiones comunes
            t = t.replace("hs", "")
                    .replace("horas", "")
                    .replace("hora", "")
                    .replace("hrs", "")
                    .replace("h", "")
                    .replace("a las", "")
                    .replace("las", "")
                    .replace("la", "")
                    .trim();

            // 🔹 Normalización a formato HH:mm
            if (t.matches("^\\d{1,2}$")) {
                // Solo hora, sin minutos
                return LocalTime.of(Integer.parseInt(t), 0);
            } else if (t.matches("^\\d{1,2}:\\d{1,2}$")) {
                return LocalTime.parse(t);
            } else if (t.matches("^\\d{1,2}\\s?(am|pm)$")) {
                int h = Integer.parseInt(t.replaceAll("\\D+", ""));
                if (t.contains("pm") && h < 12)
                    h += 12;
                if (t.contains("am") && h == 12)
                    h = 0;
                return LocalTime.of(h, 0);
            }

            // 🔹 Intento final: extraer número
            Matcher m = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?").matcher(t);
            if (m.find()) {
                int h = Integer.parseInt(m.group(1));
                int mnt = (m.group(2) != null) ? Integer.parseInt(m.group(2)) : 0;
                if (h >= 0 && h < 24 && mnt >= 0 && mnt < 60)
                    return LocalTime.of(h, mnt);
            }

            System.out.println("?? Error al interpretar hora: " + texto + " → sin coincidencia");
            return null;

        } catch (Exception e) {
            System.out.println("?? Error al interpretar hora: " + texto + " → " + e.getMessage());
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
            String url = baseUrl + "/api/tratamientos/search?nombre=" + nombre;
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

        public String ultimaIntencionPendiente;
        public String tratPendienteConsulta;

        public Long clienteId;
        public boolean esperandoConfirmacion;
        public boolean esperandoConfirmacionAgendamiento;
        public boolean ajustandoHorario; // NUEVO

        public Instant ultimoAcceso = Instant.now();

        // memoria suave
        public String ultimoTratamiento;
        public LocalDate ultimaFecha;
        public LocalTime ultimaHora;

        @Override
        public String toString() {
            return "[intent=" + ultimaIntencion
                    + ", tratamientoPend=" + tratamientoPendiente
                    + ", fechaPend=" + fechaPendiente
                    + ", horaPend=" + horaPendiente
                    + ", ultIntentPend=" + ultimaIntencionPendiente
                    + ", tratPendConsulta=" + tratPendienteConsulta
                    + ", ultimoTrat=" + ultimoTratamiento
                    + ", ultFecha=" + ultimaFecha
                    + ", ultHora=" + ultimaHora
                    + ", clienteId=" + clienteId
                    + ", esperandoConfirmacion=" + esperandoConfirmacion
                    + ", esperandoConfirmacionAgendamiento=" + esperandoConfirmacionAgendamiento
                    + ", ajustandoHorario=" + ajustandoHorario
                    + "]";
        }

        public boolean tieneDatosCompletosParaAgendar() {
            return clienteId != null
                    && tratamientoPendiente != null
                    && fechaPendiente != null
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

    /**
     * "primer viernes de noviembre" → LocalDate (si ya pasó este año, lo mueve al
     * próximo).
     */
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

        // si ya pasó este año y el texto no dice explícitamente "del año que viene",
        // pásalo al
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
            String prompt = """
                    Sos un parser de fechas en español (Paraguay). Dada esta frase de fecha en español y la fecha de hoy, responde SOLO la fecha resultante en formato ISO yyyy-MM-dd, sin palabras extra.
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
        return lower
                .matches(".*\\b(s[ií]|sí|si|dale|ok|okay|de una|perfecto|confirmo|correcto)\\b.*");
    }

    private boolean esNegacion(String lower) {
        return lower.matches(".*\\b(no|nop|negativo|mejor no)\\b.*");
    }

    private boolean esTemporal(String lower) {
        // Señales claras de “frase de fecha/hora”: días, ordinales, meses, “mañana”,
        // etc.
        return lower.matches(
                ".*\\b(hoy|mañana|manana|pasado|primer|primero|segundo|tercer|tercero|cuarto|quinto|lunes|martes|miercoles|miércoles|jueves|viernes|sabado|sábado|domingo|enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|\\d{1,2}(:\\d{2})?\\s*(am|pm)?)\\b.*");
    }

    /**
     * Extrae correcciones de fecha/hora incluso en frases informales (“no, mejor a
     * las 12”,
     * “cambiemos para el lunes a las 14”, etc.)
     */
    private Correccion extraerCorreccionFechaHora(String texto) {
        if (texto == null || texto.isBlank())
            return null;

        String lower = texto.toLowerCase().trim();

        // 1️⃣ Intentar parseo normal con FechaNaturalService
        var res = fechaNaturalService.parse(lower);
        LocalDate fecha = (res != null) ? res.fecha() : null;
        LocalTime hora = (res != null) ? res.hora() : null;

        // 2️⃣ Fallback manual: detectar hora con expresiones “a las 12”, “tipo 9”,
        // “sobre las 15”
        if (hora == null) {
            var matcher = java.util.regex.Pattern.compile(
                    "\\b(a\\s+las\\s+|a\\s+la\\s+|tipo\\s+|sobre\\s+las\\s+)?(\\d{1,2})([:h\\.]?(\\d{2}))?\\s*(am|pm)?\\b")
                    .matcher(lower);
            if (matcher.find()) {
                try {
                    int h = Integer.parseInt(matcher.group(2));
                    int min = (matcher.group(4) != null) ? Integer.parseInt(matcher.group(4)) : 0;
                    boolean pm = matcher.group(5) != null && matcher.group(5).equalsIgnoreCase("pm");
                    if (pm && h < 12)
                        h += 12;
                    if (!pm && h == 12 && matcher.group(5) == null && lower.contains("mañana"))
                        h = 12; // mantener 12 del mediodía
                    hora = LocalTime.of(h % 24, min);
                } catch (Exception ignored) {
                }
            }
        }

        // 3️⃣ Fallback manual: detectar día relativo o día de la semana (solo si no hay
        // fecha aún)
        if (fecha == null) {
            if (lower.contains("mañana")) {
                fecha = LocalDate.now().plusDays(1);
            } else if (lower.contains("pasado mañana")) {
                fecha = LocalDate.now().plusDays(2);
            } else {
                // Día de la semana (“el lunes”, “para el viernes”)
                var dias = Map.of("lunes", DayOfWeek.MONDAY, "martes", DayOfWeek.TUESDAY,
                        "miércoles", DayOfWeek.WEDNESDAY, "miercoles", DayOfWeek.WEDNESDAY,
                        "jueves", DayOfWeek.THURSDAY, "viernes", DayOfWeek.FRIDAY, "sábado",
                        DayOfWeek.SATURDAY, "sabado", DayOfWeek.SATURDAY, "domingo",
                        DayOfWeek.SUNDAY);
                for (var e : dias.entrySet()) {
                    if (lower.contains(e.getKey())) {
                        DayOfWeek hoy = LocalDate.now().getDayOfWeek();
                        int diff = e.getValue().getValue() - hoy.getValue();
                        if (diff <= 0)
                            diff += 7; // próximo día
                        fecha = LocalDate.now().plusDays(diff);
                        break;
                    }
                }
            }
        }

        // 4️⃣ Si no se detectó nada, devolver null (no hay corrección)
        if (fecha == null && hora == null)
            return null;

        return new Correccion(fecha, hora);
    }

    /** Si falta algo, preguntamos específicamente, sin caer en hora default. */
    private void pedirFaltantes(String telefono, ConversacionContexto ctx) {
        if (ctx.tratamientoPendiente == null) {
            sendWhatsappMessage(telefono, "¿Qué tratamiento querés agendar? 🙂");
            return;
        }
        if (ctx.fechaPendiente == null && ctx.horaPendiente == null) {
            sendWhatsappMessage(telefono,
                    "Necesito la *fecha* y la *hora* 😊. Por ejemplo: \"este viernes a las 14\".");
            return;
        }
        if (ctx.fechaPendiente == null) {
            sendWhatsappMessage(telefono,
                    "¿Para qué *día* querés agendar?");
            return;
        }
        if (ctx.horaPendiente == null) {
            sendWhatsappMessage(telefono, "¿A qué *hora* te viene bien? (p.ej. 13, 13:30, 1pm)");
            return;
        }
    }

    private static record Correccion(LocalDate fecha, LocalTime hora) {
    }

    /**
     * Normaliza el número de teléfono para Paraguay.
     * Ejemplo:
     * +595981123456 → 0981123456
     * 595981123456 → 0981123456
     * 981123456 → 0981123456
     */
    private String normalizePhone(String telefono) {
        if (telefono == null)
            return null;
        String num = telefono.trim().replaceAll("\\s+", "");

        if (num.startsWith("+595")) {
            num = "0" + num.substring(4);
        } else if (num.startsWith("595")) {
            num = "0" + num.substring(3);
        } else if (!num.startsWith("0")) {
            num = "0" + num;
        }

        return num;
    }

    private boolean contieneAlgoDeFechaOHora(String lower) {
        return lower.contains("lunes") || lower.contains("martes") || lower.contains("miércoles")
                || lower.contains("miercoles")
                || lower.contains("jueves") || lower.contains("viernes")
                || lower.contains("sábado") || lower.contains("sabado")
                || lower.contains("domingo") || lower.contains("mañana") || lower.contains("maana")
                || lower.contains("pasado mañana") || lower.contains("pasado maana")
                || lower.matches(".*\\b(hoy|mañana|pasado)\\b.*");
    }

    /**
     * Helper interno para reprocesar una intención pendiente
     * una vez que el cliente fue identificado correctamente.
     */
    private void processIncomingMessageInterno(WhatsappMessageDTO msg, IntentResult ir, ConversacionContexto ctx) {
        try {
            final String telefono = msg.getTelefono();

            System.out.println("🔁 Reprocesando intención pendiente internamente: " + ir.getIntent());
            System.out.println("📚 Contexto actual (interno): " + ctx);

            // Asegurar cliente asociado
            ClienteResponseDTO cliente = msg.getCliente();
            if (cliente == null && ctx.clienteId != null) {
                try {
                    cliente = http.getForObject(
                            baseUrl + "/api/clientes/" + ctx.clienteId,
                            ClienteResponseDTO.class);
                } catch (Exception e) {
                    System.err.println("⚠️ Error al recargar cliente por ID: " + e.getMessage());
                }
            }

            if (cliente == null) {
                sendWhatsappMessage(telefono, "Hubo un problema al identificarte, ¿podés intentar nuevamente?");
                return;
            }

            // ==========================================================
            // 🔹 Redirigir según intención original (igual al flujo principal)
            // ==========================================================
            switch (ir.getIntent()) {
                case "consultar_sesiones_restantes" -> {
                    String trat = ir.getTratamiento();
                    if (trat == null) {
                        sendWhatsappMessage(telefono, "¿Podés decirme el tratamiento que querés consultar?");
                        return;
                    }
                    String respuesta = consultarSesionesRestantes(ctx.clienteId, trat);
                    sendWhatsappMessage(telefono, respuesta);
                }

                case "agendar_sesion" -> {
                    ctx.ultimaIntencion = "agendar_sesion";
                    if (ir.getTratamiento() != null)
                        ctx.tratamientoPendiente = ir.getTratamiento();

                    var parsed = fechaNaturalService.parse(
                            (ir.getFecha() != null ? ir.getFecha() : "") + " " +
                                    (ir.getHora() != null ? ir.getHora() : ""));
                    if (parsed != null) {
                        if (parsed.fecha() != null)
                            ctx.fechaPendiente = parsed.fecha();
                        if (parsed.hora() != null)
                            ctx.horaPendiente = parsed.hora();
                    }

                    if (!ctx.tieneDatosCompletosParaAgendar()) {
                        pedirFaltantes(telefono, ctx);
                        return;
                    }

                    ctx.esperandoConfirmacionAgendamiento = true;
                    contextos.put(telefono, ctx);
                    sendWhatsappMessage(telefono, String.format(
                            "Perfecto 👍. ¿Confirmo *%s* el *%s* a las *%s*?",
                            ctx.tratamientoPendiente,
                            ctx.fechaPendiente,
                            ctx.horaPendiente.toString().substring(0, 5)));
                }

                case "cancelar_sesion", "cancelar_por_tratamiento_fecha", "cancelar_por_fecha_hora" -> {
                    String respuesta = cancelarSesionFlexible(
                            ctx.clienteId, ir.getTratamiento(), ir.getFecha(), ir.getHora());
                    sendWhatsappMessage(telefono, respuesta);
                }

                default -> sendWhatsappMessage(telefono,
                        "¡Listo, ya estás identificado! ¿Querés agendar, cancelar o consultar tus sesiones?");
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendWhatsappMessage(msg.getTelefono(), "Ocurrió un error al continuar tu solicitud 😔.");
        }
    }

    /**
     * Dado un texto como "a las 21", "a las 20:30", "tipo 22", intenta extraer SOLO
     * la hora.
     * No toca la fecha. Devuelve LocalTime o null si no entiende.
     */
    private LocalTime intentarSoloHora(String texto) {
        if (texto == null)
            return null;

        // Primero probamos tu parseHoraFlexible en todo el texto.
        LocalTime h = parseHoraFlexible(texto);
        if (h != null)
            return h;

        // Si no, buscamos un fragmento "a las X" / "a las X:YY".
        var m = java.util.regex.Pattern
                .compile("(?:a las|alas|tipo|para las)\\s*(\\d{1,2}([:.]\\d{2})?)")
                .matcher(texto.toLowerCase(java.util.Locale.forLanguageTag("es-PY")));
        if (m.find()) {
            String horaRaw = m.group(1);
            return parseHoraFlexible(horaRaw);
        }

        return null;
    }

}
