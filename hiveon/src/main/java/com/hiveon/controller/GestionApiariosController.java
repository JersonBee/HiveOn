package com.hiveon.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/apiarios/{apiarioId}")
class GestionApiariosController {

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

    GestionApiariosController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/gestion")
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public String gestion(@PathVariable long apiarioId, Principal usuario, Model modelo) {
        Acceso acceso = acceso(apiarioId, usuario, false);
        prepararModelo(modelo, apiarioId, acceso);
        modelo.addAttribute("colmenas", consulta("""
                select id, codigo, archivado_en is not null as archivada
                from hiveon.colmenas where apiario_id = :apiario order by codigo
                """, apiarioId).query().listOfRows());
        if (acceso.propietario()) {
            modelo.addAttribute("tipos", jdbc.sql("""
                    select id, nombre from hiveon.tipos_colmena where habilitado order by nombre
                    """).query().listOfRows());
            modelo.addAttribute("vinculos", consulta("""
                    select v.id, u.nombre, u.correo, v.funcion,
                           not u.habilitado or u.rol_codigo <> v.funcion as suspendido
                    from hiveon.vinculaciones_apiario v join hiveon.usuarios u on u.id=v.usuario_id
                    where v.apiario_id=:apiario and v.retirada_en is null order by u.nombre, v.id
                    """, apiarioId).query().listOfRows());
        }
        if (acceso.propietario() || acceso.operario()) {
            modelo.addAttribute("tareas", consulta("""
                    select t.id, t.descripcion, t.vencimiento, t.estado, u.nombre as operario,
                           v.retirada_en is not null as retirada
                    from hiveon.tareas t join hiveon.vinculaciones_apiario v on v.id=t.vinculacion_id
                    join hiveon.usuarios u on u.id=v.usuario_id
                    where t.apiario_id=:apiario
                      and (:propietario or (v.usuario_id=:usuario and v.retirada_en is null))
                    order by t.vencimiento, t.id
                    """, apiarioId).param("propietario", acceso.propietario())
                    .param("usuario", acceso.usuarioId()).query().listOfRows());
        }
        if (!acceso.operario()) {
            modelo.addAttribute("historialSanitario", consulta("""
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
                    """, apiarioId).query().listOfRows());
            modelo.addAttribute("recomendaciones", consulta("""
                    select r.contenido, c.codigo as colmena, u.nombre as autor,
                           (r.creada_en at time zone 'America/Bogota')::date as fecha
                    from hiveon.recomendaciones r
                    join hiveon.usuarios u on u.id=r.autor_id
                    left join hiveon.colmenas c on c.id=r.colmena_id
                    where r.apiario_id=:apiario order by r.creada_en desc, r.id desc
                    """, apiarioId).query().listOfRows());
        }
        return "apiarios/gestion";
    }

    @GetMapping("/editar")
    public String editar(@PathVariable long apiarioId, Principal usuario, Model modelo,
                         @RequestParam(required = false) String departamento) {
        Acceso acceso = acceso(apiarioId, usuario, false);
        exigir(acceso, "APICULTOR");
        prepararModelo(modelo, apiarioId, acceso);
        var datos = consulta("""
                select a.nombre, a.vereda, m.codigo as municipio, m.departamento_codigo as departamento
                from hiveon.apiarios a join hiveon.municipios m on m.codigo=a.municipio_codigo
                where a.id=:apiario
                """, apiarioId).query().singleRow();
        String codigo = departamento == null ? (String) datos.get("departamento") : departamento;
        modelo.addAttribute("datos", datos);
        modelo.addAttribute("departamento", codigo);
        modelo.addAttribute("departamentos", jdbc.sql("""
                select codigo, nombre from hiveon.departamentos order by nombre
                """).query().listOfRows());
        modelo.addAttribute("municipios", jdbc.sql("""
                select codigo, nombre from hiveon.municipios where departamento_codigo=:codigo order by nombre
                """).param("codigo", codigo).query().listOfRows());
        return "apiarios/editar";
    }

