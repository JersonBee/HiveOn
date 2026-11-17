package com.hiveon.controller;

import java.security.Principal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class ReportesController {

    private final JdbcClient jdbc;
    private final AccesoModulos acceso;

    ReportesController(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.acceso = new AccesoModulos(jdbc);
    }

    @GetMapping("/apiarios/{id}/reportes")
    String reportes(@PathVariable long id, Principal usuario, Model modelo,
                    @RequestParam(defaultValue = "sanitario") String tipo) {
        modelo.addAttribute("apiario", acceso.apiario(id, usuario, false));
        String consulta = switch (tipo) {
            case "sanitario" -> """
                    select i.fecha,'Inspección' as categoria,c.codigo || ' · ' || i.observaciones as detalle
                    from hiveon.inspecciones i join hiveon.colmenas c on c.id=i.colmena_id where c.apiario_id=:id
                    union all
                    select t.fecha,'Tratamiento',c.codigo || ' · ' || t.descripcion
                    from hiveon.tratamientos t join hiveon.colmenas c on c.id=t.colmena_id where c.apiario_id=:id
                    union all
                    select (r.creada_en at time zone 'America/Bogota')::date,'Recomendación',r.contenido
                    from hiveon.recomendaciones r where r.apiario_id=:id
                    order by fecha desc,categoria
                    """;
            case "produccion" -> """
                    select fecha,'Cosecha' as categoria, sum(cantidad_kg)::text || ' kg' as detalle
                    from hiveon.cosechas where apiario_id=:id group by fecha order by fecha desc
                    """;
            case "tareas" -> """
                    select vencimiento as fecha,replace(estado,'_',' ') as categoria,descripcion as detalle
                    from hiveon.tareas where apiario_id=:id and estado <> 'COMPLETADA'
                    order by vencimiento,id
                    """;
            default -> throw AccesoModulos.invalido("Selecciona un tipo de reporte válido.");
        };
        modelo.addAttribute("tipo", tipo);
        modelo.addAttribute("filas", jdbc.sql(consulta).param("id", id).query().listOfRows());
        return "sprint3/reportes";
    }
}
