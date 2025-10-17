// src/main/java/com/estetica/agendamiento/dto/SesionesRestantesDTO.java
package com.estetica.agendamiento.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SesionesRestantesDTO {
    private Long clienteId;
    private Long tratamientoId;
    private String tratamientoNombre;
    private Integer sesionesRestantes;
    private String estado; // ✅ campo: "vigente", "agotado", "no_tiene"

}