    @PostMapping("/editar")
    @Transactional
    public String actualizarApiario(@PathVariable long apiarioId, Principal usuario,
                                    @RequestParam String nombre, @RequestParam String departamento,
                                    @RequestParam String municipio, @RequestParam(defaultValue = "") String vereda) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        int filas = consulta("""
                update hiveon.apiarios a set nombre=:nombre, municipio_codigo=m.codigo, vereda=nullif(:vereda,'')
                from hiveon.municipios m where a.id=:apiario and m.codigo=:municipio
                     and m.departamento_codigo=:departamento
                """, apiarioId).param("nombre", requerido(nombre)).param("vereda", vereda.strip())
                .param("municipio", municipio).param("departamento", departamento).update();
        comprobar(filas, "Selecciona un municipio del departamento indicado.");
        return "redirect:/apiarios/" + apiarioId;
    }

    @PostMapping("/archivar")
    @Transactional
    public String archivarApiario(@PathVariable long apiarioId, Principal usuario) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        consulta("update hiveon.apiarios set archivado_en=current_timestamp where id=:apiario", apiarioId).update();
        return "redirect:/apiarios/" + apiarioId;
    }

    @PostMapping("/colmenas")
    @Transactional
    public String crearColmena(@PathVariable long apiarioId, Principal usuario, @RequestParam String codigo,
                               @RequestParam(required = false) Long tipo,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate instalacion) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        consulta("""
                insert into hiveon.colmenas(apiario_id,codigo,tipo_id,fecha_instalacion)
                values(:apiario,:codigo,:tipo,:instalacion)
                """, apiarioId).param("codigo", requerido(codigo)).param("tipo", tipo, java.sql.Types.BIGINT)
                .param("instalacion", fechaPasada(instalacion), java.sql.Types.DATE).update();
        return regresar(apiarioId);
    }

    @GetMapping("/colmenas/{colmenaId}")
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public String verColmena(@PathVariable long apiarioId, @PathVariable long colmenaId,
                             Principal usuario, Model modelo) {
        Acceso acceso = acceso(apiarioId, usuario, false);
        prepararModelo(modelo, apiarioId, acceso);
        modelo.addAttribute("colmena", colmena(apiarioId, colmenaId, false));
        if (acceso.propietario()) {
            modelo.addAttribute("tipos", jdbc.sql("""
                    select id, nombre from hiveon.tipos_colmena where habilitado order by nombre
                    """).query().listOfRows());
        }
        modelo.addAttribute("inspecciones", jdbc.sql("""
                select i.fecha, i.observaciones, u.nombre as autor from hiveon.inspecciones i
                join hiveon.usuarios u on u.id=i.autor_id
                where i.colmena_id=:colmena order by i.fecha desc, i.id desc
                """).param("colmena", colmenaId).query().listOfRows());
        if (!acceso.operario()) {
            modelo.addAttribute("tratamientos", jdbc.sql("""
                    select t.fecha, t.descripcion, u.nombre as autor from hiveon.tratamientos t
                    join hiveon.usuarios u on u.id=t.autor_id
                    where t.colmena_id=:colmena order by t.fecha desc, t.id desc
                    """).param("colmena", colmenaId).query().listOfRows());
        }
        return "apiarios/colmena";
    }

    @PostMapping("/colmenas/{colmenaId}/editar")
    @Transactional
    public String actualizarColmena(@PathVariable long apiarioId, @PathVariable long colmenaId, Principal usuario,
                                    @RequestParam String codigo, @RequestParam(required = false) Long tipo,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate instalacion) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        colmena(apiarioId, colmenaId, true);
        consulta("""
                update hiveon.colmenas set codigo=:codigo, tipo_id=:tipo, fecha_instalacion=:instalacion
                where apiario_id=:apiario and id=:colmena
                """, apiarioId).param("colmena", colmenaId).param("codigo", requerido(codigo))
                .param("tipo", tipo, java.sql.Types.BIGINT)
                .param("instalacion", fechaPasada(instalacion), java.sql.Types.DATE).update();
        return regresarColmena(apiarioId, colmenaId);
    }

    @PostMapping("/colmenas/{colmenaId}/archivar")
    @Transactional
    public String archivarColmena(@PathVariable long apiarioId, @PathVariable long colmenaId, Principal usuario) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        colmena(apiarioId, colmenaId, true);
        consulta("""
                update hiveon.colmenas set archivado_en=current_timestamp where apiario_id=:apiario and id=:colmena
                """, apiarioId).param("colmena", colmenaId).update();
        return regresarColmena(apiarioId, colmenaId);
    }

    @PostMapping("/vinculaciones")
    @Transactional
    public String vincular(@PathVariable long apiarioId, Principal usuario, @RequestParam String correo) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        int filas = consulta("""
                insert into hiveon.vinculaciones_apiario(apiario_id,usuario_id,funcion)
                select :apiario,u.id,u.rol_codigo from hiveon.usuarios u
                where lower(u.correo)=lower(:correo) and u.habilitado and u.rol_codigo in ('OPERARIO','TECNICO')
                on conflict (apiario_id, usuario_id) where retirada_en is null do nothing
                """, apiarioId).param("correo", requerido(correo)).update();
        comprobar(filas, "La cuenta debe ser de Operario o Técnico, estar habilitada y no tener un vínculo activo.");
        return regresar(apiarioId);
    }

    @PostMapping("/vinculaciones/{vinculoId}/retirar")
    @Transactional
    public String retirar(@PathVariable long apiarioId, @PathVariable long vinculoId, Principal usuario) {
        exigir(acceso(apiarioId, usuario, true), "APICULTOR");
        comprobar(consulta("""
                update hiveon.vinculaciones_apiario set retirada_en=current_timestamp
                where apiario_id=:apiario and id=:vinculo and retirada_en is null
                """, apiarioId).param("vinculo", vinculoId).update(), "La vinculación ya no está disponible.");
        return regresar(apiarioId);
    }

    @PostMapping("/colmenas/{colmenaId}/inspecciones")
    @Transactional
    public String inspeccionar(@PathVariable long apiarioId, @PathVariable long colmenaId, Principal usuario,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                               @RequestParam String observaciones) {
        Acceso acceso = acceso(apiarioId, usuario, true);
        exigir(acceso, "APICULTOR", "OPERARIO");
        colmena(apiarioId, colmenaId, true);
        jdbc.sql("""
                insert into hiveon.inspecciones(colmena_id,autor_id,fecha,observaciones)
                values (:colmena,:usuario,:fecha,:texto)
                """).param("colmena", colmenaId).param("usuario", acceso.usuarioId())
                .param("fecha", fechaPasada(fecha)).param("texto", requerido(observaciones)).update();
        return regresarColmena(apiarioId, colmenaId);
    }

    @PostMapping("/tareas")
    @Transactional
    public String asignarTarea(@PathVariable long apiarioId, Principal usuario, @RequestParam long vinculo,
                               @RequestParam String descripcion,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimiento) {
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
        return regresar(apiarioId);
    }

    @PostMapping("/tareas/{tareaId}/estado")
    @Transactional
    public String actualizarTarea(@PathVariable long apiarioId, @PathVariable long tareaId,
                                 Principal usuario, @RequestParam String estado) {
        Acceso acceso = acceso(apiarioId, usuario, true);
        exigir(acceso, "OPERARIO");
        if (!ESTADOS_TAREA.contains(estado)) {
            throw new IllegalArgumentException("Selecciona un estado válido.");
        }
        if (consulta("""
                update hiveon.tareas t set estado=:estado
                from hiveon.vinculaciones_apiario v
                where t.apiario_id=:apiario and t.id=:tarea and t.vinculacion_id=v.id
                  and v.usuario_id=:usuario and v.retirada_en is null
                """, apiarioId).param("estado", estado).param("tarea", tareaId)
                .param("usuario", acceso.usuarioId()).update() != 1) {
            throw noEncontrado();
        }
        return regresar(apiarioId);
    }

    @PostMapping("/colmenas/{colmenaId}/tratamientos")
    @Transactional
    public String tratar(@PathVariable long apiarioId, @PathVariable long colmenaId, Principal usuario,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                         @RequestParam String descripcion) {
        Acceso acceso = acceso(apiarioId, usuario, true);
        exigir(acceso, "TECNICO");
        colmena(apiarioId, colmenaId, true);
        jdbc.sql("""
                insert into hiveon.tratamientos(colmena_id,autor_id,fecha,descripcion)
                values (:colmena,:usuario,:fecha,:texto)
                """).param("colmena", colmenaId).param("usuario", acceso.usuarioId())
                .param("fecha", fechaPasada(fecha)).param("texto", requerido(descripcion)).update();
        return regresarColmena(apiarioId, colmenaId);
    }

    @PostMapping("/recomendaciones")
    @Transactional
    public String recomendar(@PathVariable long apiarioId, Principal usuario,
                             @RequestParam(required = false) Long colmena, @RequestParam String contenido) {
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
        return regresar(apiarioId);
    }

    private Acceso acceso(long apiarioId, Principal usuario, boolean escritura) {
        return consulta(ACCESO + (escritura ? " for update of a for share of u" : ""), apiarioId)
                .param("correo", usuario.getName()).query(Acceso.class).optional()
                .orElseThrow(GestionApiariosController::noEncontrado);
    }

    private java.util.Map<String, Object> colmena(long apiarioId, long colmenaId, boolean activa) {
        return consulta("""
                select c.id, c.codigo, c.tipo_id, c.fecha_instalacion, ct.nombre as tipo,
                       c.archivado_en is not null as archivada
                from hiveon.colmenas c left join hiveon.tipos_colmena ct on ct.id=c.tipo_id
                where c.apiario_id=:apiario and c.id=:colmena
                """ + (activa ? " and c.archivado_en is null" : ""), apiarioId)
                .param("colmena", colmenaId).query().listOfRows().stream().findFirst()
                .orElseThrow(GestionApiariosController::noEncontrado);
    }

    private JdbcClient.StatementSpec consulta(String sql, long apiarioId) {
        return jdbc.sql(sql).param("apiario", apiarioId);
    }

    private static void exigir(Acceso acceso, String... roles) {
        if (!Set.of(roles).contains(acceso.rol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (acceso.archivado()) {
            throw new IllegalArgumentException("El apiario está archivado.");
        }
    }

    private static void prepararModelo(Model modelo, long apiarioId, Acceso acceso) {
        modelo.addAttribute("apiarioId", apiarioId);
        modelo.addAttribute("acceso", acceso);
        modelo.addAttribute("hoy", LocalDate.now(BOGOTA));
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

    private static ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static String regresar(long apiarioId) {
        return "redirect:/apiarios/" + apiarioId + "/gestion";
    }

    private static String regresarColmena(long apiarioId, long colmenaId) {
        return "redirect:/apiarios/" + apiarioId + "/colmenas/" + colmenaId;
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

    public record Acceso(long usuarioId, String rol, String nombre, boolean archivado) {
        public boolean propietario() { return "APICULTOR".equals(rol); }
        public boolean operario() { return "OPERARIO".equals(rol); }
        public boolean tecnico() { return "TECNICO".equals(rol); }
    }
}
