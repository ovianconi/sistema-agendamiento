package com.estetica.agendamiento.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.*;

@Getter
@Setter
public class AgendamientoRequestDTO {
    @NotNull
    private Long clienteId;
    @NotNull
    private Long tratamientoId;
    @NotNull
    @FutureOrPresent
    private LocalDate fecha;
    @NotNull
    private LocalTime horaInicio;
    @NotNull
    private LocalTime horaFin;
    private Long personalId; // opcional: si null, el sistema puede sugerir uno
    private Long equipoId; // opcional

}
