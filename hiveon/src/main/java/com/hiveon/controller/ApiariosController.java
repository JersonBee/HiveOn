package com.hiveon.controller;

import java.security.Principal;

import com.hiveon.model.Apiarios;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/apiarios")
class ApiariosController {

    private final Apiarios apiarios;

    ApiariosController(Apiarios apiarios) {
        this.apiarios = apiarios;
    }

    @GetMapping
    String listar(Authentication usuario, Model model) {
        model.addAttribute("puedeRegistrar", usuario.getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_APICULTOR")));
        model.addAttribute("apiarios", apiarios.listar(usuario.getName()));
        return "apiarios/lista";
    }

    @GetMapping("/{id}")
    String consultar(@PathVariable long id, Principal usuario, Model model) {
        var apiario = apiarios.consultar(id, usuario.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("apiario", apiario);
        return "apiarios/detalle";
    }

    @GetMapping("/nuevo")
    String nuevo(@RequestParam(defaultValue = "") String departamento, Model model) {
        model.addAttribute("formulario", new Formulario("", departamento, "", ""));
        cargarUbicaciones(departamento, false, model);
        return "apiarios/nuevo";
    }

    @PostMapping
    String registrar(@Valid @ModelAttribute("formulario") Formulario formulario, BindingResult errores,
                     Principal usuario, Model model, RedirectAttributes redirect) {
        if (!errores.hasErrors()) {
            if (apiarios.registrar(usuario.getName(), formulario.nombre(), formulario.departamento(),
                    formulario.municipio(), formulario.vereda())) {
                redirect.addFlashAttribute("registrado", true);
                return "redirect:/apiarios";
            }
            errores.reject("apiario.noRegistrado",
                    "No se pudo registrar el apiario. Verifica la ubicación y que tu cuenta esté habilitada.");
        }
        cargarUbicaciones(formulario.departamento(), true, model);
        return "apiarios/nuevo";
    }

    private void cargarUbicaciones(String departamento, boolean intentoDeRegistro, Model model) {
        var ubicaciones = apiarios.ubicaciones(departamento);
        boolean seleccionado = ubicaciones.seleccionado();
        model.addAttribute("departamentos", ubicaciones.departamentos());
        model.addAttribute("departamentoSeleccionado", seleccionado);
        model.addAttribute("departamentoInvalido",
                !seleccionado && (intentoDeRegistro || departamento != null && !departamento.isBlank()));
        model.addAttribute("municipios", ubicaciones.municipios());
    }

    public record Formulario(
            @NotBlank(message = "Ingresa el nombre del apiario.") String nombre,
            @Pattern(regexp = "([0-9]{2})?", message = "Selecciona un departamento.")
            @NotBlank(message = "Selecciona un departamento.") String departamento,
            @Pattern(regexp = "([0-9]{5})?", message = "Selecciona un municipio.")
            @NotBlank(message = "Selecciona un municipio.") String municipio,
            String vereda) {
    }
}
