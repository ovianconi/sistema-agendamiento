package com.estetica.agendamiento.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import com.estetica.agendamiento.model.Cliente;
import com.estetica.agendamiento.model.ClientePaquete;
import com.estetica.agendamiento.model.Equipo;
import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.model.Sesion;
import com.estetica.agendamiento.model.Tratamiento;
import lombok.*;

@Getter
@Setter
@Data
public class SesionResponseDTO {
    private Long id;
    private Long clienteId;
    private Long tratamientoId;
    private Long personalId;
    private Long equipoId;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String estado;

    public SesionResponseDTO() {}

    // =======================================================
    // 🔁 Conversor estático desde la entidad Sesion
    // =======================================================
    public static SesionResponseDTO fromEntity(com.estetica.agendamiento.model.Sesion s) {
        if (s == null)
            return null;

        SesionResponseDTO dto = new SesionResponseDTO();

        dto.setId(s.getId());
        dto.setFecha(s.getFecha());
        dto.setHoraInicio(s.getHoraInicio());
        dto.setHoraFin(s.getHoraFin());
        dto.setEstado(s.getEstado() != null ? s.getEstado().name() : null);

        // 🔹 Cliente → viene de clientePaquete.cliente
        if (s.getClientePaquete() != null && s.getClientePaquete().getCliente() != null)
            dto.setClienteId(s.getClientePaquete().getCliente().getId());

        // 🔹 Tratamiento directo
        if (s.getTratamiento() != null)
            dto.setTratamientoId(s.getTratamiento().getId());

        // 🔹 Personal asignado (opcional)
        if (s.getPersonal() != null)
            dto.setPersonalId(s.getPersonal().getId());

        // 🔹 Equipo asignado (opcional)
        if (s.getEquipo() != null)
            dto.setEquipoId(s.getEquipo().getId());

        return dto;
    }


    // =======================================================
    // 🔁 Conversor inverso: de DTO a entidad Sesion
    // =======================================================
    public Sesion toEntity() {
        Sesion s = new Sesion();

        s.setId(this.id);
        s.setFecha(this.fecha);
        s.setHoraInicio(this.horaInicio);
        s.setHoraFin(this.horaFin);

        // Estado → si viene nulo, usar PENDIENTE como predeterminado
        if (this.estado != null) {
            try {
                s.setEstado(Sesion.EstadoSesion.valueOf(this.estado.toUpperCase()));
            } catch (IllegalArgumentException e) {
                s.setEstado(Sesion.EstadoSesion.PENDIENTE);
            }
        } else {
            s.setEstado(Sesion.EstadoSesion.PENDIENTE);
        }

        // 🔹 Relación Cliente → se setea a través de ClientePaquete
        if (this.clienteId != null) {
            Cliente cliente = new Cliente();
            cliente.setId(this.clienteId);

            ClientePaquete cp = new ClientePaquete();
            cp.setCliente(cliente);

            s.setClientePaquete(cp);
        }

        // 🔹 Tratamiento
        if (this.tratamientoId != null) {
            Tratamiento t = new Tratamiento();
            t.setId(this.tratamientoId);
            s.setTratamiento(t);
        }

        // 🔹 Personal (opcional)
        if (this.personalId != null) {
            Personal p = new Personal();
            p.setId(this.personalId);
            s.setPersonal(p);
        }

        // 🔹 Equipo (opcional)
        if (this.equipoId != null) {
            Equipo e = new Equipo();
            e.setId(this.equipoId);
            s.setEquipo(e);
        }

        return s;
    }

}
