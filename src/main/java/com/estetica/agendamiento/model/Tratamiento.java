package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

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

    @ManyToMany(mappedBy = "tratamientos")
    private List<Paquete> paquetes;

    @ManyToMany(mappedBy = "tratamientos")
    private List<Personal> personal;

    @ManyToMany
    @JoinTable(
        name = "tratamiento_equipos",
        joinColumns = @JoinColumn(name = "tratamiento_id"),
        inverseJoinColumns = @JoinColumn(name = "equipo_id")
    )
    private List<Equipo> equipos;
}
