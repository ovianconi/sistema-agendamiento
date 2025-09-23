package com.estetica.agendamiento.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "tratamientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Tratamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    private String descripcion;

    // Relación con equipos
    @ManyToMany(mappedBy = "tratamientos")
    @JsonIgnore // 🔹 evita recursión infinita al serializar
    private List<Equipo> equipos;

    @ManyToMany(mappedBy = "tratamientos")
    @JsonIgnore // 🔹 evita recursión infinita al serializar
    private List<Personal> personales;
}
