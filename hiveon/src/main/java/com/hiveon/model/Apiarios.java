package com.hiveon.model;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class Apiarios {

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

    Apiarios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Apiario> listar(String correo) {
        return jdbc.sql(CONSULTA_AUTORIZADOS + " order by a.nombre, a.id")
                .param("correo", correo)
                .query(Apiario.class)
                .list();
    }

    public Optional<Apiario> consultar(long id, String correo) {
        return jdbc.sql(CONSULTA_AUTORIZADOS + " and a.id = :id")
                .param("correo", correo)
                .param("id", id)
                .query(Apiario.class)
                .optional();
    }

    public boolean registrar(String correo, String nombre, String departamento, String municipio, String vereda) {
        return jdbc.sql("""
                insert into hiveon.apiarios (propietario_id, nombre, municipio_codigo, vereda)
                select u.id, :nombre, m.codigo, nullif(:vereda, '')
                from hiveon.usuarios u
                join hiveon.municipios m on m.codigo = :municipio
                    and m.departamento_codigo = :departamento
                where lower(u.correo) = lower(:correo) and u.habilitado
                  and u.rol_codigo = 'APICULTOR'
                """)
                .param("correo", correo)
                .param("nombre", nombre.strip())
                .param("vereda", vereda == null ? "" : vereda.strip())
                .param("municipio", municipio)
                .param("departamento", departamento)
                .update() == 1;
    }

    public Ubicaciones ubicaciones(String departamento) {
        var departamentos = jdbc.sql("""
                select codigo, nombre from hiveon.departamentos order by nombre
                """).query(Ubicacion.class).list();
        boolean seleccionado = departamentos.stream().anyMatch(d -> d.codigo().equals(departamento));
        var municipios = seleccionado
                ? jdbc.sql("""
                        select codigo, nombre from hiveon.municipios
                        where departamento_codigo = :departamento order by nombre
                        """).param("departamento", departamento).query(Ubicacion.class).list()
                : List.<Ubicacion>of();
        return new Ubicaciones(departamentos, seleccionado, municipios);
    }

    public record Ubicacion(String codigo, String nombre) {}

    public record Ubicaciones(List<Ubicacion> departamentos, boolean seleccionado, List<Ubicacion> municipios) {}

    public record Apiario(long id, String nombre, String vereda, String municipio, String departamento,
                          boolean archivado) {}

    public record ApiarioPropio(long id, String nombre, long propietarioId, boolean archivado) {}
}
