package com.estetica.agendamiento.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "paquete_tratamiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PaqueteTratamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Paquete
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paquete_id")
    @JsonIgnoreProperties({"items"}) // ← evita incluir items de vuelta
    private Paquete paquete;

    // Tratamiento
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tratamiento_id")
    @JsonIgnoreProperties({"equipos", "personales"}) // opcional, si tienes estas relaciones
    private Tratamiento tratamiento;

    // Atributo extra
    @Column(nullable = false)
    private Integer sesiones;
}
