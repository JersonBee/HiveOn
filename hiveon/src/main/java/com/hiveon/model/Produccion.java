package com.hiveon.model;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Produccion {

    private static final String ORIGEN_PUBLICO = """
            from hiveon.lotes_miel l join hiveon.apiarios a on a.id=l.apiario_id
            join hiveon.municipios m on m.codigo=a.municipio_codigo
            join hiveon.departamentos d on d.codigo=m.departamento_codigo
            where l.publicado_en is not null
            """;

    private final JdbcClient jdbc;
    private final AccesoModulos acceso;

    Produccion(JdbcClient jdbc, AccesoModulos acceso) {
        this.jdbc = jdbc;
        this.acceso = acceso;
    }

    public ProduccionDatos produccion(long id, Principal usuario) {
        var apiario = acceso.apiario(id, usuario, false);
        var hoy = LocalDate.now(AccesoModulos.BOGOTA);
        var colmenas = sql("""
                select id,codigo from hiveon.colmenas where apiario_id=:apiario and archivado_en is null
                order by codigo
                """, id).query().listOfRows();
        var cosechas = sql("""
                select h.fecha, h.cantidad_kg, c.codigo as colmena
                from hiveon.cosechas h left join hiveon.colmenas c on c.id=h.colmena_id
                where h.apiario_id=:apiario order by h.fecha desc,h.id desc
                """, id).query().listOfRows();
        var lotes = sql("""
                select id,codigo,publicado_en is not null as publicado from hiveon.lotes_miel
                where apiario_id=:apiario order by creado_en desc,id desc
                """, id).query().listOfRows();
        return new ProduccionDatos(apiario, hoy, colmenas, cosechas, lotes);
    }

    @Transactional
    public void cosechar(long id, Principal usuario, LocalDate fecha, BigDecimal cantidad, Long colmena) {
        var apiario = acceso.apiario(id, usuario, true);
        if (cantidad.signum() <= 0 || cantidad.stripTrailingZeros().scale() > 3
                || cantidad.compareTo(new BigDecimal("1000000000")) >= 0) {
            throw AccesoModulos.invalido("Ingresa una cantidad positiva en kg con hasta tres decimales.");
        }
        if (colmena != null && sql("""
                select id from hiveon.colmenas where id=:colmena and apiario_id=:apiario and archivado_en is null
                """, id).param("colmena", colmena).query(Long.class).optional().isEmpty()) {
            throw AccesoModulos.noEncontrado();
        }
        long cosecha = sql("""
                insert into hiveon.cosechas(apiario_id,colmena_id,autor_id,fecha,cantidad_kg)
                values(:apiario,:colmena,:autor,:fecha,:cantidad) returning id
                """, id).param("colmena", colmena, java.sql.Types.BIGINT).param("autor", apiario.propietarioId())
                .param("fecha", AccesoModulos.fechaPasada(fecha)).param("cantidad", cantidad)
                .query(Long.class).single();
        acceso.auditar(apiario.propietarioId(), "REGISTRAR_COSECHA", "COSECHA", cosecha);
    }

    @Transactional
    public long crearLote(long id, Principal usuario, String codigo) {
        var apiario = acceso.apiario(id, usuario, true);
        long lote = sql("""
                insert into hiveon.lotes_miel(apiario_id,codigo) values(:apiario,:codigo) returning id
                """, id).param("codigo", AccesoModulos.requerido(codigo, 80)).query(Long.class).single();
        acceso.auditar(apiario.propietarioId(), "CREAR_LOTE", "LOTE", lote);
        return lote;
    }

    public LoteDatos lote(long id, long lote, Principal usuario) {
        var apiario = acceso.apiario(id, usuario, false);
        Lote datos = cargarLote(id, lote);

        var cosechas = sql("""
                select h.id,h.fecha,h.cantidad_kg,l.cosecha_id is not null as incluida
                from hiveon.cosechas h left join hiveon.lote_cosechas l on l.cosecha_id=h.id
                where h.apiario_id=:apiario and (l.lote_id=:lote or (l.lote_id is null and not :publicado))
                order by h.fecha desc,h.id desc
                """, id).param("lote", lote).param("publicado", datos.publicado()).query().listOfRows();
        return new LoteDatos(apiario, datos, cosechas);
    }

    @Transactional
    public void editarLote(long id, long lote, Principal usuario, String codigo, List<Long> cosechas) {
        var apiario = acceso.apiario(id, usuario, true);
        if (cargarLote(id, lote).publicado()) {
            throw AccesoModulos.invalido("La composición de un lote publicado se conserva.");
        }
        sql("""
                update hiveon.lotes_miel set codigo=:codigo where apiario_id=:apiario and id=:lote
                """, id).param("codigo", AccesoModulos.requerido(codigo, 80)).param("lote", lote).update();
        sql("delete from hiveon.lote_cosechas where apiario_id=:apiario and lote_id=:lote", id)
                .param("lote", lote).update();
        for (long cosecha : cosechas == null ? List.<Long>of() : cosechas.stream().distinct().toList()) {
            if (sql("""
                    insert into hiveon.lote_cosechas(apiario_id,lote_id,cosecha_id)
                    select h.apiario_id,:lote,h.id from hiveon.cosechas h
                    where h.apiario_id=:apiario and h.id=:cosecha
                    on conflict (cosecha_id) do nothing
                    """, id).param("lote", lote).param("cosecha", cosecha).update() != 1) {
                throw AccesoModulos.invalido("Una cosecha seleccionada no pertenece al apiario o ya está en otro lote.");
            }
        }
        acceso.auditar(apiario.propietarioId(), "EDITAR_LOTE", "LOTE", lote);
    }

    @Transactional
    public void publicar(long id, long lote, Principal usuario) {
        var apiario = acceso.apiario(id, usuario, true);
        if (cargarLote(id, lote).publicado()) {
            throw AccesoModulos.invalido("El lote ya está publicado.");
        }
        int filas = sql("""
                update hiveon.lotes_miel l set publicado_en=current_timestamp
                where l.apiario_id=:apiario and l.id=:lote and l.publicado_en is null
                  and exists(select 1 from hiveon.lote_cosechas h where h.apiario_id=l.apiario_id and h.lote_id=l.id)
                """, id).param("lote", lote).update();
        if (filas == 0) {
            throw AccesoModulos.invalido("Vincula al menos una cosecha antes de publicar.");
        }
        acceso.auditar(apiario.propietarioId(), "PUBLICAR_LOTE", "LOTE", lote);
    }

    public PublicadosDatos publicados(Principal usuario, String codigo) {
        acceso.cuenta(usuario, "COMPRADOR", false);

        var lotes = jdbc.sql("""
                select l.id,l.codigo,m.nombre as municipio,d.nombre as departamento
                """ + ORIGEN_PUBLICO + " and (:codigo='' or lower(l.codigo)=lower(:codigo))"
                + " order by l.publicado_en desc,l.id desc")
                .param("codigo", codigo.strip()).query().listOfRows();
        return new PublicadosDatos(codigo, lotes);
    }

    public TrazabilidadDatos trazabilidad(long id, Principal usuario) {
        acceso.cuenta(usuario, "COMPRADOR", false);
        var lote = jdbc.sql("""
                select l.codigo,a.nombre as apiario,m.nombre as municipio,d.nombre as departamento
                """ + ORIGEN_PUBLICO + " and l.id=:id")
                .param("id", id).query().listOfRows().stream().findFirst().orElseThrow(AccesoModulos::noEncontrado);

        var fechas = jdbc.sql("""
                select distinct c.fecha from hiveon.lote_cosechas lc
                join hiveon.cosechas c on c.id=lc.cosecha_id
                where lc.lote_id=:id order by c.fecha
                """).param("id", id).query(LocalDate.class).list();
        return new TrazabilidadDatos(lote, fechas);
    }

    private Lote cargarLote(long apiario, long lote) {
        return sql("""
                select id,codigo,publicado_en is not null as publicado from hiveon.lotes_miel
                where apiario_id=:apiario and id=:lote
                """, apiario).param("lote", lote).query(Lote.class).optional().orElseThrow(AccesoModulos::noEncontrado);
    }

    private JdbcClient.StatementSpec sql(String sentencia, long apiario) {
        return jdbc.sql(sentencia).param("apiario", apiario);
    }

    public record Lote(long id, String codigo, boolean publicado) {}

    public record ProduccionDatos(
            Apiarios.ApiarioPropio apiario, LocalDate hoy, List<Map<String, Object>> colmenas,
            List<Map<String, Object>> cosechas, List<Map<String, Object>> lotes) {}

    public record LoteDatos(
            Apiarios.ApiarioPropio apiario, Lote lote, List<Map<String, Object>> cosechas) {}

    public record PublicadosDatos(
            String codigo, List<Map<String, Object>> lotes) {}

    public record TrazabilidadDatos(
            Map<String, Object> lote, List<LocalDate> fechas) {}
}
