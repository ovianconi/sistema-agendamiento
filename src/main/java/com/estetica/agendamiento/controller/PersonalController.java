package com.estetica.agendamiento.controller;

import com.estetica.agendamiento.model.Personal;
import com.estetica.agendamiento.service.PersonalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/personales")
@RequiredArgsConstructor
public class PersonalController {

    private final PersonalService personalService;

    @GetMapping
    public Page<Personal> getAll(@PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return personalService.findAll(pageable);
    }

    @PostMapping
    public ResponseEntity<Personal> create(@Valid @RequestBody Personal personal) {
        return ResponseEntity.ok(personalService.save(personal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Personal> update(@PathVariable Long id, @Valid @RequestBody Personal personal) {
        return ResponseEntity.ok(personalService.update(id, personal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        personalService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
