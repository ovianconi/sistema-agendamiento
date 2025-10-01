package com.estetica.agendamiento.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "cliente_paquete")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ClientePaquete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    // 🔑 Evita que serialice propiedades proxy de Cliente
    private Cliente cliente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "paquete_id")
    @JsonIgnoreProperties({"items"})
    // 🔑 Evita que Paquete devuelva otra vez todos sus items y reentre en el loop
    private Paquete paquete;

    @Column(nullable = false)
    private LocalDate fechaCompra;

    @Column
    private LocalDate fechaValidez;

    @Column
    private LocalDate fechaInicio; // se setea en el primer uso


    @OneToMany(mappedBy = "clientePaquete", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @JsonIgnoreProperties({"clientePaquete"})
    // 🔑 Evita que ClientePaqueteTratamiento serialice de nuevo a su padre
    private List<ClientePaqueteTratamiento> tratamientos;
}
