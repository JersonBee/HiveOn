package com.hiveon.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class AccesoController {

    @GetMapping("/")
    String bienvenida() {
        return "index";
    }

    @GetMapping("/ingresar")
    String ingresar(Authentication usuario) {
        if (usuario != null && usuario.isAuthenticated()
                && !(usuario instanceof AnonymousAuthenticationToken)) {
            return "redirect:/inicio";
        }
        return "ingresar";
    }

    @GetMapping("/inicio")
    String inicio(Authentication usuario, Model model) {
        model.addAttribute("puedeConsultarApiarios", usuario.getAuthorities().stream()
                .anyMatch(autoridad -> java.util.Set.of("ROLE_APICULTOR", "ROLE_OPERARIO", "ROLE_TECNICO")
                        .contains(autoridad.getAuthority())));
        model.addAttribute("roles", AuthorityUtils.authorityListToSet(usuario.getAuthorities()));
        return "inicio";
    }
}
