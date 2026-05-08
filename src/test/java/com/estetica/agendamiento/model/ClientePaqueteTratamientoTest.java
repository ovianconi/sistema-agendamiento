package com.estetica.agendamiento.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;

class ClientePaqueteTratamientoTest {

    @Test
    void puR101_deberiaTenerSesionesDisponiblesCuandoQuedanCuatro() {
        ClientePaqueteTratamiento cpt = ClientePaqueteTratamiento.builder()
                .sesionesRestantes(5)
                .sesionesUsadas(5)
                .build();

        assertTrue(cpt.tieneSesionesDisponibles());
        assertEquals(5, cpt.getSesionesRestantes());
        assertEquals(5, cpt.getSesionesUsadas());
    }

    @Test
    void puR102_noDeberiaTenerSesionesDisponiblesCuandoQuedanCero() {
        ClientePaqueteTratamiento cpt = ClientePaqueteTratamiento.builder()
                .sesionesRestantes(0)
                .sesionesUsadas(10)
                .build();

        assertFalse(cpt.tieneSesionesDisponibles());
        assertEquals(0, cpt.getSesionesRestantes());
        assertEquals(10, cpt.getSesionesUsadas());
    }

    /*
     * @Test
     * void deberiaConsumirSesionCorrectamenteCuandoHaySaldo() {
     * ClientePaqueteTratamiento cpt = ClientePaqueteTratamiento.builder()
     * .sesionesRestantes(5)
     * .sesionesUsadas(0)
     * .build();
     * 
     * cpt.consumirSesion();
     * 
     * assertEquals(4, cpt.getSesionesRestantes());
     * assertEquals(0, cpt.getSesionesUsadas());
     * }
     * 
     * @Test
     * void deberiaDevolverSesionCorrectamente() {
     * ClientePaqueteTratamiento cpt = ClientePaqueteTratamiento.builder()
     * .sesionesRestantes(2)
     * .sesionesUsadas(1)
     * .build();
     * 
     * cpt.devolverSesion();
     * 
     * assertEquals(3, cpt.getSesionesRestantes());
     * assertEquals(1, cpt.getSesionesUsadas());
     * }
     * 
     * @Test
     * void deberiaMarcarSesionComoUsadaCorrectamente() {
     * ClientePaqueteTratamiento cpt = ClientePaqueteTratamiento.builder()
     * .sesionesRestantes(2)
     * .sesionesUsadas(1)
     * .build();
     * 
     * cpt.marcarUsada();
     * 
     * assertEquals(2, cpt.getSesionesRestantes());
     * assertEquals(2, cpt.getSesionesUsadas());
     * }
     */
}