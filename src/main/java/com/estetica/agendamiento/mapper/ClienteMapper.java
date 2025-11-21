package com.estetica.agendamiento.mapper;

import com.estetica.agendamiento.dto.ClienteResponseDTO;
import com.estetica.agendamiento.model.Cliente;

public class ClienteMapper {

    public static ClienteResponseDTO toResponse(Cliente c) {
        if (c == null)
            return null;
        return new ClienteResponseDTO(
                c.getId(),
                c.getNombre(),
                c.getApellido(),
                c.getTelefono(),
                c.getDocumento(),
                c.getCorreo());
    }
}
