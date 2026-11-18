package com.hiveon.model;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GestionApiarios {

    private static final Set<String> ESTADOS_TAREA = Set.of("PENDIENTE", "EN_CURSO", "COMPLETADA");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final String ACCESO = """
            select u.id as usuario_id, u.rol_codigo as rol, a.nombre, a.archivado_en is not null as archivado
            from hiveon.apiarios a
            cross join hiveon.usuarios u
            where a.id = :apiario and lower(u.correo) = lower(:correo) and u.habilitado
              and ((u.rol_codigo = 'APICULTOR' and a.propietario_id = u.id)
                or (u.rol_codigo in ('OPERARIO', 'TECNICO') and exists (
                    select 1 from hiveon.vinculaciones_apiario v
                    where v.apiario_id = a.id and v.usuario_id = u.id
                      and v.funcion = u.rol_codigo and v.retirada_en is null)))
            """;

    private final JdbcClient jdbc;

    GestionApiarios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public GestionDatos gestion(long apiarioId, Principal usuario) {
        List<Map<String, Object>> tipos = List.of();
        List<Map<String, Object>> vinculos = List.of();
        List<Map<String, Object>> tareas = List.of();
        List<Map<String, Object>> historialSanitario = List.of();
        List<Map<String, Object>> recomendaciones = List.of();
        Acceso acceso = acceso(apiarioId, usuario, false);

        var hoy = LocalDate.now(BOGOTA);
        var colmenas = consulta("""
                select id, codigo, archivado_en is not null as archivada
                from hiveon.colmenas where apiario_id = :apiario order by codigo
                """, apiarioId).query().listOfRows();
        if (acceso.propietario()) {
            tipos = jdbc.sql("""
                    select id, nombre from hiveon.tipos_colmena where habilitado order by nombre
                    """).query().listOfRows();
            vinculos = consulta("""
                    select v.id, u.nombre, u.correo, v.funcion,
                           not u.habilitado or u.rol_codigo <> v.funcion as suspendido
                    from hiveon.vinculaciones_apiario v join hiveon.usuarios u on u.id=v.usuario_id
                    where v.apiario_id=:apiario and v.retirada_en is null order by u.nombre, v.id
                    """, apiarioId).query().listOfRows();
        }
        if (acceso.propietario() || acceso.operario()) {
            tareas = consulta("""
                    select t.id, t.descripcion, t.vencimiento, t.estado, u.nombre as operario,
                           v.retirada_en is not null as retirada
                    from hiveon.tareas t join hiveon.vinculaciones_apiario v on v.id=t.vinculacion_id
                    join hiveon.usuarios u on u.id=v.usuario_id
                    where t.apiario_id=:apiario
                      and (:propietario or (v.usuario_id=:usuario and v.retirada_en is null))
                    order by t.vencimiento, t.id
                    """, apiarioId).param("propietario", acceso.propietario())
                    .param("usuario", acceso.usuarioId()).query().listOfRows();
        }
        if (!acceso.operario()) {
            historialSanitario = consulta("""
                    select h.fecha, h.tipo, h.texto, c.codigo as colmena, u.nombre as autor
                    from (
                        select colmena_id, autor_id, fecha, 'Inspección' as tipo, observaciones as texto
                        from hiveon.inspecciones
                        union all
                        select colmena_id, autor_id, fecha, 'Tratamiento', descripcion
                        from hiveon.tratamientos
                    ) h join hiveon.colmenas c on c.id=h.colmena_id
                    join hiveon.usuarios u on u.id=h.autor_id
                    where c.apiario_id=:apiario order by h.fecha desc, c.codigo, h.tipo
                    """, apiarioId).query().listOfRows();
            recomendaciones = consulta("""
                    select r.contenido, c.codigo as colmena, u.nombre as autor,
                           (r.creada_en at time zone 'America/Bogota')::date as fecha
                    from hiveon.recomendaciones r
                    join hiveon.usuarios u on u.id=r.autor_id
                    left join hiveon.colmenas c on c.id=r.colmena_id
                    where r.apiario_id=:apiario order by r.creada_en desc, r.id desc
                    """, apiarioId).query().listOfRows();
        }
        return new GestionDatos(apiarioId, acceso, hoy, colmenas, tipos, vinculos, tareas,
                historialSanitario, recomendaciones);
    }

    public EditarDatos editar(long apiarioId, Principal usuario, String departamento) {
        Acceso acceso = acceso(apiarioId, usuario, false);
        exigir(acceso, "APICULTOR");

        var hoy = LocalDate.now(BOGOTA);
        var datos = consulta("""
                select a.nombre, a.vereda, m.codigo as municipio, m.departamento_codigo as departamento
                from hiveon.apiarios a join hiveon.municipios m on m.codigo=a.municipio_codigo
                where a.id=:apiario
                """, apiarioId).query().singleRow();
        String codigo = departamento == null ? (String) datos.get("departamento") : departamento;

        var departamentos = jdbc.sql("""
                select codigo, nombre from hiveon.departamentos order by nombre
                """).query().listOfRows();
        var municipios = jdbc.sql("""
                select codigo, nombre from hiveon.municipios where departamento_codigo=:codigo order by nombre
                """).param("codigo", codigo).query().listOfRows();
        return new EditarDatos(apiarioId, acceso, hoy, datos, codigo, departamentos, municipios);
    }

    @Transactional
public void actualizarApiario(long apiarioId, Principal usuario, String nombre, String departamento,
                              String municipio, String vereda) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        int filas = consulta("""
                update hiveon.apiarios a set nombre=:nombre, municipio_codigo=m.codigo, vereda=nullif(:vereda,'')
                from hiveon.municipios m where a.id=:apiario and m.codigo=:municipio
                     and m.departamento_codigo=:departamento
                """, apiarioId).param("nombre", requerido(nombre)).param("vereda", vereda.strip())
                .param("municipio", municipio).param("departamento", departamento).update();
        comprobar(filas, "Selecciona un municipio del departamento indicado.");
    }

    @Transactional
    public void archivarApiario(long apiarioId, Principal usuario) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        consulta("update hiveon.apiarios set archivado_en=current_timestamp where id=:apiario", apiarioId).update();
    }

    @Transactional
    public void crearColmena(long apiarioId, Principal usuario, String codigo, Long tipo, LocalDate instalacion) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        consulta("""
                insert into hiveon.colmenas(apiario_id,codigo,tipo_id,fecha_instalacion)
                values(:apiario,:codigo,:tipo,:instalacion)
                """, apiarioId).param("codigo", requerido(codigo)).param("tipo", tipo, java.sql.Types.BIGINT)
                .param("instalacion", fechaPasada(instalacion), java.sql.Types.DATE).update();
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public VerColmenaDatos verColmena(long apiarioId, long colmenaId, Principal usuario) {
        List<Map<String, Object>> tipos = List.of();
        List<Map<String, Object>> tratamientos = List.of();
        Acceso acceso = acceso(apiarioId, usuario, false);

        var hoy = LocalDate.now(BOGOTA);
        var colmena = colmena(apiarioId, colmenaId, false);
        if (acceso.propietario()) {
            tipos = jdbc.sql("""
                    select id, nombre from hiveon.tipos_colmena where habilitado order by nombre
                    """).query().listOfRows();
        }
        var inspecciones = jdbc.sql("""
                select i.fecha, i.observaciones, u.nombre as autor from hiveon.inspecciones i
                join hiveon.usuarios u on u.id=i.autor_id
                where i.colmena_id=:colmena order by i.fecha desc, i.id desc
                """).param("colmena", colmenaId).query().listOfRows();
        if (!acceso.operario()) {
            tratamientos = jdbc.sql("""
                    select t.fecha, t.descripcion, u.nombre as autor from hiveon.tratamientos t
                    join hiveon.usuarios u on u.id=t.autor_id
                    where t.colmena_id=:colmena order by t.fecha desc, t.id desc
                    """).param("colmena", colmenaId).query().listOfRows();
        }
        return new VerColmenaDatos(apiarioId, acceso, hoy, colmena, tipos, inspecciones, tratamientos);
    }

    @Transactional
