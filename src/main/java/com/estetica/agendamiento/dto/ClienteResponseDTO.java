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

    public ClienteResponseDTO() {
    }

    public ClienteResponseDTO(Long id,
            String nombre,
            String apellido,
            String telefono,
            String documento,
            String email) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.telefono = telefono;
        this.documento = documento;
        this.email = email;
    }

    public static ClienteResponseDTO fromEntity(Cliente c) {
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
