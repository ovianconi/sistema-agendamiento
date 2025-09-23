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
    private Cliente cliente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "paquete_id")
    private Paquete paquete;

    @Column(nullable = false)
    private LocalDate fechaCompra;

    @Column(nullable = false)
    private LocalDate fechaValidez; // fechaCompra + meses

    // Aquí se guardarán las sesiones restantes por tratamiento
    @OneToMany(mappedBy = "clientePaquete", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<ClientePaqueteTratamiento> tratamientos;
}
