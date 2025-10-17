package com.estetica.agendamiento.service;

import com.estetica.agendamiento.dto.ClienteResponseDTO;
import com.estetica.agendamiento.dto.SesionesRestantesDTO;
import com.estetica.agendamiento.model.Cliente;
import com.estetica.agendamiento.model.ClientePaqueteTratamiento;
import com.estetica.agendamiento.repository.ClientePaqueteTratamientoRepository;
import com.estetica.agendamiento.repository.ClienteRepository;
import com.estetica.agendamiento.repository.TratamientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository;
    private final TratamientoRepository tratamientoRepository;

    public ClienteService(ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository,
            TratamientoRepository tratamientoRepository, ClienteRepository clienteRepository) {
        this.clientePaqueteTratamientoRepository = clientePaqueteTratamientoRepository;
        this.tratamientoRepository = tratamientoRepository;
        this.clienteRepository = clienteRepository;
    }

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

    /**
     * Devuelve la cantidad de sesiones restantes del tratamiento especificado para el cliente. Si
     * no hay registros vigentes, devuelve 0.
     */
    public int getSesionesRestantes(Long clienteId, Long tratamientoId) {
        var tratamiento = tratamientoRepository.findById(tratamientoId)
                .orElseThrow(() -> new RuntimeException("Tratamiento no encontrado"));

        var cptOpt = clientePaqueteTratamientoRepository
                .findByClienteAndTratamientoConVigencia(clienteId, tratamientoId, LocalDate.now());

        return cptOpt.map(ClientePaqueteTratamiento::getSesionesRestantes).orElse(0);
    }

    public Optional<ClienteResponseDTO> getClienteByTelefono(String telefono) {
        String normalized = normalizePhone(telefono);
        return clienteRepository.findByTelefono(normalized).map(this::toDTO);
    }

    /**
     * Normaliza el número de teléfono para Paraguay. Ejemplo: +595981123456 → 0981123456
     * 595981123456 → 0981123456 981123456 → 0981123456
     */
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

    private ClienteResponseDTO toDTO(Cliente cliente) {
        ClienteResponseDTO dto = new ClienteResponseDTO();
        dto.setId(cliente.getId());
        dto.setNombre(cliente.getNombre());
        dto.setApellido(cliente.getApellido());
        dto.setTelefono(cliente.getTelefono());
        dto.setDocumento(cliente.getDocumento());
        dto.setEmail(cliente.getCorreo());
        return dto;
    }

    public ClienteResponseDTO findByDocumento(String documento) {
        return clienteRepository.findByDocumento(documento).map(ClienteResponseDTO::fromEntity)
                .orElse(null);
    }

    public ClienteResponseDTO findByNombre(String nombre) {
        List<Cliente> lista = clienteRepository.findByNombreCompleto(nombre);
        if (lista.isEmpty())
            return null;
        if (lista.size() == 1)
            return ClienteResponseDTO.fromEntity(lista.get(0));

        // si hay varios, devolver el primero y loguear aviso
        System.out.println("⚠️ Múltiples coincidencias para nombre: " + nombre);
        return ClienteResponseDTO.fromEntity(lista.get(0));
    }

    public SesionesRestantesDTO obtenerSesionesRestantes(Long clienteId, Long tratamientoId) {
        var tratamiento = tratamientoRepository.findById(tratamientoId)
                .orElseThrow(() -> new RuntimeException("Tratamiento no encontrado"));

        var vigente = clientePaqueteTratamientoRepository
                .findByClienteAndTratamientoConVigencia(clienteId, tratamientoId, LocalDate.now());

        if (vigente.isPresent()) {
            var entidad = vigente.get();
            return new SesionesRestantesDTO(clienteId, tratamientoId, tratamiento.getNombre(),
                    entidad.getSesionesRestantes(), "vigente");
        }

        boolean algunaVezTuvo = clientePaqueteTratamientoRepository
                .existsByClientePaquete_Cliente_IdAndTratamiento_Id(clienteId, tratamientoId);

        if (algunaVezTuvo) {
            return new SesionesRestantesDTO(clienteId, tratamientoId, tratamiento.getNombre(), 0,
                    "agotado");
        }

        return new SesionesRestantesDTO(clienteId, tratamientoId, tratamiento.getNombre(), 0,
                "no_tiene");
    }

}
