package com.hiveon.controller;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;

import com.hiveon.model.GestionApiarios;
import com.hiveon.model.OperacionRechazada;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/apiarios/{apiarioId}")
class GestionApiariosController {

    private final GestionApiarios modeloDatos;

    GestionApiariosController(GestionApiarios modeloDatos) {
        this.modeloDatos = modeloDatos;
    }

    @GetMapping("/gestion")
    public String gestion(@PathVariable long apiarioId, Principal usuario, Model modelo) {
        var datos = modeloDatos.gestion(apiarioId, usuario);
        modelo.addAttribute("apiarioId", datos.apiarioId());
        modelo.addAttribute("acceso", datos.acceso());
        modelo.addAttribute("hoy", datos.hoy());
        modelo.addAttribute("colmenas", datos.colmenas());
        modelo.addAttribute("tipos", datos.tipos());
        modelo.addAttribute("vinculos", datos.vinculos());
        modelo.addAttribute("tareas", datos.tareas());
        modelo.addAttribute("historialSanitario", datos.historialSanitario());
        modelo.addAttribute("recomendaciones", datos.recomendaciones());
        return "apiarios/gestion";
    }

    @GetMapping("/editar")
    public String editar(@PathVariable long apiarioId, Principal usuario, Model modelo,
                         @RequestParam(required = false) String departamento) {
        var datos = modeloDatos.editar(apiarioId, usuario, departamento);
        modelo.addAttribute("apiarioId", datos.apiarioId());
        modelo.addAttribute("acceso", datos.acceso());
        modelo.addAttribute("hoy", datos.hoy());
        modelo.addAttribute("datos", datos.datos());
        modelo.addAttribute("departamento", datos.departamento());
        modelo.addAttribute("departamentos", datos.departamentos());
        modelo.addAttribute("municipios", datos.municipios());
        return "apiarios/editar";
    }

    @PostMapping("/editar")
    public String actualizarApiario(@PathVariable long apiarioId, Principal usuario,
                                    @RequestParam String nombre, @RequestParam String departamento,
                                    @RequestParam String municipio, @RequestParam(defaultValue = "") String vereda) {
        modeloDatos.actualizarApiario(apiarioId, usuario, nombre, departamento, municipio, vereda);
        return "redirect:/apiarios/" + apiarioId;
    }

    @PostMapping("/archivar")
    public String archivarApiario(@PathVariable long apiarioId, Principal usuario) {
        modeloDatos.archivarApiario(apiarioId, usuario);
        return "redirect:/apiarios/" + apiarioId;
    }

    @PostMapping("/colmenas")
    public String crearColmena(@PathVariable long apiarioId, Principal usuario, @RequestParam String codigo,
                               @RequestParam(required = false) Long tipo,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate instalacion) {
        modeloDatos.crearColmena(apiarioId, usuario, codigo, tipo, instalacion);
        return regresar(apiarioId);
    }

    @GetMapping("/colmenas/{colmenaId}")
    public String verColmena(@PathVariable long apiarioId, @PathVariable long colmenaId,
                             Principal usuario, Model modelo) {
        var datos = modeloDatos.verColmena(apiarioId, colmenaId, usuario);
        modelo.addAttribute("apiarioId", datos.apiarioId());
        modelo.addAttribute("acceso", datos.acceso());
        modelo.addAttribute("hoy", datos.hoy());
        modelo.addAttribute("colmena", datos.colmena());
        modelo.addAttribute("tipos", datos.tipos());
        modelo.addAttribute("inspecciones", datos.inspecciones());
        modelo.addAttribute("tratamientos", datos.tratamientos());
        return "apiarios/colmena";
    }

    @PostMapping("/colmenas/{colmenaId}/editar")
    public String actualizarColmena(@PathVariable long apiarioId, @PathVariable long colmenaId, Principal usuario,
                                    @RequestParam String codigo, @RequestParam(required = false) Long tipo,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate instalacion) {
        modeloDatos.actualizarColmena(apiarioId, colmenaId, usuario, codigo, tipo, instalacion);
        return regresarColmena(apiarioId, colmenaId);
    }

