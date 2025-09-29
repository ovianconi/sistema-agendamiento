package com.estetica.agendamiento.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rol_vista",
        uniqueConstraints = @UniqueConstraint(name = "uk_rol_vista",
                columnNames = {"rol", "vista_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RolVista {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String rol; // Ej: "ROLE_ADMIN"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vista_id", nullable = false)
    @JsonBackReference
    private Vista vista;
}
