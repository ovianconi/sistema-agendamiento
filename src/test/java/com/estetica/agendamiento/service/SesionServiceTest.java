package com.estetica.agendamiento.service;

import com.estetica.agendamiento.dto.SesionRequestDTO;
import com.estetica.agendamiento.model.*;
import com.estetica.agendamiento.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SesionServiceTest {

        @Mock
        private SesionRepository sesionRepository;
        @Mock
        private ClienteRepository clienteRepository;
        @Mock
        private TratamientoRepository tratamientoRepository;
        @Mock
        private ClientePaqueteTratamientoRepository cptRepository;
        @Mock
        private PersonalRepository personalRepository;
        @Mock
        private EquipoRepository equipoRepository;
        @Mock
        private ClientePaqueteRepository clientePaqueteRepository;
        @Mock
        private ParametroSistemaService parametroSistemaService;

        @InjectMocks
        private SesionService sesionService;

        private Cliente cliente;
        private Tratamiento tratamiento;

        @BeforeEach
        void setup() {
                cliente = new Cliente();
                cliente.setId(1L);

                tratamiento = new Tratamiento();
                tratamiento.setId(1L);
                tratamiento.setRequiereEquipo(false);
        }

        // ==========================================
        // ✅ CASO 1: CREACIÓN EXITOSA
        // ==========================================
        /*
         * @Test
         * void deberiaCrearSesionCorrectamente() {
         * 
         * LocalDate fecha = LocalDate.now().plusDays(1);
         * LocalTime horaInicio = LocalTime.of(10, 0);
         * 
         * when(tratamientoRepository.findById(1L))
         * .thenReturn(java.util.Optional.of(tratamiento));
         * 
         * when(sesionRepository.existsActivaByClienteAndRango(any(), any(), any(),
         * any()))
         * .thenReturn(false);
         * 
         * ClientePaquete cp = new ClientePaquete();
         * cp.setId(1L);
         * 
         * ClientePaqueteTratamiento cpt = new ClientePaqueteTratamiento();
         * cpt.setClientePaquete(cp);
         * cpt.setTratamiento(tratamiento);
         * cpt.setSesionesRestantes(5);
         * 
         * when(cptRepository.encontrarVigentesConSaldo(any(), any()))
         * .thenReturn(java.util.List.of(cpt));
         * 
         * Personal personal = new Personal();
         * personal.setId(1L);
         * 
         * when(personalRepository.findByTratamientos_Id(any()))
         * .thenReturn(java.util.List.of(personal));
         * 
         * when(sesionRepository.existsActivaByPersonalAndRango(any(), any(), any(),
         * any()))
         * .thenReturn(false);
         * 
         * when(sesionRepository.save(any()))
         * .thenAnswer(i -> i.getArgument(0));
         * 
         * Sesion sesion = sesionService.crearSesion(
         * 1L, 1L, fecha, horaInicio, 60);
         * 
         * assertNotNull(sesion);
         * assertEquals(Sesion.EstadoSesion.PENDIENTE, sesion.getEstado());
         * }
         * 
         * // ==========================================
         * // ❌ CASO 2: FECHA PASADA
         * // ==========================================
         * 
         * @Test
         * void noDeberiaPermitirFechaPasada() {
         * 
         * LocalDate fecha = LocalDate.now().minusDays(1);
         * LocalTime hora = LocalTime.of(10, 0);
         * 
         * RuntimeException ex = assertThrows(RuntimeException.class,
         * () -> sesionService.crearSesion(1L, 1L, fecha, hora, 60));
         * 
         * assertTrue(ex.getMessage().contains("pasado"));
         * }
         * 
         * // ==========================================
         * // ❌ CASO 3: CONFLICTO DE CLIENTE
         * // ==========================================
         * 
         * @Test
         * void noDeberiaPermitirConflictoCliente() {
         * 
         * LocalDate fecha = LocalDate.now().plusDays(1);
         * LocalTime hora = LocalTime.of(10, 0);
         * 
         * when(tratamientoRepository.findById(1L))
         * .thenReturn(java.util.Optional.of(tratamiento));
         * 
         * when(sesionRepository.existsActivaByClienteAndRango(any(), any(), any(),
         * any()))
         * .thenReturn(true);
         * 
         * RuntimeException ex = assertThrows(RuntimeException.class,
         * () -> sesionService.crearSesion(1L, 1L, fecha, hora, 60));
         * 
         * assertTrue(ex.getMessage().contains("otra sesión"));
         * }
         * 
         * // =============================================
         * // CANCELAR CORRECTAMENTE
         * // =============================================
         * 
         * @Test
         * void deberiaCancelarSesionCorrectamente() {
         * Long sesionId = 1L;
         * Long clientePaqueteId = 10L;
         * Long tratamientoId = 20L;
         * 
         * LocalDate fecha = LocalDate.now().plusDays(1);
         * LocalTime horaInicio = LocalTime.of(15, 0);
         * 
         * Tratamiento tratamiento = new Tratamiento();
         * tratamiento.setId(tratamientoId);
         * tratamiento.setNombre("Limpieza facial");
         * 
         * Paquete paquete = new Paquete();
         * paquete.setId(100L);
         * 
         * Cliente cliente = new Cliente();
         * cliente.setId(200L);
         * cliente.setNombre("Ana");
         * cliente.setApellido("Gomez");
         * 
         * ClientePaquete clientePaquete = new ClientePaquete();
         * clientePaquete.setId(clientePaqueteId);
         * clientePaquete.setPaquete(paquete);
         * clientePaquete.setCliente(cliente);
         * 
         * Sesion sesion = new Sesion();
         * sesion.setId(sesionId);
         * sesion.setFecha(fecha);
         * sesion.setHoraInicio(horaInicio);
         * sesion.setHoraFin(horaInicio.plusHours(1));
         * sesion.setEstado(Sesion.EstadoSesion.PENDIENTE);
         * sesion.setClientePaquete(clientePaquete);
         * sesion.setTratamiento(tratamiento);
         * 
         * ClientePaqueteTratamiento cpt = new ClientePaqueteTratamiento();
         * cpt.setId(300L);
         * cpt.setClientePaquete(clientePaquete);
         * cpt.setTratamiento(tratamiento);
         * cpt.setSesionesRestantes(2);
         * 
         * when(sesionRepository.findById(sesionId))
         * .thenReturn(java.util.Optional.of(sesion));
         * 
         * when(parametroSistemaService.getParametroEntero(
         * "TIEMPO_MINIMO_CANCELACION_HORAS", 2))
         * .thenReturn(2);
         * 
         * when(cptRepository.findByClientePaqueteIdAndTratamientoId(clientePaqueteId,
         * tratamientoId))
         * .thenReturn(java.util.Optional.of(cpt));
         * 
         * when(cptRepository.save(any(ClientePaqueteTratamiento.class)))
         * .thenAnswer(inv -> inv.getArgument(0));
         * 
         * when(sesionRepository.save(any(Sesion.class)))
         * .thenAnswer(inv -> inv.getArgument(0));
         * 
         * Sesion resultado = sesionService.cancelarSesion(sesionId);
         * 
         * assertNotNull(resultado);
         * assertEquals(Sesion.EstadoSesion.CANCELADA, resultado.getEstado());
         * assertEquals(3, cpt.getSesionesRestantes());
         * 
         * verify(cptRepository).save(cpt);
         * verify(sesionRepository).save(sesion);
         * }
         * 
         * // =============================================
         * // CANCELAR SESION USADA
         * // =============================================
         * 
         * @Test
         * void noDeberiaCancelarSesionUsada() {
         * Long sesionId = 1L;
         * 
         * Sesion sesion = new Sesion();
         * sesion.setId(sesionId);
         * sesion.setEstado(Sesion.EstadoSesion.USADA);
         * sesion.setFecha(LocalDate.now().plusDays(1));
         * sesion.setHoraInicio(LocalTime.of(10, 0));
         * 
         * when(sesionRepository.findById(sesionId))
         * .thenReturn(java.util.Optional.of(sesion));
         * 
         * when(parametroSistemaService.getParametroEntero(
         * "TIEMPO_MINIMO_CANCELACION_HORAS", 2))
         * .thenReturn(2);
         * 
         * RuntimeException ex = assertThrows(RuntimeException.class,
         * () -> sesionService.cancelarSesion(sesionId));
         * 
         * assertTrue(ex.getMessage().contains("ya fue usada"));
         * 
         * verify(cptRepository, never()).save(any());
         * verify(sesionRepository, never()).save(any(Sesion.class));
         * }
         * 
         * // =============================================
         * // CANCELAR SESION PERDIDA
         * // =============================================
         * 
         * @Test
         * void noDeberiaCancelarSesionPerdida() {
         * Long sesionId = 1L;
         * 
         * Sesion sesion = new Sesion();
         * sesion.setId(sesionId);
         * sesion.setEstado(Sesion.EstadoSesion.PERDIDA);
         * sesion.setFecha(LocalDate.now().plusDays(1));
         * sesion.setHoraInicio(LocalTime.of(10, 0));
         * 
         * when(sesionRepository.findById(sesionId))
         * .thenReturn(java.util.Optional.of(sesion));
         * 
         * when(parametroSistemaService.getParametroEntero(
         * "TIEMPO_MINIMO_CANCELACION_HORAS", 2))
         * .thenReturn(2);
         * 
         * RuntimeException ex = assertThrows(RuntimeException.class,
         * () -> sesionService.cancelarSesion(sesionId));
         * 
         * assertTrue(ex.getMessage().contains("marcada como perdida"));
         * 
         * verify(cptRepository, never()).save(any());
         * verify(sesionRepository, never()).save(any(Sesion.class));
         * }
         * 
         * // =============================================
         * // CANCELAR SESION CANCELADA
         * // =============================================
         * 
         * @Test
         * void deberiaRetornarMismaSesionSiYaEstabaCancelada() {
         * Long sesionId = 1L;
         * 
         * Sesion sesion = new Sesion();
         * sesion.setId(sesionId);
         * sesion.setEstado(Sesion.EstadoSesion.CANCELADA);
         * sesion.setFecha(LocalDate.now().plusDays(1));
         * sesion.setHoraInicio(LocalTime.of(10, 0));
         * 
         * when(sesionRepository.findById(sesionId))
         * .thenReturn(java.util.Optional.of(sesion));
         * 
         * when(parametroSistemaService.getParametroEntero(
         * "TIEMPO_MINIMO_CANCELACION_HORAS", 2))
         * .thenReturn(2);
         * 
         * Sesion resultado = sesionService.cancelarSesion(sesionId);
         * 
         * assertSame(sesion, resultado);
         * assertEquals(Sesion.EstadoSesion.CANCELADA, resultado.getEstado());
         * 
         * verify(cptRepository, never()).save(any());
         * verify(sesionRepository, never()).save(any(Sesion.class));
         * }
         * 
         * // =============================================
         * // CANCELAR SESION FUERA RANGO TIEMPO
         * // =============================================
         * 
         * @Test
         * void noDeberiaCancelarSesionFueraDelTiempoPermitido() {
         * Long sesionId = 1L;
         * 
         * Sesion sesion = new Sesion();
         * sesion.setId(sesionId);
         * sesion.setEstado(Sesion.EstadoSesion.PENDIENTE);
         * sesion.setFecha(LocalDate.now());
         * sesion.setHoraInicio(LocalTime.now().plusMinutes(30)); // demasiado cerca
         * 
         * when(sesionRepository.findById(sesionId))
         * .thenReturn(java.util.Optional.of(sesion));
         * 
         * when(parametroSistemaService.getParametroEntero(
         * "TIEMPO_MINIMO_CANCELACION_HORAS", 2))
         * .thenReturn(2);
         * 
         * RuntimeException ex = assertThrows(RuntimeException.class,
         * () -> sesionService.cancelarSesion(sesionId));
         * 
         * assertTrue(ex.getMessage().contains("solo puede cancelarse hasta"));
         * 
         * verify(cptRepository, never()).save(any());
         * verify(sesionRepository, never()).save(any(Sesion.class));
         * }
         * 
         * // =============================================
         * // CANCELAR SESION NO EXISTE
         * // =============================================
         * 
         * @Test
         * void deberiaLanzarErrorSiSesionNoExiste() {
         * Long sesionId = 999L;
         * 
         * when(sesionRepository.findById(sesionId))
         * .thenReturn(java.util.Optional.empty());
         * 
         * RuntimeException ex = assertThrows(RuntimeException.class,
         * () -> sesionService.cancelarSesion(sesionId));
         * 
         * assertTrue(ex.getMessage().contains("Sesión no encontrada"));
         * 
         * verify(cptRepository, never()).save(any());
         * verify(sesionRepository, never()).save(any(Sesion.class));
         * }
         */

        // PU-R1-03: Incremento de sesiones al cancelar correctamente
        @Test
        void puR103_deberiaIncrementarSesionAlCancelarCorrectamente() {

                Long sesionId = 1L;
                Long clientePaqueteId = 10L;
                Long tratamientoId = 20L;

                LocalDate fecha = LocalDate.now().plusDays(1);
                LocalTime horaInicio = LocalTime.of(15, 0);

                Tratamiento tratamiento = new Tratamiento();
                tratamiento.setId(tratamientoId);

                ClientePaquete clientePaquete = new ClientePaquete();
                clientePaquete.setId(clientePaqueteId);

                Sesion sesion = new Sesion();
                sesion.setId(sesionId);
                sesion.setEstado(Sesion.EstadoSesion.PENDIENTE);
                sesion.setFecha(fecha);
                sesion.setHoraInicio(horaInicio);
                sesion.setClientePaquete(clientePaquete);
                sesion.setTratamiento(tratamiento);

                ClientePaqueteTratamiento cpt = new ClientePaqueteTratamiento();
                cpt.setClientePaquete(clientePaquete);
                cpt.setTratamiento(tratamiento);
                cpt.setSesionesRestantes(2);

                when(sesionRepository.findById(sesionId))
                                .thenReturn(java.util.Optional.of(sesion));

                when(parametroSistemaService.getParametroEntero("TIEMPO_MINIMO_CANCELACION_HORAS", 2))
                                .thenReturn(2);

                when(cptRepository.findByClientePaqueteIdAndTratamientoId(clientePaqueteId, tratamientoId))
                                .thenReturn(java.util.Optional.of(cpt));

                when(cptRepository.save(any()))
                                .thenAnswer(inv -> inv.getArgument(0));

                when(sesionRepository.save(any()))
                                .thenAnswer(inv -> inv.getArgument(0));

                // Ejecutar
                Sesion resultado = sesionService.cancelarSesion(sesionId);

                // Validar
                assertEquals(Sesion.EstadoSesion.CANCELADA, resultado.getEstado());
                assertEquals(3, cpt.getSesionesRestantes());
        }

        // =======================================
        // PROFESIONAL HABILITADO
        // =======================================
        @Test
        void puR104_deberiaAsignarProfesionalCuandoEstaHabilitadoParaTratamiento() {
                SesionRequestDTO dto = new SesionRequestDTO();
                dto.setClienteId(1L);
                dto.setTratamientoId(1L);
                dto.setFecha(LocalDate.now().plusDays(1));
                dto.setHoraInicio(LocalTime.of(9, 0));
                dto.setHoraFin(LocalTime.of(10, 0));
                dto.setPersonalId(5L);

                Cliente cliente = new Cliente();
                cliente.setId(1L);

                Tratamiento tratamiento = new Tratamiento();
                tratamiento.setId(1L);
                tratamiento.setRequiereEquipo(false);

                ClientePaquete cp = new ClientePaquete();
                cp.setId(10L);

                ClientePaqueteTratamiento cpt = new ClientePaqueteTratamiento();
                cpt.setClientePaquete(cp);
                cpt.setTratamiento(tratamiento);
                cpt.setSesionesRestantes(3);

                Personal personal = new Personal();
                personal.setId(5L);

                when(clienteRepository.findById(1L)).thenReturn(java.util.Optional.of(cliente));
                when(tratamientoRepository.findById(1L)).thenReturn(java.util.Optional.of(tratamiento));
                when(parametroSistemaService.getParametroEntero(
                                eq("DURACION_SESION_MINUTOS"), eq(60)))
                                .thenReturn(60);

                when(parametroSistemaService.getParametroHora(
                                eq("HORA_ENTRADA"), any(LocalTime.class)))
                                .thenReturn(LocalTime.of(8, 0));

                when(parametroSistemaService.getParametroHora(
                                eq("HORA_SALIDA"), any(LocalTime.class)))
                                .thenReturn(LocalTime.of(20, 0));

                when(sesionRepository.existsActivaByClienteAndRango(anyLong(), any(), any(), any()))
                                .thenReturn(false);

                when(cptRepository.encontrarVigentesConSaldo(anyLong(), any()))
                                .thenReturn(java.util.List.of(cpt));

                when(personalRepository.findById(5L)).thenReturn(java.util.Optional.of(personal));
                when(personalRepository.existsByIdAndTratamientos_Id(5L, 1L)).thenReturn(true);

                when(sesionRepository.existsActivaByPersonalAndRango(any(), any(), any(), any()))
                                .thenReturn(false);

                when(sesionRepository.save(any(Sesion.class)))
                                .thenAnswer(inv -> inv.getArgument(0));

                Sesion resultado = sesionService.crearSesionDesdeDTO(dto);

                assertNotNull(resultado);
                assertNotNull(resultado.getPersonal());
                assertEquals(5L, resultado.getPersonal().getId());
        }

        // =======================================
        // PROFESIONAL NO HABILITADO
        // =======================================
        @Test
        void puR105_deberiaRechazarProfesionalCuandoNoEstaHabilitadoParaTratamiento() {
                SesionRequestDTO dto = new SesionRequestDTO();
                dto.setClienteId(1L);
                dto.setTratamientoId(1L);
                dto.setFecha(LocalDate.now().plusDays(1));
                dto.setHoraInicio(LocalTime.of(9, 0));
                dto.setHoraFin(LocalTime.of(10, 0));
                dto.setPersonalId(5L);

                Cliente cliente = new Cliente();
                cliente.setId(1L);

                Tratamiento tratamiento = new Tratamiento();
                tratamiento.setId(1L);
                tratamiento.setRequiereEquipo(false);

                ClientePaquete cp = new ClientePaquete();
                cp.setId(10L);

                ClientePaqueteTratamiento cpt = new ClientePaqueteTratamiento();
                cpt.setClientePaquete(cp);
                cpt.setTratamiento(tratamiento);
                cpt.setSesionesRestantes(3);

                Personal personal = new Personal();
                personal.setId(5L);

                when(clienteRepository.findById(1L)).thenReturn(java.util.Optional.of(cliente));
                when(tratamientoRepository.findById(1L)).thenReturn(java.util.Optional.of(tratamiento));
                when(parametroSistemaService.getParametroEntero(
                                eq("DURACION_SESION_MINUTOS"), eq(60)))
                                .thenReturn(60);

                when(parametroSistemaService.getParametroHora(
                                eq("HORA_ENTRADA"), any(LocalTime.class)))
                                .thenReturn(LocalTime.of(8, 0));

                when(parametroSistemaService.getParametroHora(
                                eq("HORA_SALIDA"), any(LocalTime.class)))
                                .thenReturn(LocalTime.of(20, 0));

                when(sesionRepository.existsActivaByClienteAndRango(anyLong(), any(), any(), any()))
                                .thenReturn(false);

                when(cptRepository.encontrarVigentesConSaldo(anyLong(), any()))
                                .thenReturn(java.util.List.of(cpt));

                when(personalRepository.findById(5L)).thenReturn(java.util.Optional.of(personal));
                when(personalRepository.existsByIdAndTratamientos_Id(5L, 1L)).thenReturn(false);

                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> sesionService.crearSesionDesdeDTO(dto));

                assertTrue(ex.getMessage().contains("no está habilitado"));
                verify(sesionRepository, never()).save(any(Sesion.class));
        }

        // =================================
        // HORARIO CONFLICTO REAL
        // =================================
        @Test
        void puR106_deberiaRechazarSesionCuandoExisteConflictoHorarioDelCliente() {
                LocalDate fecha = LocalDate.now().plusDays(1);
                LocalTime horaInicio = LocalTime.of(10, 30);

                Tratamiento tratamiento = new Tratamiento();
                tratamiento.setId(1L);
                tratamiento.setRequiereEquipo(false);

                when(tratamientoRepository.findById(1L))
                                .thenReturn(java.util.Optional.of(tratamiento));

                when(sesionRepository.existsActivaByClienteAndRango(1L, fecha, horaInicio, horaInicio.plusMinutes(60)))
                                .thenReturn(true);

                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> sesionService.crearSesion(1L, 1L, fecha, horaInicio, 60));

                assertTrue(ex.getMessage().contains("otra sesión"));
        }

        // =================================
        // HORARIO SIN CONFLICTO
        // =================================
        @Test
        void puR107_deberiaPermitirSesionCuandoNoExisteConflictoHorario() {
                LocalDate fecha = LocalDate.now().plusDays(1);
                LocalTime horaInicio = LocalTime.of(11, 30);

                Tratamiento tratamiento = new Tratamiento();
                tratamiento.setId(1L);
                tratamiento.setRequiereEquipo(false);

                ClientePaquete cp = new ClientePaquete();
                cp.setId(10L);

                ClientePaqueteTratamiento cpt = new ClientePaqueteTratamiento();
                cpt.setClientePaquete(cp);
                cpt.setTratamiento(tratamiento);
                cpt.setSesionesRestantes(5);

                Personal personal = new Personal();
                personal.setId(2L);

                when(tratamientoRepository.findById(1L))
                                .thenReturn(java.util.Optional.of(tratamiento));

                when(sesionRepository.existsActivaByClienteAndRango(anyLong(), any(), any(), any()))
                                .thenReturn(false);

                when(cptRepository.encontrarVigentesConSaldo(anyLong(), any()))
                                .thenReturn(java.util.List.of(cpt));

                when(personalRepository.findByTratamientos_Id(1L))
                                .thenReturn(java.util.List.of(personal));

                when(sesionRepository.existsActivaByPersonalAndRango(any(), any(), any(), any()))
                                .thenReturn(false);

                when(sesionRepository.save(any(Sesion.class)))
                                .thenAnswer(inv -> inv.getArgument(0));

                Sesion resultado = sesionService.crearSesion(1L, 1L, fecha, horaInicio, 60);

                assertNotNull(resultado);
                assertEquals(LocalTime.of(12, 30), resultado.getHoraFin());
        }

        // =================================
        // HORARIO CASO BORDE
        // =================================
        @Test
        void puR108_deberiaPermitirSesionEnCasoBordeCuandoFinCoincideConInicio() {
                LocalDate fecha = LocalDate.now().plusDays(1);
                LocalTime horaInicio = LocalTime.of(11, 0); // borde respecto a una previa 10:00-11:00

                Tratamiento tratamiento = new Tratamiento();
                tratamiento.setId(1L);
                tratamiento.setRequiereEquipo(false);

                ClientePaquete cp = new ClientePaquete();
                cp.setId(10L);

                ClientePaqueteTratamiento cpt = new ClientePaqueteTratamiento();
                cpt.setClientePaquete(cp);
                cpt.setTratamiento(tratamiento);
                cpt.setSesionesRestantes(5);

                Personal personal = new Personal();
                personal.setId(2L);

                when(tratamientoRepository.findById(1L))
                                .thenReturn(java.util.Optional.of(tratamiento));

                when(sesionRepository.existsActivaByClienteAndRango(anyLong(), any(), any(), any()))
                                .thenReturn(false);

                when(cptRepository.encontrarVigentesConSaldo(anyLong(), any()))
                                .thenReturn(java.util.List.of(cpt));

                when(personalRepository.findByTratamientos_Id(1L))
                                .thenReturn(java.util.List.of(personal));

                when(sesionRepository.existsActivaByPersonalAndRango(any(), any(), any(), any()))
                                .thenReturn(false);

                when(sesionRepository.save(any(Sesion.class)))
                                .thenAnswer(inv -> inv.getArgument(0));

                Sesion resultado = sesionService.crearSesion(1L, 1L, fecha, horaInicio, 60);

                assertNotNull(resultado);
                assertEquals(LocalTime.of(12, 0), resultado.getHoraFin());
        }

}