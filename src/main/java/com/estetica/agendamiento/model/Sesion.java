package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sesiones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSesion estado;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "tratamiento_id", nullable = false)
    private Tratamiento tratamiento;

    @ManyToOne
    @JoinColumn(name = "personal_id", nullable = false)
    private Personal personal;

    @ManyToOne
    @JoinColumn(name = "equipo_id")
    private Equipo equipo;

    public enum EstadoSesion {
        PENDIENTE, CONFIRMADA, CANCELADA, COMPLETADA
    }
}
