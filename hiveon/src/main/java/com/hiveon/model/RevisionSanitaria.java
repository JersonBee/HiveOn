package com.hiveon.model;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RevisionSanitaria {

    private final JdbcClient jdbc;
    private final AccesoModulos acceso;

    RevisionSanitaria(JdbcClient jdbc, AccesoModulos acceso) {
        this.jdbc = jdbc;
        this.acceso = acceso;
    }

    public PropiosDatos propios(long id, Principal usuario) {
        var apiario = acceso.apiario(id, usuario, false);
        var expedientes = jdbc.sql("""
                select descripcion,decision,(remitido_en at time zone 'America/Bogota')::date as fecha
                from hiveon.expedientes_sanitarios where apiario_id=:id order by remitido_en desc,id desc
                """).param("id", id).query().listOfRows();
        return new PropiosDatos(apiario, expedientes);
    }

    @Transactional
    public void remitir(long id, Principal usuario, String descripcion) {
        var apiario = acceso.apiario(id, usuario, true);
        long expediente = jdbc.sql("""
                insert into hiveon.expedientes_sanitarios(apiario_id,remitente_id,descripcion)
                values(:apiario,:autor,:descripcion) returning id
                """).param("apiario", id).param("autor", apiario.propietarioId())
                .param("descripcion", AccesoModulos.requerido(descripcion, 4000)).query(Long.class).single();
        acceso.auditar(apiario.propietarioId(), "REMITIR_EXPEDIENTE", "EXPEDIENTE", expediente);
    }

    public List<Map<String, Object>> remitidos(Principal usuario) {
        acceso.cuenta(usuario, "INSPECTOR", false);
        return jdbc.sql("""
                select e.id,a.nombre as apiario,e.decision,
                    (e.remitido_en at time zone 'America/Bogota')::date as fecha
                from hiveon.expedientes_sanitarios e join hiveon.apiarios a on a.id=e.apiario_id
                order by (e.decision is not null),e.remitido_en desc,e.id desc
                """).query().listOfRows();
    }

    public ExpedienteDatos expediente(long id, Principal usuario) {
        acceso.cuenta(usuario, "INSPECTOR", false);
        var expediente = jdbc.sql("""
                select e.id,e.apiario_id,a.nombre as apiario,e.descripcion,e.decision,e.observaciones_revision,
                    (e.remitido_en at time zone 'America/Bogota')::date as remitido,
                    (e.resuelto_en at time zone 'America/Bogota')::date as resuelto,
                    u.nombre as inspector
                from hiveon.expedientes_sanitarios e join hiveon.apiarios a on a.id=e.apiario_id
                left join hiveon.usuarios u on u.id=e.inspector_id where e.id=:id
                """).param("id", id).query().listOfRows().stream().findFirst().orElseThrow(AccesoModulos::noEncontrado);

        var antecedentes = jdbc.sql("""
                select 'Inspección' as tipo,i.fecha,i.observaciones as descripcion,c.codigo as colmena
                from hiveon.inspecciones i join hiveon.colmenas c on c.id=i.colmena_id
                where c.apiario_id=:apiario
                union all
                select 'Tratamiento',t.fecha,t.descripcion,c.codigo
                from hiveon.tratamientos t join hiveon.colmenas c on c.id=t.colmena_id
                where c.apiario_id=:apiario
                union all
                select 'Recomendación',(r.creada_en at time zone 'America/Bogota')::date,r.contenido,c.codigo
                from hiveon.recomendaciones r left join hiveon.colmenas c on c.id=r.colmena_id
                where r.apiario_id=:apiario
                order by fecha desc,tipo,colmena
                """).param("apiario", expediente.get("apiario_id")).query().listOfRows();
        return new ExpedienteDatos(expediente, antecedentes);
    }

    @Transactional
    public void resolver(long id, Principal usuario, String decision, String observaciones) {
        long inspector = acceso.cuenta(usuario, "INSPECTOR", true);
        if (!Set.of("VALIDADO", "RECHAZADO").contains(decision) || observaciones.strip().length() > 4000) {
            throw AccesoModulos.invalido("Selecciona una decisión válida y revisa las observaciones.");
        }
        int resueltos = jdbc.sql("""
                update hiveon.expedientes_sanitarios
                set decision=:decision,inspector_id=:actor,resuelto_en=current_timestamp,
                    observaciones_revision=nullif(:texto,'')
                where id=:id and decision is null
                """).param("decision", decision).param("actor", inspector)
                .param("texto", observaciones.strip()).param("id", id).update();
        if (resueltos == 0) {
            throw OperacionRechazada.conflicto("El expediente ya fue resuelto o no está disponible.");
        }
        acceso.auditar(inspector, decision, "EXPEDIENTE", id);
    }

    public record PropiosDatos(
            Apiarios.ApiarioPropio apiario, List<Map<String, Object>> expedientes) {}

    public record ExpedienteDatos(
            Map<String, Object> expediente, List<Map<String, Object>> antecedentes) {}
}
