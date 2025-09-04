// src/main/java/com/estetica/agendamiento/controller/DashboardController.java
package com.estetica.agendamiento.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.HashMap;

@RestController
public class DashboardController {

    @GetMapping("/api/dashboard/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("clientes", 120);         // aquí irían consultas reales a repositorios
        summary.put("personal", 15);
        summary.put("tratamientos", 8);
        summary.put("equipos", 10);
        summary.put("paquetes", 25);
        summary.put("sesionesAgendadas", 320);
        summary.put("sesionesRestantes", 150);
        return summary;
    }
}
