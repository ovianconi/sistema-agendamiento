package com.estetica.agendamiento.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TratamientoDisponibleDTO {
    private Long id;
    private String nombre;
    private Integer sesionesRestantes;
}