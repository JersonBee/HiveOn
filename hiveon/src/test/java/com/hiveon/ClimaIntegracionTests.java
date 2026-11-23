package com.hiveon;

import java.util.UUID;

import com.hiveon.model.Clima;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
@EnabledIfEnvironmentVariable(named = "HIVEON_SPRINT2_INTEGRATION", matches = "true")
class ClimaIntegracionTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean Clima clima;

    @Test
    void validaPropiedadVinculacionYRevocacionEnLaBaseAntesDeConsultarClima() throws Exception {
        Cuenta propietario = cuenta("APICULTOR");
        Cuenta ajeno = cuenta("APICULTOR");
        long apiario = jdbc.sql("""
                insert into hiveon.apiarios(propietario_id,nombre,municipio_codigo)
                values(:id,'Apiario climático ficticio','68001') returning id
                """).param("id", propietario.id()).query(Long.class).single();
        String ruta = "/apiarios/" + apiario + "/clima";
        when(clima.consultar("BUCARAMANGA", "SANTANDER"))
                .thenReturn(new Clima.Resultado(null, Clima.Estado.CONEXION));
        mvc.perform(get(ruta).with(user(propietario.correo()).roles(propietario.rol())))
                .andExpect(status().isOk());
        verify(clima).consultar("BUCARAMANGA", "SANTANDER");
        clearInvocations(clima);
        mvc.perform(get(ruta).with(user(ajeno.correo()).roles(ajeno.rol())))
                .andExpect(status().isNotFound());
        verifyNoInteractions(clima);
        for (String rol : new String[]{"OPERARIO", "TECNICO"}) {
            Cuenta colaborador = cuenta(rol);
            mvc.perform(get(ruta).with(user(colaborador.correo()).roles(rol)))
                    .andExpect(status().isNotFound());
            verifyNoInteractions(clima);
            long vinculo = jdbc.sql("""
                    insert into hiveon.vinculaciones_apiario(apiario_id,usuario_id,funcion)
                    values(:apiario,:usuario,:rol) returning id
                    """).param("apiario", apiario).param("usuario", colaborador.id())
                    .param("rol", rol).query(Long.class).single();
            mvc.perform(get(ruta).with(user(colaborador.correo()).roles(rol))).andExpect(status().isOk());
            verify(clima).consultar("BUCARAMANGA", "SANTANDER");
            clearInvocations(clima);
            jdbc.sql("update hiveon.vinculaciones_apiario set retirada_en=clock_timestamp() where id=:id")
                    .param("id", vinculo).update();
            mvc.perform(get(ruta).with(user(colaborador.correo()).roles(rol)))
                    .andExpect(status().isNotFound());
            verifyNoInteractions(clima);
        }
        jdbc.sql("update hiveon.apiarios set archivado_en=clock_timestamp() where id=:id")
                .param("id", apiario).update();
        mvc.perform(get(ruta).with(user(propietario.correo()).roles(propietario.rol())))
                .andExpect(status().isOk());
    }

    private Cuenta cuenta(String rol) {
        String correo = UUID.randomUUID() + "@example.test";
        long id = jdbc.sql("""
                insert into hiveon.usuarios(rol_codigo,nombre,correo,contrasena_hash)
                values(:rol,'Cuenta de prueba climática',:correo,'ficticio') returning id
                """).param("rol", rol).param("correo", correo).query(Long.class).single();
        return new Cuenta(id, correo, rol);
    }

    private record Cuenta(long id, String correo, String rol) {}
}