public void actualizarColmena(long apiarioId, long colmenaId, Principal usuario, String codigo,
                              Long tipo, LocalDate instalacion) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        colmena(apiarioId, colmenaId, true);
        consulta("""
                update hiveon.colmenas set codigo=:codigo, tipo_id=:tipo, fecha_instalacion=:instalacion
                where apiario_id=:apiario and id=:colmena
                """, apiarioId).param("colmena", colmenaId).param("codigo", requerido(codigo))
                .param("tipo", tipo, java.sql.Types.BIGINT)
                .param("instalacion", fechaPasada(instalacion), java.sql.Types.DATE).update();
    }

    @Transactional
    public void archivarColmena(long apiarioId, long colmenaId, Principal usuario) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        colmena(apiarioId, colmenaId, true);
        consulta("""
                update hiveon.colmenas set archivado_en=current_timestamp where apiario_id=:apiario and id=:colmena
                """, apiarioId).param("colmena", colmenaId).update();
    }

    @Transactional
    public void vincular(long apiarioId, Principal usuario, String correo) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        int filas = consulta("""
                insert into hiveon.vinculaciones_apiario(apiario_id,usuario_id,funcion)
                select :apiario,u.id,u.rol_codigo from hiveon.usuarios u
                where lower(u.correo)=lower(:correo) and u.habilitado and u.rol_codigo in ('OPERARIO','TECNICO')
                on conflict (apiario_id, usuario_id) where retirada_en is null do nothing
                """, apiarioId).param("correo", requerido(correo)).update();
        comprobar(filas, "La cuenta debe ser de Operario o Técnico, estar habilitada y no tener un vínculo activo.");
    }

    @Transactional
    public void retirar(long apiarioId, long vinculoId, Principal usuario) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        comprobar(consulta("""
                update hiveon.vinculaciones_apiario set retirada_en=current_timestamp
                where apiario_id=:apiario and id=:vinculo and retirada_en is null
                """, apiarioId).param("vinculo", vinculoId).update(), "La vinculación ya no está disponible.");
    }

    @Transactional
