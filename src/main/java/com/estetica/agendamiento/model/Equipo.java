package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "equipos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Equipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ejemplo: "Equipo Facial"
    @Column(nullable = false)
    private String nombre;

    // Ejemplo: "EF1", "EF2", "EF3"
    @Column(nullable = false, unique = true)
    private String codigo;
}
