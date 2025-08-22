package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.service.PersonalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/personal")
public class PersonalController {

    @Autowired
    private PersonalService personalService;

    @GetMapping
    public List<Personal> listarPersonal() {
        return personalService.listarPersonal();
    }

    @GetMapping("/{id}")
    public Optional<Personal> obtenerPersonal(@PathVariable Long id) {
        return personalService.obtenerPersonal(id);
    }

    @PostMapping
    public Personal crearPersonal(@RequestBody Personal personal) {
        return personalService.guardarPersonal(personal);
    }

    @DeleteMapping("/{id}")
    public void eliminarPersonal(@PathVariable Long id) {
        personalService.eliminarPersonal(id);
    }
}
