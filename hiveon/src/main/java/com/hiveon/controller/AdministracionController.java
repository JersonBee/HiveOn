package com.hiveon.controller;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/administracion")
class AdministracionController {

    private static final int EVENTOS_POR_PAGINA = 40;
    private final JdbcClient jdbc;
    private final PasswordEncoder contrasenas;
    private final AccesoModulos acceso;

    AdministracionController(JdbcClient jdbc, PasswordEncoder contrasenas) {
        this.jdbc = jdbc;
        this.contrasenas = contrasenas;
        this.acceso = new AccesoModulos(jdbc);
    }

    @GetMapping
    String inicio(Principal usuario, Model modelo) {
        acceso.cuenta(usuario, "ADMINISTRADOR", false);
        modelo.addAttribute("cuentas", jdbc.sql("""
                select id,nombre,correo,rol_codigo,habilitado from hiveon.usuarios order by nombre,id
                """).query().listOfRows());
        modelo.addAttribute("tipos", jdbc.sql("""
                select id,nombre,habilitado from hiveon.tipos_colmena order by nombre,id
                """).query().listOfRows());
        cargarRoles(modelo);
        return "sprint3/administracion";
    }

    @GetMapping("/usuarios/{id}")
    String usuario(@PathVariable long id, Principal usuario, Model modelo) {
        acceso.cuenta(usuario, "ADMINISTRADOR", false);
        modelo.addAttribute("cuenta", jdbc.sql("""
                select id,nombre,correo,rol_codigo,habilitado from hiveon.usuarios where id=:id
                """).param("id", id).query().listOfRows().stream().findFirst()
                .orElseThrow(AccesoModulos::noEncontrado));
        cargarRoles(modelo);
        return "sprint3/usuario";
    }

    @PostMapping("/usuarios")
    @Transactional
    public String crearUsuario(Principal usuario, @RequestParam String nombre, @RequestParam String correo,
                                @RequestParam String rol, @RequestParam String clave) {
        long actor = acceso.cuenta(usuario, "ADMINISTRADOR", false);
        long id = jdbc.sql("""
                insert into hiveon.usuarios(rol_codigo,nombre,correo,contrasena_hash)
                values(:rol,:nombre,:correo,:hash) returning id
                """).param("rol", rol).param("nombre", AccesoModulos.requerido(nombre, 160))
                .param("correo", correoValido(correo)).param("hash", codificar(clave)).query(Long.class).single();
        acceso.auditar(actor, "CREAR_CUENTA", "USUARIO", id);
        return "redirect:/administracion/usuarios/" + id;
    }

    @PostMapping("/usuarios/{id}")
    @Transactional
    public String actualizarUsuario(@PathVariable long id, Principal usuario, @RequestParam String nombre,
                                     @RequestParam String correo, @RequestParam String rol,
                                     @RequestParam(defaultValue = "false") boolean habilitado,
                                     @RequestParam(defaultValue = "") String clave) {
        long actor = acceso.cuenta(usuario, "ADMINISTRADOR", false);
        if (actor == id && (!habilitado || !rol.equals("ADMINISTRADOR"))) {
            throw AccesoModulos.invalido("Para cambiar tu rol o inhabilitar tu propia cuenta solicita a otro administrador.");
        }
        int filas = jdbc.sql("""
                update hiveon.usuarios set nombre=:nombre,correo=:correo,rol_codigo=:rol,
                    habilitado=:habilitado,contrasena_hash=coalesce(:hash,contrasena_hash)
                where id=:id
                """).param("id", id).param("nombre", AccesoModulos.requerido(nombre, 160))
                .param("correo", correoValido(correo)).param("rol", rol).param("habilitado", habilitado)
                .param("hash", clave.isEmpty() ? null : codificar(clave), java.sql.Types.VARCHAR).update();
        if (filas == 0) {
            throw AccesoModulos.noEncontrado();
        }
        acceso.auditar(actor, "ACTUALIZAR_CUENTA", "USUARIO", id);
        return "redirect:/administracion";
    }

    @PostMapping("/tipos-colmena")
    @Transactional
    public String crearTipo(Principal usuario, @RequestParam String nombre) {
        long actor = acceso.cuenta(usuario, "ADMINISTRADOR", true);
        long id = jdbc.sql("""
                insert into hiveon.tipos_colmena(nombre) values(:nombre) returning id
                """).param("nombre", AccesoModulos.requerido(nombre, 100)).query(Long.class).single();
        acceso.auditar(actor, "CREAR_TIPO_COLMENA", "TIPO_COLMENA", id);
        return "redirect:/administracion";
    }

    @PostMapping("/tipos-colmena/{id}")
    @Transactional
    public String editarTipo(@PathVariable long id, Principal usuario, @RequestParam String nombre,
                             @RequestParam(defaultValue = "false") boolean habilitado) {
        long actor = acceso.cuenta(usuario, "ADMINISTRADOR", true);
        int filas = jdbc.sql("""
                update hiveon.tipos_colmena set nombre=:nombre,habilitado=:habilitado where id=:id
                """).param("id", id).param("nombre", AccesoModulos.requerido(nombre, 100))
                .param("habilitado", habilitado).update();
        if (filas == 0) {
            throw AccesoModulos.noEncontrado();
        }
        acceso.auditar(actor, "ACTUALIZAR_TIPO_COLMENA", "TIPO_COLMENA", id);
        return "redirect:/administracion";
    }

    @GetMapping("/auditoria")
    String auditoria(Principal usuario, Model modelo, @RequestParam(defaultValue = "1") int pagina) {
        acceso.cuenta(usuario, "ADMINISTRADOR", false);
        if (pagina < 1 || pagina == Integer.MAX_VALUE) {
            throw AccesoModulos.invalido("Selecciona una página válida.");
        }
        var filas = jdbc.sql("""
                select e.accion,e.recurso,e.recurso_id,e.resultado,
                    coalesce(u.nombre,'Sistema') as actor,
                    to_char(e.ocurrido_en at time zone 'America/Bogota','YYYY-MM-DD HH24:MI') as fecha
                from hiveon.eventos_auditoria e left join hiveon.usuarios u on u.id=e.actor_id
                order by e.ocurrido_en desc,e.id desc offset :desplazamiento limit :limite
                """).param("desplazamiento", (pagina - 1L) * EVENTOS_POR_PAGINA)
                .param("limite", EVENTOS_POR_PAGINA + 1).query().listOfRows();
        modelo.addAttribute("eventos", filas.stream().limit(EVENTOS_POR_PAGINA).toList());
        modelo.addAttribute("pagina", pagina);
        modelo.addAttribute("haySiguiente", filas.size() > EVENTOS_POR_PAGINA);
        return "sprint3/auditoria";
    }

    private void cargarRoles(Model modelo) {
        modelo.addAttribute("catalogoRoles", jdbc.sql("""
                select codigo,nombre from hiveon.roles order by nombre
                """).query().listOfRows());
    }

    private static String correoValido(String correo) {
        String valor = AccesoModulos.requerido(correo, 100);
        if (!valor.matches("[^\\s@]+@[^\\s@]+[.][^\\s@]+")) {
            throw AccesoModulos.invalido("Ingresa un correo válido de hasta 100 caracteres.");
        }
        return valor;
    }

    private String codificar(String clave) {
        if (clave.length() < 8 || clave.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw AccesoModulos.invalido("Usa al menos ocho caracteres y un máximo de 72 bytes para la contraseña.");
        }
        return contrasenas.encode(clave);
    }
}
