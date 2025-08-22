package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "paquetes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Paquete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private int sesionesDisponibles;

    @Column(nullable = false)
    private LocalDate fechaCompra;

    @Column(nullable = false)
    private LocalDate fechaVencimiento;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToMany
    @JoinTable(
        name = "paquete_tratamientos",
        joinColumns = @JoinColumn(name = "paquete_id"),
        inverseJoinColumns = @JoinColumn(name = "tratamiento_id")
    )
    private List<Tratamiento> tratamientos;
}
