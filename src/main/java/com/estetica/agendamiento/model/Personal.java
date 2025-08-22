package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "personal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Personal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 15)
    private String documento;

    @Column(nullable = false, length = 50)
    private String telefono;

    @ManyToMany
    @JoinTable(
        name = "personal_tratamientos",
        joinColumns = @JoinColumn(name = "personal_id"),
        inverseJoinColumns = @JoinColumn(name = "tratamiento_id")
    )
    private List<Tratamiento> tratamientos;
}
