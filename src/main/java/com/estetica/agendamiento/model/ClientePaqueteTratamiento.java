package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cliente_paquete_tratamiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientePaqueteTratamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_paquete_id")
    private ClientePaquete clientePaquete;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tratamiento_id")
    private Tratamiento tratamiento;

    @Column(nullable = false)
    private Integer sesionesRestantes;
}
