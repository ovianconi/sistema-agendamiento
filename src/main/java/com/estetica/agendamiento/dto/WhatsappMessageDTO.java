package com.estetica.agendamiento.dto;

import lombok.Data;

@Data
public class WhatsappMessageDTO {
    private String telefono;          // Número de WhatsApp (wa_id)
    private String texto;             // Texto recibido
    private ClienteResponseDTO cliente; // Se setea si ya está registrado
    private String mensajeId;         // ID del mensaje (opcional)
    private String nombre;            // Nombre del contacto
}