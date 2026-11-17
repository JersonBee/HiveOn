package com.hiveon.controller;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
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

    private static final String CONSULTA_AUTORIZADOS = """
            select a.id, a.nombre, a.vereda, m.nombre as municipio,
                   d.nombre as departamento, a.archivado_en is not null as archivado
            from hiveon.apiarios a
            cross join hiveon.usuarios u
            join hiveon.municipios m on m.codigo = a.municipio_codigo
            join hiveon.departamentos d on d.codigo = m.departamento_codigo
            where lower(u.correo) = lower(:correo) and u.habilitado
              and ((u.rol_codigo = 'APICULTOR' and u.id = a.propietario_id)
                or (u.rol_codigo in ('OPERARIO', 'TECNICO') and exists (
                    select 1 from hiveon.vinculaciones_apiario v
                    where v.apiario_id = a.id and v.usuario_id = u.id
                      and v.funcion = u.rol_codigo and v.retirada_en is null)))
            """;

    private final JdbcClient jdbc;

    ApiariosController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    String listar(Authentication usuario, Model model) {
        model.addAttribute("puedeRegistrar", usuario.getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_APICULTOR")));
        model.addAttribute("apiarios", jdbc.sql(CONSULTA_AUTORIZADOS + " order by a.nombre, a.id")
                .param("correo", usuario.getName())
                .query(Apiario.class)
                .list());
        return "apiarios/lista";
    }

    @GetMapping("/{id}")
    String consultar(@PathVariable long id, Principal usuario, Model model) {
        Apiario apiario = jdbc.sql(CONSULTA_AUTORIZADOS + " and a.id = :id")
                .param("correo", usuario.getName())
                .param("id", id)
                .query(Apiario.class)
                .optional()
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
            int registrados = jdbc.sql("""
                    insert into hiveon.apiarios (propietario_id, nombre, municipio_codigo, vereda)
                    select u.id, :nombre, m.codigo, nullif(:vereda, '')
                    from hiveon.usuarios u
                    join hiveon.municipios m on m.codigo = :municipio
                        and m.departamento_codigo = :departamento
                    where lower(u.correo) = lower(:correo) and u.habilitado
                      and u.rol_codigo = 'APICULTOR'
                    """)
                    .param("correo", usuario.getName())
                    .param("nombre", formulario.nombre().strip())
                    .param("vereda", formulario.vereda() == null ? "" : formulario.vereda().strip())
                    .param("municipio", formulario.municipio())
                    .param("departamento", formulario.departamento())
                    .update();
            if (registrados == 1) {
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
        List<Ubicacion> departamentos = jdbc.sql("""
                select codigo, nombre from hiveon.departamentos order by nombre
                """).query(Ubicacion.class).list();
        boolean seleccionado = departamentos.stream().anyMatch(d -> d.codigo().equals(departamento));
        model.addAttribute("departamentos", departamentos);
        model.addAttribute("departamentoSeleccionado", seleccionado);
        model.addAttribute("departamentoInvalido",
                !seleccionado && (intentoDeRegistro || departamento != null && !departamento.isBlank()));
        model.addAttribute("municipios", seleccionado
                ? jdbc.sql("""
                        select codigo, nombre from hiveon.municipios
                        where departamento_codigo = :departamento order by nombre
                        """).param("departamento", departamento).query(Ubicacion.class).list()
                : List.of());
    }

    public record Formulario(
            @NotBlank(message = "Ingresa el nombre del apiario.") String nombre,
            @Pattern(regexp = "([0-9]{2})?", message = "Selecciona un departamento.")
            @NotBlank(message = "Selecciona un departamento.") String departamento,
            @Pattern(regexp = "([0-9]{5})?", message = "Selecciona un municipio.")
            @NotBlank(message = "Selecciona un municipio.") String municipio,
            String vereda) {
    }

    public record Ubicacion(String codigo, String nombre) {
    }

    public record Apiario(long id, String nombre, String vereda, String municipio, String departamento,
                          boolean archivado) {
    }
}
