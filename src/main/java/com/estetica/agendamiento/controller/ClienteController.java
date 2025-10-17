package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.dto.ClienteResponseDTO;
import com.estetica.agendamiento.dto.SesionesRestantesDTO;
import com.estetica.agendamiento.model.Cliente;
import com.estetica.agendamiento.model.ClientePaqueteTratamiento;
import com.estetica.agendamiento.repository.ClienteRepository;
import com.estetica.agendamiento.repository.ClientePaqueteTratamientoRepository;
import com.estetica.agendamiento.service.ClienteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteRepository clienteRepository;
    private final ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository;
    private final ClienteService clienteService;

    public ClienteController(ClienteRepository clienteRepository,
            ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository,
            ClienteService clienteService) {
        this.clienteRepository = clienteRepository;
        this.clientePaqueteTratamientoRepository = clientePaqueteTratamientoRepository;
        this.clienteService = clienteService;
    }

    @GetMapping
    public ResponseEntity<List<Cliente>> listarClientes() {
        return ResponseEntity.ok(clienteRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cliente> obtenerCliente(@PathVariable Long id) {
        return clienteRepository.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Cliente> crearCliente(@RequestBody Cliente cliente) {
        return ResponseEntity.ok(clienteRepository.save(cliente));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cliente> actualizarCliente(@PathVariable Long id,
            @RequestBody Cliente cliente) {
        return clienteRepository.findById(id).map(existing -> {
            cliente.setId(id);
            return ResponseEntity.ok(clienteRepository.save(cliente));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Long id) {
        if (clienteRepository.existsById(id)) {
            clienteRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{clienteId}/tratamientos/{tratamientoId}/sesiones-restantes")
    public ResponseEntity<SesionesRestantesDTO> getSesionesRestantes(@PathVariable Long clienteId,
            @PathVariable Long tratamientoId) {

        SesionesRestantesDTO dto =
                clienteService.obtenerSesionesRestantes(clienteId, tratamientoId);

        if (dto == null) {
            dto = new SesionesRestantesDTO(clienteId, tratamientoId, null, 0, "no_tiene");
        }

        return ResponseEntity.ok(dto);
    }

    // ==============================
    // 🔹 Endpoint tratamientos disponibles
    // ==============================
    @GetMapping("/{id}/tratamientos-disponibles")
    public ResponseEntity<List<Map<String, Object>>> getTratamientosDisponibles(
            @PathVariable Long id) {
        List<ClientePaqueteTratamiento> cpts =
                clientePaqueteTratamientoRepository.encontrarVigentesConSaldo(id, LocalDate.now());

        List<Map<String, Object>> response = cpts.stream().map(cpt -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", cpt.getTratamiento().getId());
            map.put("nombre", cpt.getTratamiento().getNombre());
            map.put("sesionesRestantes", cpt.getSesionesRestantes());
            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }

    // Buscar cliente por teléfono
    @GetMapping("/by-telefono/{telefono}")
    public ResponseEntity<ClienteResponseDTO> getClienteByTelefono(@PathVariable String telefono) {
        return clienteService.getClienteByTelefono(telefono).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Buscar cliente por documento
    @GetMapping("/by-documento/{documento}")
    public ClienteResponseDTO getByDocumento(@PathVariable String documento) {
        return clienteService.findByDocumento(documento);
    }

    // Buscar cliente por nombre (parcial o completo)
    @GetMapping("/by-nombre/{nombre}")
    public ClienteResponseDTO getByNombre(@PathVariable String nombre) {
        return clienteService.findByNombre(nombre);
    }

    // 🔹 Nuevo endpoint: listar solo clientes con paquetes asignados
    @GetMapping("/con-paquetes")
    public ResponseEntity<List<Cliente>> listarClientesConPaquetes() {
        List<Cliente> clientes = clienteRepository.findClientesConPaquetes();
        return ResponseEntity.ok(clientes);
    }
}
