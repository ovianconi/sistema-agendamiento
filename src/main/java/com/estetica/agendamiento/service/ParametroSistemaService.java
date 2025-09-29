package com.estetica.agendamiento.service;

import com.estetica.agendamiento.model.ParametroSistema;
import com.estetica.agendamiento.repository.ParametroSistemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParametroSistemaService {

    private final ParametroSistemaRepository repository;

    public String getParametro(String clave, String valorPorDefecto) {
        return repository.findByClave(clave)
                .map(ParametroSistema::getValor)
                .orElse(valorPorDefecto);
    }

    public int getParametroEntero(String clave, int valorPorDefecto) {
        try {
            return Integer.parseInt(getParametro(clave, String.valueOf(valorPorDefecto)));
        } catch (NumberFormatException e) {
            return valorPorDefecto;
        }
    }
}
