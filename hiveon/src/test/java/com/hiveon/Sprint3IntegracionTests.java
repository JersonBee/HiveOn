package com.hiveon;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@EnabledIfEnvironmentVariable(named = "HIVEON_SPRINT3_INTEGRATION", matches = "true")
class Sprint3IntegracionTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder contrasenas;

    @Test
    void publicaTrazabilidadYResuelveUnExpedienteUnaSolaVez() throws Exception {
        Cuenta propietario = cuenta("APICULTOR");
        Cuenta ajeno = cuenta("APICULTOR");
        Cuenta comprador = cuenta("COMPRADOR");
        Cuenta inspector = cuenta("INSPECTOR");
        long id = jdbc.sql("""
                insert into hiveon.apiarios(propietario_id,nombre,municipio_codigo)
                values(:id,'Apiario de origen','68001') returning id
                """).param("id", propietario.id()).query(Long.class).single();
        String apiario = "/apiarios/" + id;
        String produccion = apiario + "/produccion";
        String hoy = LocalDate.now(ZoneId.of("America/Bogota")).toString();
        String codigo = UUID.randomUUID().toString();
        mvc.perform(get(produccion).with(user(ajeno.correo()).roles(ajeno.rol())))
                .andExpect(status().isNotFound());
        mvc.perform(get(produccion).with(user(propietario.correo()).roles(propietario.rol())))
                .andExpect(content().string(containsString("Aún no hay cosechas")));
        mvc.perform(post(produccion + "/cosechas").with(user(propietario.correo()).roles(propietario.rol()))
                        .with(csrf()).param("fecha", hoy).param("cantidad", "2.125"))
                .andExpect(status().is3xxRedirection());
        long cosecha = jdbc.sql("select id from hiveon.cosechas where apiario_id=:id")
                .param("id", id).query(Long.class).single();
        mvc.perform(post(produccion + "/lotes").with(user(propietario.correo()).roles(propietario.rol()))
                        .with(csrf()).param("codigo", codigo))
                .andExpect(status().is3xxRedirection());
        long lote = jdbc.sql("select id from hiveon.lotes_miel where codigo=:codigo")
                .param("codigo", codigo).query(Long.class).single();
        String propio = produccion + "/lotes/" + lote;
        mvc.perform(get(propio).with(user(propietario.correo()).roles(propietario.rol())))
                .andExpect(content().string(containsString("Guardar lote")));
        mvc.perform(get("/lotes/" + lote).with(user(comprador.correo()).roles(comprador.rol())))
                .andExpect(status().isNotFound());
        mvc.perform(post(propio + "/publicar").with(user(propietario.correo()).roles(propietario.rol())).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(post(propio + "/editar").with(user(propietario.correo()).roles(propietario.rol()))
                        .with(csrf()).param("codigo", codigo).param("cosechas", Long.toString(cosecha)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(propio + "/publicar").with(user(propietario.correo()).roles(propietario.rol())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/lotes").with(user(comprador.correo()).roles(comprador.rol())).param("codigo", codigo))
                .andExpect(content().string(containsString(codigo)));
        mvc.perform(get("/lotes/" + lote).with(user(comprador.correo()).roles(comprador.rol())))
                .andExpect(content().string(containsString("Apiario de origen")))
                .andExpect(content().string(containsString(hoy)))
                .andExpect(content().string(not(containsString(propietario.correo()))));
        mvc.perform(get(apiario + "/reportes").with(user(propietario.correo()).roles(propietario.rol()))
                        .param("tipo", "produccion"))
                .andExpect(content().string(containsString("2.125 kg")));
        mvc.perform(get(apiario + "/reportes").with(user(ajeno.correo()).roles(ajeno.rol())))
                .andExpect(status().isNotFound());

        mvc.perform(post(apiario + "/expedientes").with(user(propietario.correo()).roles(propietario.rol()))
                        .with(csrf()).param("descripcion", "Antecedentes remitidos"))
                .andExpect(status().is3xxRedirection());
        long expediente = jdbc.sql("select id from hiveon.expedientes_sanitarios where apiario_id=:id")
                .param("id", id).query(Long.class).single();
        String revision = "/expedientes/" + expediente;
        mvc.perform(get("/expedientes").with(user(inspector.correo()).roles(inspector.rol())))
                .andExpect(content().string(containsString("Apiario de origen")));
        mvc.perform(get(revision).with(user(comprador.correo()).roles(comprador.rol())))
                .andExpect(status().isForbidden());
        mvc.perform(get(revision).with(user(inspector.correo()).roles(inspector.rol())))
                .andExpect(content().string(containsString("Antecedentes remitidos")));
        mvc.perform(post(revision + "/resolver").with(user(inspector.correo()).roles(inspector.rol()))
                        .with(csrf()).param("decision", "VALIDADO"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(revision + "/resolver").with(user(inspector.correo()).roles(inspector.rol()))
                        .with(csrf()).param("decision", "RECHAZADO"))
                .andExpect(status().isConflict());
        mvc.perform(get(apiario + "/expedientes").with(user(propietario.correo()).roles(propietario.rol())))
                .andExpect(content().string(containsString("VALIDADO")));
    }

    @Test
    void rechazaUnExpedienteYConservaLaDecisionDelInspector() throws Exception {
        Cuenta propietario = cuenta("APICULTOR");
        Cuenta inspector = cuenta("INSPECTOR");
        long apiario = jdbc.sql("""
                insert into hiveon.apiarios(propietario_id,nombre,municipio_codigo)
                values(:id,'Apiario para revisión','68001') returning id
                """).param("id", propietario.id()).query(Long.class).single();
        String expedientes = "/apiarios/" + apiario + "/expedientes";
        var apicultor = user(propietario.correo()).roles(propietario.rol());
        var usuarioInspector = user(inspector.correo()).roles(inspector.rol());
        mvc.perform(post(expedientes).with(apicultor).with(csrf()).param("descripcion", " "))
                .andExpect(status().isBadRequest());
        assertEquals(0L, jdbc.sql("""
                select count(*) from hiveon.expedientes_sanitarios where apiario_id=:id
                """).param("id", apiario).query(Long.class).single());
        mvc.perform(post(expedientes).with(apicultor).with(csrf()).param("descripcion", "Revisión de prueba"))
                .andExpect(status().is3xxRedirection());
        long expediente = jdbc.sql("select id from hiveon.expedientes_sanitarios where apiario_id=:id")
                .param("id", apiario).query(Long.class).single();
        String resolver = "/expedientes/" + expediente + "/resolver";
        mvc.perform(post(resolver).with(usuarioInspector).with(csrf()).param("decision", "PENDIENTE"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(resolver).with(usuarioInspector).with(csrf()).param("decision", "RECHAZADO")
                        .param("observaciones", "Falta evidencia sanitaria"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get(expedientes).with(apicultor))
                .andExpect(content().string(containsString("RECHAZADO")));
        mvc.perform(get("/expedientes/" + expediente).with(usuarioInspector))
                .andExpect(content().string(containsString("Falta evidencia sanitaria")));
        assertEquals(inspector.id(), jdbc.sql("""
                select inspector_id from hiveon.expedientes_sanitarios where id=:id and resuelto_en is not null
                """).param("id", expediente).query(Long.class).single());
    }

    @Test
    void administraUnaCuentaYUnTipoYRegistraLaAuditoria() throws Exception {
        Cuenta admin = cuenta("ADMINISTRADOR");
        Cuenta comprador = cuenta("COMPRADOR");
        String correo = UUID.randomUUID() + "@example.test";
        String tipo = "Tipo prueba " + UUID.randomUUID();
        mvc.perform(get("/administracion").with(user(admin.correo()).roles(admin.rol())))
                .andExpect(content().string(containsString("Catálogo de tipos de colmena")));
        mvc.perform(post("/administracion/usuarios").with(user(admin.correo()).roles(admin.rol()))
                        .with(csrf()).param("nombre", "Usuario de prueba").param("correo", correo)
                        .param("rol", "APICULTOR").param("clave", "ficticia-prueba-11"))
                .andExpect(status().is3xxRedirection());
        var creado = jdbc.sql("select id,contrasena_hash from hiveon.usuarios where correo=:correo")
                .param("correo", correo).query().singleRow();
        assertTrue(contrasenas.matches("ficticia-prueba-11", (String) creado.get("contrasena_hash")));
        String ruta = "/administracion/usuarios/" + creado.get("id");
        mvc.perform(get(ruta).with(user(admin.correo()).roles(admin.rol())))
                .andExpect(content().string(containsString(correo)))
                .andExpect(content().string(not(containsString((String) creado.get("contrasena_hash")))));
        mvc.perform(post(ruta).with(user(admin.correo()).roles(admin.rol()))
                        .with(csrf()).param("nombre", "Usuario editado").param("correo", correo)
                        .param("rol", "OPERARIO"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/administracion/tipos-colmena").with(user(admin.correo()).roles(admin.rol()))
                        .with(csrf()).param("nombre", tipo))
                .andExpect(status().is3xxRedirection());
        long tipoId = jdbc.sql("select id from hiveon.tipos_colmena where nombre=:nombre")
                .param("nombre", tipo).query(Long.class).single();
        mvc.perform(post("/administracion/tipos-colmena/" + tipoId).with(user(admin.correo()).roles(admin.rol()))
                        .with(csrf()).param("nombre", tipo + " editado"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/administracion/auditoria").with(user(admin.correo()).roles(admin.rol())))
                .andExpect(content().string(containsString("CREAR CUENTA")))
                .andExpect(content().string(containsString("ACTUALIZAR TIPO COLMENA")));
        mvc.perform(get("/administracion/auditoria").with(user(comprador.correo()).roles(comprador.rol())))
                .andExpect(status().isForbidden());
    }

    private Cuenta cuenta(String rol) {
        String correo = UUID.randomUUID() + "@example.test";
        long id = jdbc.sql("""
                insert into hiveon.usuarios(rol_codigo,nombre,correo,contrasena_hash)
                values(:rol,:rol,:correo,'ficticio') returning id
                """).param("rol", rol).param("correo", correo).query(Long.class).single();
        return new Cuenta(id, correo, rol);
    }

    private record Cuenta(long id, String correo, String rol) {}
}
