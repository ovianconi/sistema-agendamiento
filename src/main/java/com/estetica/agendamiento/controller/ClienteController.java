package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.dto.ClienteResponseDTO;
import com.estetica.agendamiento.dto.SesionesRestantesDTO;
import com.estetica.agendamiento.model.Cliente;
import com.estetica.agendamiento.model.ClientePaqueteTratamiento;
import com.estetica.agendamiento.repository.ClientePaqueteTratamientoRepository;
import com.estetica.agendamiento.service.ClienteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository;

    public ClienteController(ClientePaqueteTratamientoRepository clientePaqueteTratamientoRepository,
            ClienteService clienteService) {
        this.clientePaqueteTratamientoRepository = clientePaqueteTratamientoRepository;
        this.clienteService = clienteService;
    }

    // ---------- CRUD básico/front ----------
    @GetMapping
    public ResponseEntity<List<Cliente>> listarClientes() {
        return ResponseEntity.ok(clienteService.listarClientes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cliente> obtenerCliente(@PathVariable Long id) {
        return clienteService.obtenerCliente(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Cliente> crearCliente(@RequestBody Cliente cliente) {
        return ResponseEntity.ok(clienteService.guardarCliente(cliente));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cliente> actualizarCliente(@PathVariable Long id,
            @RequestBody Cliente cliente) {
        return clienteService.obtenerCliente(id)
                .map(existing -> {
                    cliente.setId(id);
                    return ResponseEntity.ok(clienteService.guardarCliente(cliente));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Long id) {
        clienteService.eliminarCliente(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Para el calendario (combo de clientes válidos) ----------
    @GetMapping("/con-paquetes")
    public ResponseEntity<List<Cliente>> listarClientesConPaquetes() {
        return ResponseEntity.ok(clienteService.listarClientesConPaquetes());
    }

    // ---------- Busquedas específicas para WhatsApp / IA ----------

    // ==============================
    // 🔹 Buscar cliente por teléfono normalizado
    // ==============================
    @GetMapping("/by-telefono/{telefono}")
    public ResponseEntity<?> getClienteByTelefono(@PathVariable String telefono) {
        // normalizar número antes de buscar
        String normalizado = ClientePhoneNormalizer.normalizePhone(telefono);

        return clienteService.getClienteByTelefono(normalizado)
                .<ResponseEntity<?>>map(cliente -> ResponseEntity.ok(toDto(cliente)))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "error", "NOT_FOUND",
                        "detalle", "No se encontró cliente con ese teléfono")));
    }

    // ==============================
    // 🔹 Buscar cliente por documento (CI)
    // ==============================
    @GetMapping("/by-documento/{documento}")
    public ResponseEntity<?> getByDocumento(@PathVariable String documento) {
        try {
            Cliente cliente = clienteService.findEntityByDocumento(documento);
            return ResponseEntity.ok(toDto(cliente));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "NOT_FOUND",
                    "detalle", "No se encontró cliente con ese documento"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "ERROR",
                    "detalle", e.getMessage()));
        }
    }

    // ==============================
    // 🔹 Buscar cliente por nombre flexible
    // Nota importante:
    // - Este endpoint devuelve 200 solo si hay UN candidato claro.
    // - Si hay 0 ó más de 1, devolvemos 404 con detalle.
    // ==============================
    @GetMapping("/by-nombre/{nombre}")
    public ResponseEntity<?> getByNombre(@PathVariable String nombre) {
        List<Cliente> coincidencias = clienteService.searchByNombreFlexible(nombre);

        if (coincidencias.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "NOT_FOUND",
                    "detalle", "No se encontró cliente con ese nombre"));
        }
        if (coincidencias.size() > 1) {
            // Podríamos devolver sugerencias, pero por ahora solo informamos ambigüedad
            return ResponseEntity.status(404).body(Map.of(
                    "error", "AMBIGUO",
                    "detalle", "Hay más de un cliente con ese nombre, necesito tu documento"));
        }

        return ResponseEntity.ok(toDto(coincidencias.get(0)));
    }

    // ---------- sesiones restantes ----------
    @GetMapping("/{clienteId}/tratamientos/{tratamientoId}/sesiones-restantes")
    public ResponseEntity<SesionesRestantesDTO> getSesionesRestantes(
            @PathVariable Long clienteId,
            @PathVariable Long tratamientoId) {

        SesionesRestantesDTO dto = clienteService.obtenerSesionesRestantes(clienteId, tratamientoId);

        if (dto == null) {
            dto = new SesionesRestantesDTO(clienteId, tratamientoId, null, 0, "no_tiene");
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{clienteId}/tratamientos/{tratamientoId}/sesiones-restantes-count")
    public ResponseEntity<Integer> getSesionesRestantesCount(
            @PathVariable Long clienteId,
            @PathVariable Long tratamientoId) {
        return ResponseEntity.ok(
                clienteService.getSesionesRestantesCount(clienteId, tratamientoId));
    }

    // sanity check
    @GetMapping("/now")
    public ResponseEntity<String> pingFecha() {
        return ResponseEntity.ok(LocalDate.now().toString());
    }

    // ==============================
    // 🔹 Endpoint tratamientos disponibles //
    // ==============================
    @GetMapping("/{id}/tratamientos-disponibles")
    public ResponseEntity<List<Map<String, Object>>> getTratamientosDisponibles(@PathVariable Long id) {
        List<ClientePaqueteTratamiento> cpts = clientePaqueteTratamientoRepository.encontrarVigentesConSaldo(id,
                LocalDate.now());
        List<Map<String, Object>> response = cpts.stream().map(cpt -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", cpt.getTratamiento().getId());
            map.put("nombre", cpt.getTratamiento().getNombre());
            map.put("sesionesRestantes", cpt.getSesionesRestantes());
            return map;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // ==============================
    // 🔹 Mapper chiquito → DTO que el bot espera
    // ==============================
    private ClienteResponseDTO toDto(Cliente c) {
        return new ClienteResponseDTO(
                c.getId(),
                c.getNombre(),
                c.getApellido(),
                c.getTelefono(),
                c.getDocumento(),
                c.getCorreo());
    }

    public class ClientePhoneNormalizer {
        /**
         * Normaliza un número paraguayo al formato 09XXXXXXXX.
         * Ej:
         * +595981123456 -> 0981123456
         * 595981123456 -> 0981123456
         * 981123456 -> 0981123456
         */
        public static String normalizePhone(String raw) {
            if (raw == null)
                return null;
            String onlyDigits = raw.replaceAll("\\D", ""); // sacar + espacios etc.

            // sacar prefijo país 595 si viene
            if (onlyDigits.startsWith("595")) {
                onlyDigits = onlyDigits.substring(3);
            }

            // asegurar que empiece con 0
            if (!onlyDigits.startsWith("0")) {
                onlyDigits = "0" + onlyDigits;
            }
            return onlyDigits;
        }
    }
}
