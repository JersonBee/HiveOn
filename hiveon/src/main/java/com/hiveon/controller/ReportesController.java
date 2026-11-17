package com.hiveon.controller;

import java.security.Principal;

import com.hiveon.model.Reportes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class ReportesController {

    private final Reportes modeloDatos;

    ReportesController(Reportes modeloDatos) {
        this.modeloDatos = modeloDatos;
    }

    @GetMapping("/apiarios/{id}/reportes")
    String reportes(@PathVariable long id, Principal usuario, Model modelo,
                    @RequestParam(defaultValue = "sanitario") String tipo) {
        var datos = modeloDatos.reportes(id, usuario, tipo);
        modelo.addAttribute("apiario", datos.apiario());
        modelo.addAttribute("tipo", datos.tipo());
        modelo.addAttribute("filas", datos.filas());
        return "sprint3/reportes";
    }
}
