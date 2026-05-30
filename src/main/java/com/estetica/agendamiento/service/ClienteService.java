package com.estetica.agendamiento.service;

import com.estetica.agendamiento.dto.ClienteResponseDTO;
import com.estetica.agendamiento.dto.SesionesRestantesDTO;
import com.estetica.agendamiento.mapper.ClienteMapper;
import com.estetica.agendamiento.model.Cliente;
import com.estetica.agendamiento.model.ClientePaqueteTratamiento;
import com.estetica.agendamiento.repository.ClientePaqueteTratamientoRepository;
import com.estetica.agendamiento.repository.ClienteRepository;
import com.estetica.agendamiento.repository.TratamientoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.estetica.agendamiento.dto.TratamientoDisponibleDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository;
    private final TratamientoRepository tratamientoRepository;

    public ClienteService(
            ClienteRepository clienteRepository,
            ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository,
            TratamientoRepository tratamientoRepository) {
        this.clienteRepository = clienteRepository;
        this.clientePaqueteTratamientoRepository = clientePaqueteTratamientoRepository;
        this.tratamientoRepository = tratamientoRepository;
    }

    // -------------------------------------------------
    // helper interno
    // -------------------------------------------------
    private ClienteResponseDTO toDto(Cliente c) {
        return ClienteResponseDTO.fromEntity(c);
    }

    // Normalizador de teléfono estilo PY
    private String normalizePhone(String telefono) {
        if (telefono == null)
            return null;
        String num = telefono.trim().replaceAll("\\s+", "");

        if (num.startsWith("+595")) {
            num = "0" + num.substring(4);
        } else if (num.startsWith("595")) {
            num = "0" + num.substring(3);
        } else if (!num.startsWith("0")) {
            num = "0" + num;
        }

        return num;
    }

    // -------------------------------------------------
    // CRUD / frontend
    // -------------------------------------------------
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    public Optional<Cliente> obtenerCliente(Long id) {
        return clienteRepository.findById(id);
    }

    public Cliente guardarCliente(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    public void eliminarCliente(Long id) {
        clienteRepository.deleteById(id);
    }

    public List<Cliente> listarClientesConPaquetes() {
        return clienteRepository.findClientesConPaquetes();
    }

    // -------------------------------------------------
    // búsquedas para WhatsApp / chatbot
    // -------------------------------------------------
    public Optional<Cliente> getClienteByTelefono(String telefonoNormalizado) {
        return clienteRepository.findByTelefono(telefonoNormalizado);
    }

    public Cliente findEntityByDocumento(String documento) {
        return clienteRepository.findByDocumento(documento)
                .orElseThrow(NoSuchElementException::new);
    }

    public List<Cliente> searchByNombreFlexible(String nombre) {
        return clienteRepository.searchByNombreFlexible(nombre.trim().toLowerCase());
    }

    // -------------------------------------------------
    // lógica de sesiones restantes
    // -------------------------------------------------
    public SesionesRestantesDTO obtenerSesionesRestantes(Long clienteId, Long tratamientoId) {

        var tratamiento = tratamientoRepository.findById(tratamientoId)
                .orElseThrow(() -> new RuntimeException("Tratamiento no encontrado"));

        var vigenteOpt = clientePaqueteTratamientoRepository
                .findByClienteAndTratamientoConVigencia(clienteId, tratamientoId, LocalDate.now());

        if (vigenteOpt.isPresent()) {
            ClientePaqueteTratamiento cpt = vigenteOpt.get();
            return new SesionesRestantesDTO(
                    clienteId,
                    tratamientoId,
                    tratamiento.getNombre(),
                    cpt.getSesionesRestantes(),
                    "vigente");
        }

        boolean algunaVezTuvo = clientePaqueteTratamientoRepository
                .existsByClientePaquete_Cliente_IdAndTratamiento_Id(clienteId, tratamientoId);

        if (algunaVezTuvo) {
            return new SesionesRestantesDTO(
                    clienteId,
                    tratamientoId,
                    tratamiento.getNombre(),
                    0,
                    "agotado");
        }

        return new SesionesRestantesDTO(
                clienteId,
                tratamientoId,
                tratamiento.getNombre(),
                0,
                "no_tiene");
    }

    public int getSesionesRestantesCount(Long clienteId, Long tratamientoId) {
        return clientePaqueteTratamientoRepository
                .findByClienteAndTratamientoConVigencia(clienteId, tratamientoId, LocalDate.now())
                .map(ClientePaqueteTratamiento::getSesionesRestantes)
                .orElse(0);
    }

    public List<TratamientoDisponibleDTO> obtenerTratamientosDisponibles(Long clienteId) {
        return clientePaqueteTratamientoRepository
                .encontrarVigentesConSaldo(clienteId, LocalDate.now())
                .stream()
                .map(cpt -> new TratamientoDisponibleDTO(
                        cpt.getTratamiento().getId(),
                        cpt.getTratamiento().getNombre(),
                        cpt.getSesionesRestantes()))
                .toList();
    }
}
