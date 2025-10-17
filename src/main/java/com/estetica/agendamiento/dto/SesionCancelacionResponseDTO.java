package com.estetica.agendamiento.dto;

import com.estetica.agendamiento.model.Sesion;

public class SesionCancelacionResponseDTO {
    private Long sesionId;
    private String tratamiento;
    private String cliente;
    private String fecha;
    private String horaInicio;
    private String horaFin;
    private String estado;
    private String mensaje;

    public SesionCancelacionResponseDTO(Sesion sesion, String mensaje) {
        this.sesionId = sesion.getId();
        this.tratamiento = sesion.getTratamiento().getNombre();
        this.cliente = sesion.getClientePaquete().getCliente().getNombre() + " " +
                       sesion.getClientePaquete().getCliente().getApellido();
        this.fecha = sesion.getFecha().toString();
        this.horaInicio = sesion.getHoraInicio().toString();
        this.horaFin = sesion.getHoraFin().toString();
        this.estado = sesion.getEstado().name();
        this.mensaje = mensaje;
    }

    public Long getSesionId() { return sesionId; }
    public String getTratamiento() { return tratamiento; }
    public String getCliente() { return cliente; }
    public String getFecha() { return fecha; }
    public String getHoraInicio() { return horaInicio; }
    public String getHoraFin() { return horaFin; }
    public String getEstado() { return estado; }
    public String getMensaje() { return mensaje; }
}
