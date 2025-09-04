package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tratamientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tratamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    // Relación opcional con equipo
    @ManyToOne
    @JoinColumn(name = "equipo_id", nullable = true) // ahora puede ser null
    private Equipo equipo;
}
