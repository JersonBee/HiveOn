package com.hiveon.model;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Administracion {

    private static final int EVENTOS_POR_PAGINA = 40;
    private final JdbcClient jdbc;
    private final PasswordEncoder contrasenas;
    private final AccesoModulos acceso;

    Administracion(JdbcClient jdbc, AccesoModulos acceso, PasswordEncoder contrasenas) {
        this.jdbc = jdbc;
        this.contrasenas = contrasenas;
        this.acceso = acceso;
    }

    public InicioDatos inicio(Principal usuario) {
        acceso.cuenta(usuario, "ADMINISTRADOR", false);
        var cuentas = jdbc.sql("""
                select id,nombre,correo,rol_codigo,habilitado from hiveon.usuarios order by nombre,id
                """).query().listOfRows();
        var tipos = jdbc.sql("""
                select id,nombre,habilitado from hiveon.tipos_colmena order by nombre,id
                """).query().listOfRows();
        var catalogoRoles = roles();
        return new InicioDatos(cuentas, tipos, catalogoRoles);
    }

    public UsuarioDatos usuario(long id, Principal usuario) {
        acceso.cuenta(usuario, "ADMINISTRADOR", false);
        var cuenta = jdbc.sql("""
                select id,nombre,correo,rol_codigo,habilitado from hiveon.usuarios where id=:id
                """).param("id", id).query().listOfRows().stream().findFirst()
                .orElseThrow(AccesoModulos::noEncontrado);
        var catalogoRoles = roles();
        return new UsuarioDatos(cuenta, catalogoRoles);
    }

    @Transactional
    public long crearUsuario(Principal usuario, String nombre, String correo, String rol, String clave) {
        long actor = acceso.cuenta(usuario, "ADMINISTRADOR", false);
        long id = jdbc.sql("""
                insert into hiveon.usuarios(rol_codigo,nombre,correo,contrasena_hash)
                values(:rol,:nombre,:correo,:hash) returning id
                """).param("rol", rol).param("nombre", AccesoModulos.requerido(nombre, 160))
                .param("correo", correoValido(correo)).param("hash", codificar(clave)).query(Long.class).single();
        acceso.auditar(actor, "CREAR_CUENTA", "USUARIO", id);
        return id;
    }

    @Transactional
public void actualizarUsuario(long id, Principal usuario, String nombre, String correo, String rol,
                              boolean habilitado, String clave) {
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
    }

    @Transactional
    public void crearTipo(Principal usuario, String nombre) {
        long actor = acceso.cuenta(usuario, "ADMINISTRADOR", true);
        long id = jdbc.sql("""
                insert into hiveon.tipos_colmena(nombre) values(:nombre) returning id
                """).param("nombre", AccesoModulos.requerido(nombre, 100)).query(Long.class).single();
        acceso.auditar(actor, "CREAR_TIPO_COLMENA", "TIPO_COLMENA", id);
    }

    @Transactional
    public void editarTipo(long id, Principal usuario, String nombre, boolean habilitado) {
        long actor = acceso.cuenta(usuario, "ADMINISTRADOR", true);
        int filas = jdbc.sql("""
                update hiveon.tipos_colmena set nombre=:nombre,habilitado=:habilitado where id=:id
                """).param("id", id).param("nombre", AccesoModulos.requerido(nombre, 100))
                .param("habilitado", habilitado).update();
        if (filas == 0) {
            throw AccesoModulos.noEncontrado();
        }
        acceso.auditar(actor, "ACTUALIZAR_TIPO_COLMENA", "TIPO_COLMENA", id);
    }

    public AuditoriaDatos auditoria(Principal usuario, int pagina) {
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
        var eventos = filas.stream().limit(EVENTOS_POR_PAGINA).toList();

        var haySiguiente = filas.size() > EVENTOS_POR_PAGINA;
        return new AuditoriaDatos(eventos, pagina, haySiguiente);
    }

    private List<Map<String, Object>> roles() {
        return jdbc.sql("""
                select codigo,nombre from hiveon.roles order by nombre
                """).query().listOfRows();
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

    public record InicioDatos(
            List<Map<String, Object>> cuentas, List<Map<String, Object>> tipos,
            List<Map<String, Object>> catalogoRoles) {}

    public record UsuarioDatos(
            Map<String, Object> cuenta, List<Map<String, Object>> catalogoRoles) {}

    public record AuditoriaDatos(
            List<Map<String, Object>> eventos, int pagina, boolean haySiguiente) {}
}
