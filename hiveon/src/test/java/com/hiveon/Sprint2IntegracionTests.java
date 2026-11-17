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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@EnabledIfEnvironmentVariable(named = "HIVEON_SPRINT2_INTEGRATION", matches = "true")
class Sprint2IntegracionTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void gestionaApiarioColmenaVinculosYSeguimientoConAislamientoPorRol() throws Exception {
        Cuenta apicultor = cuenta("APICULTOR", "Apicultor");
        Cuenta ajeno = cuenta("APICULTOR", "Otro apicultor");
        Cuenta operario = cuenta("OPERARIO", "Operario");
        Cuenta tecnico = cuenta("TECNICO", "Técnico");
        long apiario = apiario(apicultor);
        String ruta = "/apiarios/" + apiario;
        String hoy = LocalDate.now(ZoneId.of("America/Bogota")).toString();

        mvc.perform(get(ruta + "/gestion").with(user(ajeno.correo()).roles("APICULTOR")))
                .andExpect(status().isNotFound());
        mvc.perform(get(ruta + "/gestion").with(user(operario.correo()).roles("OPERARIO")))
                .andExpect(status().isNotFound());
        mvc.perform(get(ruta + "/editar").with(user(apicultor.correo()).roles("APICULTOR")))
                .andExpect(content().string(containsString("Editar apiario")));
        mvc.perform(post(ruta + "/editar").with(user(apicultor.correo()).roles("APICULTOR")).with(csrf())
                        .param("nombre", "Apiario actualizado").param("departamento", "68")
                        .param("municipio", "68001"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(ruta + "/colmenas").with(user(apicultor.correo()).roles("APICULTOR")).with(csrf())
                        .param("codigo", "C01").param("instalacion", hoy))
                .andExpect(status().is3xxRedirection());
        long colmena = jdbc.sql("select id from hiveon.colmenas where apiario_id=:id")
                .param("id", apiario).query(Long.class).single();
        String rutaColmena = ruta + "/colmenas/" + colmena;
        mvc.perform(get("/apiarios/" + apiario(ajeno) + "/colmenas/" + colmena)
                        .with(user(ajeno.correo()).roles("APICULTOR")))
                .andExpect(status().isNotFound());
        mvc.perform(post(rutaColmena + "/editar").with(user(apicultor.correo()).roles("APICULTOR"))
                        .with(csrf()).param("codigo", "C02"))
                .andExpect(status().is3xxRedirection());

        for (Cuenta invitado : new Cuenta[]{operario, tecnico}) {
            mvc.perform(post(ruta + "/vinculaciones").with(user(apicultor.correo()).roles("APICULTOR"))
                            .with(csrf()).param("correo", invitado.correo()))
                    .andExpect(status().is3xxRedirection());
            mvc.perform(get(ruta + "/gestion").with(user(invitado.correo()).roles(invitado.rol())))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("C02")));
        }
        long vinculo = jdbc.sql("""
                select id from hiveon.vinculaciones_apiario where apiario_id=:apiario and usuario_id=:usuario
                """).param("apiario", apiario).param("usuario", operario.id()).query(Long.class).single();
        mvc.perform(post(rutaColmena + "/tratamientos").with(user(operario.correo()).roles("OPERARIO"))
                        .with(csrf()).param("fecha", hoy).param("descripcion", "Sin permiso"))
                .andExpect(status().isForbidden());
        mvc.perform(post(rutaColmena + "/inspecciones").with(user(operario.correo()).roles("OPERARIO"))
                        .with(csrf()).param("fecha", hoy).param("observaciones", "Revisión de la colmena"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(rutaColmena + "/inspecciones").with(user(apicultor.correo()).roles("APICULTOR"))
                        .with(csrf()).param("fecha", hoy).param("observaciones", "Control del propietario"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(ruta + "/tareas").with(user(apicultor.correo()).roles("APICULTOR")).with(csrf())
                        .param("vinculo", Long.toString(vinculo)).param("descripcion", "Revisar alimento")
                        .param("vencimiento", hoy))
                .andExpect(status().is3xxRedirection());
        long tarea = jdbc.sql("select id from hiveon.tareas where apiario_id=:id")
                .param("id", apiario).query(Long.class).single();
        mvc.perform(post(ruta + "/tareas/" + tarea + "/estado").with(user(operario.correo()).roles("OPERARIO"))
                        .with(csrf()).param("estado", "COMPLETADA"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(rutaColmena + "/tratamientos").with(user(tecnico.correo()).roles("TECNICO"))
                        .with(csrf()).param("fecha", hoy).param("descripcion", "Observación sanitaria de prueba"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(ruta + "/recomendaciones").with(user(tecnico.correo()).roles("TECNICO"))
                        .with(csrf()).param("colmena", Long.toString(colmena)).param("contenido", "Seguimiento de prueba"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get(rutaColmena).with(user(apicultor.correo()).roles("APICULTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Observación sanitaria de prueba")));
        mvc.perform(get(rutaColmena).with(user(operario.correo()).roles("OPERARIO")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Observación sanitaria de prueba"))));
        mvc.perform(get(ruta + "/gestion").with(user(apicultor.correo()).roles("APICULTOR")))
                .andExpect(content().string(containsString("Seguimiento de prueba")))
                .andExpect(content().string(containsString("Observación sanitaria de prueba")))
                .andExpect(content().string(containsString("COMPLETADA")));

        mvc.perform(post(ruta + "/vinculaciones/" + vinculo + "/retirar")
                        .with(user(apicultor.correo()).roles("APICULTOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get(rutaColmena).with(user(operario.correo()).roles("OPERARIO")))
                .andExpect(status().isNotFound());
        assertEquals(operario.id(), jdbc.sql("""
                select autor_id from hiveon.inspecciones where colmena_id=:id and observaciones='Revisión de la colmena'
                """).param("id", colmena).query(Long.class).single());
        mvc.perform(post(rutaColmena + "/archivar").with(user(apicultor.correo()).roles("APICULTOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post(ruta + "/archivar").with(user(apicultor.correo()).roles("APICULTOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get(rutaColmena).with(user(apicultor.correo()).roles("APICULTOR")))
                .andExpect(content().string(containsString("Fuera de operación")))
                .andExpect(content().string(containsString("Revisión de la colmena")));
    }

    private Cuenta cuenta(String rol, String nombre) {
        String correo = UUID.randomUUID() + "@example.test";
        long id = jdbc.sql("""
                insert into hiveon.usuarios(rol_codigo,nombre,correo,contrasena_hash)
                values(:rol,:nombre,:correo,'ficticio') returning id
                """).param("rol", rol).param("nombre", nombre).param("correo", correo)
                .query(Long.class).single();
        return new Cuenta(id, correo, rol);
    }

    private long apiario(Cuenta propietario) {
        return jdbc.sql("""
                insert into hiveon.apiarios(propietario_id,nombre,municipio_codigo)
                values(:id,'Apiario de prueba','68001') returning id
                """).param("id", propietario.id()).query(Long.class).single();
    }

    private record Cuenta(long id, String correo, String rol) {
    }
}