    @PostMapping("/colmenas/{colmenaId}/archivar")
    public String archivarColmena(@PathVariable long apiarioId, @PathVariable long colmenaId, Principal usuario) {
        modeloDatos.archivarColmena(apiarioId, colmenaId, usuario);
        return regresarColmena(apiarioId, colmenaId);
    }

    @PostMapping("/vinculaciones")
    public String vincular(@PathVariable long apiarioId, Principal usuario, @RequestParam String correo) {
        modeloDatos.vincular(apiarioId, usuario, correo);
        return regresar(apiarioId);
    }

    @PostMapping("/vinculaciones/{vinculoId}/retirar")
    public String retirar(@PathVariable long apiarioId, @PathVariable long vinculoId, Principal usuario) {
        modeloDatos.retirar(apiarioId, vinculoId, usuario);
        return regresar(apiarioId);
    }

    @PostMapping("/colmenas/{colmenaId}/inspecciones")
    public String inspeccionar(@PathVariable long apiarioId, @PathVariable long colmenaId, Principal usuario,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                               @RequestParam String observaciones) {
        modeloDatos.inspeccionar(apiarioId, colmenaId, usuario, fecha, observaciones);
        return regresarColmena(apiarioId, colmenaId);
    }

    @PostMapping("/tareas")
    public String asignarTarea(@PathVariable long apiarioId, Principal usuario, @RequestParam long vinculo,
                               @RequestParam String descripcion,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimiento) {
        modeloDatos.asignarTarea(apiarioId, usuario, vinculo, descripcion, vencimiento);
        return regresar(apiarioId);
    }

    @PostMapping("/tareas/{tareaId}/estado")
    public String actualizarTarea(@PathVariable long apiarioId, @PathVariable long tareaId,
                                 Principal usuario, @RequestParam String estado) {
        modeloDatos.actualizarTarea(apiarioId, tareaId, usuario, estado);
        return regresar(apiarioId);
    }

    @PostMapping("/colmenas/{colmenaId}/tratamientos")
    public String tratar(@PathVariable long apiarioId, @PathVariable long colmenaId, Principal usuario,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                         @RequestParam String descripcion) {
        modeloDatos.tratar(apiarioId, colmenaId, usuario, fecha, descripcion);
        return regresarColmena(apiarioId, colmenaId);
    }

    @PostMapping("/recomendaciones")
    public String recomendar(@PathVariable long apiarioId, Principal usuario,
                             @RequestParam(required = false) Long colmena, @RequestParam String contenido) {
        modeloDatos.recomendar(apiarioId, usuario, colmena, contenido);
        return regresar(apiarioId);
    }

    private static String regresar(long apiarioId) {
        return "redirect:/apiarios/" + apiarioId + "/gestion";
    }

    private static String regresarColmena(long apiarioId, long colmenaId) {
        return "redirect:/apiarios/" + apiarioId + "/colmenas/" + colmenaId;
    }

    @ExceptionHandler(OperacionRechazada.class)
    void estadoOperacion(OperacionRechazada error, HttpServletResponse respuesta) throws IOException {
        respuesta.sendError(ErroresSprint3.estadoHttp(error).value());
    }

    @ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
    org.springframework.web.servlet.ModelAndView errorOperacion(Exception error, HttpServletRequest request) {
        String mensaje = error instanceof DataIntegrityViolationException
                ? "Revisa que los datos sean válidos y que el vínculo o código no esté duplicado."
                : error.getMessage();
        var vista = new org.springframework.web.servlet.ModelAndView("apiarios/operacion-invalida");
        vista.setStatus(HttpStatus.BAD_REQUEST);
        vista.addObject("mensaje", mensaje);
        String[] segmentos = request.getRequestURI().substring(request.getContextPath().length()).split("/");
        vista.addObject("regreso", "/apiarios/" + segmentos[2] + "/gestion");
        return vista;
    }
}
