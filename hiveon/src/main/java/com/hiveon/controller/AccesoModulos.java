package com.hiveon.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

record AccesoModulos(JdbcClient jdbc) {

    static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    long cuenta(Principal principal, String rol, boolean escritura) {
        return jdbc.sql("""
                select id from hiveon.usuarios where lower(correo)=lower(:correo)
                  and habilitado and rol_codigo=:rol
                """ + (escritura ? " for share" : ""))
                .param("correo", principal.getName()).param("rol", rol)
                .query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    Apiario apiario(long apiario, Principal principal, boolean escribir) {
        long cuenta = cuenta(principal, "APICULTOR", escribir);
        Apiario datos = jdbc.sql("""
                select a.id, a.nombre, a.propietario_id, a.archivado_en is not null as archivado
                from hiveon.apiarios a where a.id=:apiario and a.propietario_id=:propietario
                """ + (escribir ? " for update" : ""))
                .param("apiario", apiario).param("propietario", cuenta)
                .query(Apiario.class).optional().orElseThrow(AccesoModulos::noEncontrado);
        if (escribir && datos.archivado()) {
            throw invalido("El apiario está archivado.");
        }
        return datos;
    }

    void auditar(long actor, String accion, String recurso, long id) {
        jdbc.sql("""
                insert into hiveon.eventos_auditoria(actor_id,accion,recurso,recurso_id,resultado)
                values (:actor,:accion,:recurso,:id,'COMPLETADO')
                """).param("actor", actor).param("accion", accion).param("recurso", recurso)
                .param("id", id).update();
    }

    static String requerido(String valor, int maximo) {
        if (valor.isBlank() || valor.strip().length() > maximo) {
            throw invalido("Completa los campos obligatorios y revisa la longitud de los datos.");
        }
        return valor.strip();
    }

    static LocalDate fechaPasada(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now(BOGOTA))) {
            throw invalido("La fecha no puede estar en el futuro.");
        }
        return fecha;
    }

    static ResponseStatusException noEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    static ResponseStatusException invalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    public record Apiario(long id, String nombre, long propietarioId, boolean archivado) {}
}
