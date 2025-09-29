package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "parametros_sistema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParametroSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String clave; // ejemplo: "TIEMPO_MINIMO_CANCELACION_HORAS"

    @Column(nullable = false, length = 200)
    private String valor; // se guarda como String, se parsea al tipo necesario

    @Column(nullable = true, length = 200)
    private String descripcion; // para facilitar su administración
}
