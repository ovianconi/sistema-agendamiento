package com.estetica.agendamiento.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, unique = true, length = 15)
    @Pattern(regexp = "^\\+?\\d+$", message = "El documento solo debe tener caracteres numéricos")
    private String documento;

    @Column(nullable = false, length = 50)
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "El teléfono internacional debe tener entre 8 y 15 dígitos, opcional '+' al inicio")
    private String telefono;

    @Email(message = "Correo electrónico no válido")
    @Column(nullable = true, length = 100)
    private String correo;
}
