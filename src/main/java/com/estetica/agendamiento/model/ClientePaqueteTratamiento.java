package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "cliente_paquete_tratamiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ClientePaqueteTratamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_paquete_id")
    @JsonIgnoreProperties({"tratamientos"})
    private ClientePaquete clientePaquete;

    @ManyToOne
    @JoinColumn(name = "tratamiento_id")
    private Tratamiento tratamiento;

    @Column(nullable = false)
    private int sesionesRestantes;

    @Column(nullable = false)
    @Builder.Default
    private int sesionesUsadas = 0;

    public boolean tieneSesionesDisponibles() {
        return sesionesRestantes > 0;
    }

    /** 🔑 Resta una sesión al momento de agendar */
    public void consumirSesion() {
        if (!tieneSesionesDisponibles()) {
            throw new IllegalStateException("No quedan sesiones disponibles para este tratamiento");
        }
        sesionesRestantes--;
    }

    /** 🔑 Suma una sesión (para cuando se cancele la cita) */
    public void devolverSesion() {
        sesionesRestantes++;
    }

    /** 🔑 Registra el uso de una sesión en el historial */
    public void marcarUsada() {
        sesionesUsadas++;
    }
}
