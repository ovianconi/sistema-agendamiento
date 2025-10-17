// src/main/java/com/estetica/agendamiento/dto/ClienteResponseDTO.java
package com.estetica.agendamiento.dto;

import com.estetica.agendamiento.model.Cliente;
import lombok.Data;

@Data
public class ClienteResponseDTO {
    private Long id;
    private String nombre;
    private String apellido;
    private String telefono;
    private String documento;
    private String email;

    public static ClienteResponseDTO fromEntity(Cliente cliente) {
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setId(cliente.getId());
        dto.setNombre(cliente.getNombre());
        dto.setApellido(cliente.getApellido());
        dto.setTelefono(cliente.getTelefono());
        dto.setDocumento(cliente.getDocumento());
        dto.setEmail(cliente.getCorreo());
        return dto;
    }
}
