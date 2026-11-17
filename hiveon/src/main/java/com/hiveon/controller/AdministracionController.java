package com.hiveon.controller;

import java.security.Principal;

import com.hiveon.model.Administracion;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/administracion")
class AdministracionController {

    private final Administracion modeloDatos;

    AdministracionController(Administracion modeloDatos) {
        this.modeloDatos = modeloDatos;
    }

    @GetMapping
    String inicio(Principal usuario, Model modelo) {
        var datos = modeloDatos.inicio(usuario);
        modelo.addAttribute("cuentas", datos.cuentas());
        modelo.addAttribute("tipos", datos.tipos());
        modelo.addAttribute("catalogoRoles", datos.catalogoRoles());
        return "sprint3/administracion";
    }

    @GetMapping("/usuarios/{id}")
    String usuario(@PathVariable long id, Principal usuario, Model modelo) {
        var datos = modeloDatos.usuario(id, usuario);
        modelo.addAttribute("cuenta", datos.cuenta());
        modelo.addAttribute("catalogoRoles", datos.catalogoRoles());
        return "sprint3/usuario";
    }

    @PostMapping("/usuarios")
    public String crearUsuario(Principal usuario, @RequestParam String nombre, @RequestParam String correo,
                                @RequestParam String rol, @RequestParam String clave) {
        long id = modeloDatos.crearUsuario(usuario, nombre, correo, rol, clave);
        return "redirect:/administracion/usuarios/" + id;
    }

    @PostMapping("/usuarios/{id}")
    public String actualizarUsuario(@PathVariable long id, Principal usuario, @RequestParam String nombre,
                                     @RequestParam String correo, @RequestParam String rol,
                                     @RequestParam(defaultValue = "false") boolean habilitado,
                                     @RequestParam(defaultValue = "") String clave) {
        modeloDatos.actualizarUsuario(id, usuario, nombre, correo, rol, habilitado, clave);
        return "redirect:/administracion";
    }

    @PostMapping("/tipos-colmena")
    public String crearTipo(Principal usuario, @RequestParam String nombre) {
        modeloDatos.crearTipo(usuario, nombre);
        return "redirect:/administracion";
    }

    @PostMapping("/tipos-colmena/{id}")
    public String editarTipo(@PathVariable long id, Principal usuario, @RequestParam String nombre,
                             @RequestParam(defaultValue = "false") boolean habilitado) {
        modeloDatos.editarTipo(id, usuario, nombre, habilitado);
        return "redirect:/administracion";
    }

    @GetMapping("/auditoria")
    String auditoria(Principal usuario, Model modelo, @RequestParam(defaultValue = "1") int pagina) {
        var datos = modeloDatos.auditoria(usuario, pagina);
        modelo.addAttribute("eventos", datos.eventos());
        modelo.addAttribute("pagina", datos.pagina());
        modelo.addAttribute("haySiguiente", datos.haySiguiente());
        return "sprint3/auditoria";
    }
}
