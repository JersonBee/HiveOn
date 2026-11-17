package com.hiveon.controller;

import java.security.Principal;

import com.hiveon.model.RevisionSanitaria;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class RevisionSanitariaController {

    private final RevisionSanitaria modeloDatos;

    RevisionSanitariaController(RevisionSanitaria modeloDatos) {
        this.modeloDatos = modeloDatos;
    }

    @GetMapping("/apiarios/{id}/expedientes")
    String propios(@PathVariable long id, Principal usuario, Model modelo) {
        var datos = modeloDatos.propios(id, usuario);
        modelo.addAttribute("apiario", datos.apiario());
        modelo.addAttribute("expedientes", datos.expedientes());
        return "sprint3/expedientes-propios";
    }

    @PostMapping("/apiarios/{id}/expedientes")
    public String remitir(@PathVariable long id, Principal usuario, @RequestParam String descripcion) {
        modeloDatos.remitir(id, usuario, descripcion);
        return "redirect:/apiarios/" + id + "/expedientes";
    }

    @GetMapping("/expedientes")
    String remitidos(Principal usuario, Model modelo) {
        modelo.addAttribute("expedientes", modeloDatos.remitidos(usuario));
        return "sprint3/expedientes";
    }

    @GetMapping("/expedientes/{id}")
    String expediente(@PathVariable long id, Principal usuario, Model modelo) {
        var datos = modeloDatos.expediente(id, usuario);
        modelo.addAttribute("expediente", datos.expediente());
        modelo.addAttribute("antecedentes", datos.antecedentes());
        return "sprint3/expediente";
    }

    @PostMapping("/expedientes/{id}/resolver")
    public String resolver(@PathVariable long id, Principal usuario, @RequestParam String decision,
                           @RequestParam(defaultValue = "") String observaciones) {
        modeloDatos.resolver(id, usuario, decision, observaciones);
        return "redirect:/expedientes/" + id;
    }
}
