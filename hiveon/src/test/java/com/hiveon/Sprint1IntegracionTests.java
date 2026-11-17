package com.hiveon;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@EnabledIfEnvironmentVariable(named = "HIVEON_SPRINT1_INTEGRATION", matches = "true")
class Sprint1IntegracionTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder codificador;

    @Test
    void registraConsultaYProtegeUnApiarioEnPostgresql() throws Exception {
        String idPrueba = UUID.randomUUID().toString();
        String correo = "sprint1-" + idPrueba + "@example.test";
        String otroCorreo = "sprint1-otro-" + idPrueba + "@example.test";
        String clave = UUID.randomUUID().toString();
        String nombre = "Apiario de prueba " + idPrueba;
        String hash = codificador.encode(clave);
        long propietario = crearApicultor(correo, hash);
        crearApicultor(otroCorreo, hash);

        mvc.perform(get("/")).andExpect(status().isOk());
        MockHttpSession sesion = ingresar(correo, clave);
        mvc.perform(get("/apiarios").session(sesion))
                .andExpect(content().string(containsString("Aún no tienes apiarios registrados.")));
        mvc.perform(get("/apiarios/nuevo").session(sesion).param("departamento", "68"))
                .andExpect(content().string(containsString("BUCARAMANGA")));

        mvc.perform(post("/apiarios").session(sesion).with(csrf())
                        .param("nombre", nombre).param("departamento", "68").param("municipio", "05001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Verifica la ubicación")));
        assertEquals(0, cantidadDeApiarios(propietario));

        mvc.perform(post("/apiarios").session(sesion).with(csrf())
                        .param("nombre", nombre).param("departamento", "68").param("municipio", "68001"))
                .andExpect(redirectedUrl("/apiarios"));
        long apiario = jdbc.sql("""
                select id from hiveon.apiarios
                where propietario_id = :propietario and municipio_codigo = '68001' and nombre = :nombre
                """).param("propietario", propietario).param("nombre", nombre).query(Long.class).single();
        assertEquals(1, cantidadDeApiarios(propietario));
        mvc.perform(get("/apiarios").session(sesion))
                .andExpect(content().string(containsString(nombre)));
        mvc.perform(get("/apiarios/{id}", apiario).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nombre)));

        MockHttpSession otraSesion = ingresar(otroCorreo, clave);
        mvc.perform(get("/apiarios").session(otraSesion))
                .andExpect(content().string(not(containsString(nombre))));
        mvc.perform(get("/apiarios/{id}", apiario).session(otraSesion))
                .andExpect(status().isNotFound());
        mvc.perform(post("/salir").session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/ingresar?salida"));
        mvc.perform(get("/apiarios")).andExpect(status().is3xxRedirection());
    }

    private MockHttpSession ingresar(String correo, String clave) throws Exception {
        var solicitud = mvc.perform(post("/ingresar").with(csrf())
                        .param("username", correo).param("password", clave))
                .andExpect(redirectedUrl("/inicio"))
                .andReturn().getRequest();
        MockHttpSession sesion = (MockHttpSession) solicitud.getSession(false);
        assertNotNull(sesion);
        return sesion;
    }

    private long crearApicultor(String correo, String hash) {
        return jdbc.sql("""
                insert into hiveon.usuarios (rol_codigo, nombre, correo, contrasena_hash)
                values ('APICULTOR', 'Apicultor de prueba', :correo, :hash)
                returning id
                """).param("correo", correo).param("hash", hash).query(Long.class).single();
    }

    private int cantidadDeApiarios(long propietario) {
        return jdbc.sql("select count(*) from hiveon.apiarios where propietario_id = :propietario")
                .param("propietario", propietario).query(Integer.class).single();
    }
}
