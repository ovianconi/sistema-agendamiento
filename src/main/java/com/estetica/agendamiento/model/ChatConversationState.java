package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "chat_conversation_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversationState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String telefono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(length = 80)
    private String intent;

    @Column(name = "flujo_activo", length = 80)
    private String flujoActivo;

    @Column(name = "accion_pendiente", length = 80)
    private String accionPendiente;

    @Column(length = 80)
    private String esperando;

    @Column(length = 255)
    private String tratamiento;

    private LocalDate fecha;

    private LocalTime hora;

    @Column(name = "esperando_confirmacion", nullable = false)
    private boolean esperandoConfirmacion;

    @Column(name = "datos_parciales", columnDefinition = "TEXT")
    private String datosParciales;

    @Column(name = "last_bot_question", columnDefinition = "TEXT")
    private String lastBotQuestion;

    @Column(name = "tema_general", length = 80)
    private String temaGeneral;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean tieneFlujoActivo() {
        return flujoActivo != null && !flujoActivo.isBlank();
    }

    public boolean estaEsperandoDato() {
        return esperando != null && !esperando.isBlank();
    }

    public void limpiarFlujo() {
        this.intent = null;
        this.flujoActivo = null;
        this.accionPendiente = null;
        this.esperando = null;
        this.tratamiento = null;
        this.fecha = null;
        this.hora = null;
        this.esperandoConfirmacion = false;
        this.datosParciales = null;
        this.lastBotQuestion = null;
        this.temaGeneral = null;
    }
}