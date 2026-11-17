package com.hiveon.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import com.hiveon.model.Produccion;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class ProduccionController {

    private final Produccion modeloDatos;

    ProduccionController(Produccion modeloDatos) {
        this.modeloDatos = modeloDatos;
    }

    @GetMapping("/apiarios/{id}/produccion")
    String produccion(@PathVariable long id, Principal usuario, Model modelo) {
        var datos = modeloDatos.produccion(id, usuario);
        modelo.addAttribute("apiario", datos.apiario());
        modelo.addAttribute("hoy", datos.hoy());
        modelo.addAttribute("colmenas", datos.colmenas());
        modelo.addAttribute("cosechas", datos.cosechas());
        modelo.addAttribute("lotes", datos.lotes());
        return "sprint3/produccion";
    }

    @PostMapping("/apiarios/{id}/produccion/cosechas")
    public String cosechar(@PathVariable long id, Principal usuario,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                           @RequestParam BigDecimal cantidad, @RequestParam(required = false) Long colmena) {
        modeloDatos.cosechar(id, usuario, fecha, cantidad, colmena);
        return volver(id);
    }

    @PostMapping("/apiarios/{id}/produccion/lotes")
    public String crearLote(@PathVariable long id, Principal usuario, @RequestParam String codigo) {
        long lote = modeloDatos.crearLote(id, usuario, codigo);
        return volverLote(id, lote);
    }

    @GetMapping("/apiarios/{id}/produccion/lotes/{lote}")
    String lote(@PathVariable long id, @PathVariable long lote, Principal usuario, Model modelo) {
        var datos = modeloDatos.lote(id, lote, usuario);
        modelo.addAttribute("apiario", datos.apiario());
        modelo.addAttribute("lote", datos.lote());
        modelo.addAttribute("cosechas", datos.cosechas());
        return "sprint3/lote";
    }

    @PostMapping("/apiarios/{id}/produccion/lotes/{lote}/editar")
    public String editarLote(@PathVariable long id, @PathVariable long lote, Principal usuario,
                             @RequestParam String codigo, @RequestParam(required = false) List<Long> cosechas) {
        modeloDatos.editarLote(id, lote, usuario, codigo, cosechas);
        return volverLote(id, lote);
    }

    @PostMapping("/apiarios/{id}/produccion/lotes/{lote}/publicar")
    public String publicar(@PathVariable long id, @PathVariable long lote, Principal usuario) {
        modeloDatos.publicar(id, lote, usuario);
        return volverLote(id, lote);
    }

    @GetMapping("/lotes")
    String publicados(Principal usuario, Model modelo, @RequestParam(defaultValue = "") String codigo) {
        var datos = modeloDatos.publicados(usuario, codigo);
        modelo.addAttribute("codigo", datos.codigo());
        modelo.addAttribute("lotes", datos.lotes());
        return "sprint3/lotes-publicos";
    }

    @GetMapping("/lotes/{id}")
    String trazabilidad(@PathVariable long id, Principal usuario, Model modelo) {
        var datos = modeloDatos.trazabilidad(id, usuario);
        modelo.addAttribute("lote", datos.lote());
        modelo.addAttribute("fechas", datos.fechas());
        return "sprint3/trazabilidad";
    }

    private static String volver(long apiario) {
        return "redirect:/apiarios/" + apiario + "/produccion";
    }

    private static String volverLote(long apiario, long lote) {
        return volver(apiario) + "/lotes/" + lote;
    }
}