public void inspeccionar(long apiarioId, long colmenaId, Principal usuario, LocalDate fecha,
                         String observaciones) {
        Acceso acceso = acceso(apiarioId, usuario, true);
        exigir(acceso, "APICULTOR", "OPERARIO");
        colmena(apiarioId, colmenaId, true);
        jdbc.sql("""
                insert into hiveon.inspecciones(colmena_id,autor_id,fecha,observaciones)
                values (:colmena,:usuario,:fecha,:texto)
                """).param("colmena", colmenaId).param("usuario", acceso.usuarioId())
                .param("fecha", fechaPasada(fecha)).param("texto", requerido(observaciones)).update();
    }

    @Transactional
public void asignarTarea(long apiarioId, Principal usuario, long vinculo, String descripcion,
                         LocalDate vencimiento) {
        Acceso acceso = acceso(apiarioId, usuario, true);
        exigir(acceso, "APICULTOR");
        if (vencimiento.isBefore(LocalDate.now(BOGOTA))) {
            throw new IllegalArgumentException("El vencimiento no puede ser anterior a hoy.");
        }
        comprobar(consulta("""
                insert into hiveon.tareas(apiario_id,vinculacion_id,creador_id,descripcion,vencimiento)
                select v.apiario_id,v.id,:usuario,:texto,:vencimiento
                from hiveon.vinculaciones_apiario v
                where v.apiario_id=:apiario and v.id=:vinculo
                  and v.funcion='OPERARIO' and v.retirada_en is null
                """, apiarioId).param("vinculo", vinculo).param("usuario", acceso.usuarioId())
                .param("texto", requerido(descripcion)).param("vencimiento", vencimiento).update(),
                "Selecciona un operario vinculado al apiario.");
    }

    @Transactional
    public void actualizarTarea(long apiarioId, long tareaId, Principal usuario, String estado) {
        Acceso acceso = acceso(apiarioId, usuario, true);
        exigir(acceso, "OPERARIO");
        if (!ESTADOS_TAREA.contains(estado)) {
            throw new IllegalArgumentException("Selecciona un estado válido.");
        }
        String actual = consulta("""
                select t.estado from hiveon.tareas t
                join hiveon.vinculaciones_apiario v on t.vinculacion_id=v.id
                where t.apiario_id=:apiario and t.id=:tarea
                  and v.usuario_id=:usuario and v.retirada_en is null
                for update of t
                """, apiarioId).param("tarea", tareaId).param("usuario", acceso.usuarioId())
                .query(String.class).optional().orElseThrow(OperacionRechazada::noEncontrado);
        if (actual.equals(estado)) {
            return;
        }
        if ("COMPLETADA".equals(actual)) {
            throw new IllegalArgumentException("Una tarea completada no se puede reabrir.");
        }
        consulta("update hiveon.tareas set estado=:estado where apiario_id=:apiario and id=:tarea", apiarioId)
                .param("estado", estado).param("tarea", tareaId).update();
    }

    @Transactional
    public void tratar(long apiarioId, long colmenaId, Principal usuario, LocalDate fecha, String descripcion) {
        Acceso acceso = acceso(apiarioId, usuario, true);
        exigir(acceso, "TECNICO");
        colmena(apiarioId, colmenaId, true);
        jdbc.sql("""
                insert into hiveon.tratamientos(colmena_id,autor_id,fecha,descripcion)
                values (:colmena,:usuario,:fecha,:texto)
                """).param("colmena", colmenaId).param("usuario", acceso.usuarioId())
                .param("fecha", fechaPasada(fecha)).param("texto", requerido(descripcion)).update();
    }

    @Transactional
    public void recomendar(long apiarioId, Principal usuario, Long colmena, String contenido) {
        Acceso acceso = acceso(apiarioId, usuario, true);
        exigir(acceso, "TECNICO");
        if (colmena != null) {
            colmena(apiarioId, colmena, true);
        }
        consulta("""
                insert into hiveon.recomendaciones(apiario_id,colmena_id,autor_id,contenido)
                values (:apiario,:colmena,:usuario,:texto)
                """, apiarioId).param("colmena", colmena, java.sql.Types.BIGINT)
                .param("usuario", acceso.usuarioId()).param("texto", requerido(contenido)).update();
    }

    private Acceso acceso(long apiarioId, Principal usuario, boolean escritura) {
        return consulta(ACCESO + (escritura ? " for update of a for share of u" : ""), apiarioId)
                .param("correo", usuario.getName()).query(Acceso.class).optional()
                .orElseThrow(OperacionRechazada::noEncontrado);
    }

    private Map<String, Object> colmena(long apiarioId, long colmenaId, boolean activa) {
        return consulta("""
                select c.id, c.codigo, c.tipo_id, c.fecha_instalacion, ct.nombre as tipo,
                       c.archivado_en is not null as archivada
                from hiveon.colmenas c left join hiveon.tipos_colmena ct on ct.id=c.tipo_id
                where c.apiario_id=:apiario and c.id=:colmena
                """ + (activa ? " and c.archivado_en is null" : ""), apiarioId)
                .param("colmena", colmenaId).query().listOfRows().stream().findFirst()
                .orElseThrow(OperacionRechazada::noEncontrado);
    }

    private JdbcClient.StatementSpec consulta(String sql, long apiarioId) {
        return jdbc.sql(sql).param("apiario", apiarioId);
    }

    private static void exigir(Acceso acceso, String... roles) {
        if (!Set.of(roles).contains(acceso.rol())) {
            throw OperacionRechazada.sinAcceso();
        }
        if (acceso.archivado()) {
            throw new IllegalArgumentException("El apiario está archivado.");
        }
    }

    private static String requerido(String texto) {
        if (texto.isBlank()) {
            throw new IllegalArgumentException("Completa los campos obligatorios.");
        }
        return texto.strip();
    }

    private static LocalDate fechaPasada(LocalDate fecha) {
        if (fecha != null && fecha.isAfter(LocalDate.now(BOGOTA))) {
            throw new IllegalArgumentException("La fecha no puede estar en el futuro.");
        }
        return fecha;
    }

    private static void comprobar(int filas, String mensaje) {
        if (filas != 1) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    public record Acceso(long usuarioId, String rol, String nombre, boolean archivado) {
        public boolean propietario() { return "APICULTOR".equals(rol); }
        public boolean operario() { return "OPERARIO".equals(rol); }
        public boolean tecnico() { return "TECNICO".equals(rol); }
    }

    public record GestionDatos(
            long apiarioId, Acceso acceso, LocalDate hoy, List<Map<String, Object>> colmenas,
            List<Map<String, Object>> tipos, List<Map<String, Object>> vinculos,
            List<Map<String, Object>> tareas, List<Map<String, Object>> historialSanitario,
            List<Map<String, Object>> recomendaciones) {}

    public record EditarDatos(
            long apiarioId, Acceso acceso, LocalDate hoy, Map<String, Object> datos, String departamento,
            List<Map<String, Object>> departamentos, List<Map<String, Object>> municipios) {}

    public record VerColmenaDatos(
            long apiarioId, Acceso acceso, LocalDate hoy, Map<String, Object> colmena,
            List<Map<String, Object>> tipos, List<Map<String, Object>> inspecciones,
            List<Map<String, Object>> tratamientos) {}
}
