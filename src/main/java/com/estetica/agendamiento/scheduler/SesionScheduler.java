package com.estetica.agendamiento.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.estetica.agendamiento.service.SesionService;

@Component
public class SesionScheduler {

    @Autowired
    private SesionService sesionService;

    // Ejecutar todos los días a las 22:00 (hora del servidor)
    @Scheduled(cron = "0 0 20 * * *")
    public void marcarSesionesPerdidas() {
        System.out
                .println("⏰ Ejecutando tarea diaria: marcar sesiones pendientes como perdidas...");
        sesionService.marcarSesionesPendientesComoPerdidas();
    }
}
