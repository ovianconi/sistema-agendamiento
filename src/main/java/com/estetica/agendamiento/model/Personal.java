package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
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

    @Column(nullable = false, length = 100)
    private String apellido;

    @Email(message = "Correo electrónico no válido")
    @Column(nullable = true, length = 100)
    private String correo;

    @Column(nullable = false, length = 50)
    private String telefono;

    @ManyToMany
    @JoinTable(name = "personal_tratamiento", joinColumns = @JoinColumn(name = "personal_id"),
            inverseJoinColumns = @JoinColumn(name = "tratamiento_id"))
    private List<Tratamiento> tratamientos;
}
