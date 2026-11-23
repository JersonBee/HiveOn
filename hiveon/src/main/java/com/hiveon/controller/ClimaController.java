package com.hiveon.controller;

import java.security.Principal;

import com.hiveon.model.Apiarios;
import com.hiveon.model.Clima;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
class ClimaController {
    private final Apiarios apiarios;
    private final Clima clima;

    ClimaController(Apiarios apiarios, Clima clima) {
        this.apiarios = apiarios;
        this.clima = clima;
    }

    @GetMapping("/apiarios/{id}/clima")
    String consultar(@PathVariable long id, Principal usuario, Model modelo) {
        var apiario = apiarios.consultar(id, usuario.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        modelo.addAttribute("apiario", apiario);
        modelo.addAttribute("clima", clima.consultar(apiario.municipio(), apiario.departamento()));
        return "apiarios/clima";
    }
}
