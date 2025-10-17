package com.estetica.agendamiento.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {
    public String intent; // Ej: "consultar_sesiones_restantes", "cancelar_por_fecha_hora"
    public String tratamiento; // Ej: "radiofrecuencia", "masaje"
    public String fecha; // Ej: "2025-10-08"
    public String hora; // Ej: "15:00"
    public String clienteIdent; // Ej: "por_telefono", "por_documento", "por_nombre"
}
