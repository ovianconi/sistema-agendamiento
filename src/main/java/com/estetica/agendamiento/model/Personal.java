package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "personal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Personal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Email(message = "Correo electrónico no válido")
    @Column(nullable = true, length = 100, unique = true)
    private String correo;

    @Column(nullable = false, length = 16)
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "El teléfono internacional debe tener entre 8 y 15 dígitos, opcional '+' al inicio")
    private String telefono;

    @ManyToMany
    @JoinTable(name = "personal_tratamiento", joinColumns = @JoinColumn(name = "personal_id"), inverseJoinColumns = @JoinColumn(name = "tratamiento_id"))
    private List<Tratamiento> tratamientos;
}
