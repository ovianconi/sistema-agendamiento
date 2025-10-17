package com.estetica.agendamiento.dto;

import java.util.List;
import java.util.stream.Collectors;
import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.model.Tratamiento;

public class PersonalDTO {
    private Long id;
    private String nombre;
    private String apellido;
    private String correo;
    private String telefono;
    private List<String> tratamientos;

    // Constructor
    public PersonalDTO(Personal p) {
        this.id = p.getId();
        this.nombre = p.getNombre();
        this.apellido = p.getApellido();
        this.correo = p.getCorreo();
        this.telefono = p.getTelefono();
        this.tratamientos = p.getTratamientos().stream().map(Tratamiento::getNombre)
                .collect(Collectors.toList());
    }
}
