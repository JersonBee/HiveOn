package com.hiveon.controller;

import java.util.List;
import java.util.Optional;

import com.hiveon.config.SeguridadConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiariosController.class)
@Import(SeguridadConfig.class)
class ApiariosControllerTests {

    private static final String CORREO = "apiarios@example.test";

    @Autowired MockMvc mvc;
    @MockitoBean JdbcClient jdbc;

    @Test
    void filtraMunicipiosEnElServidor() throws Exception {
        catalogos();
        mvc.perform(get("/apiarios/nuevo").with(user(CORREO).roles("APICULTOR"))
                        .param("departamento", "68"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BUCARAMANGA")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void registraConLaCuentaDeSesionAunqueSeEnvieOtroPropietario() throws Exception {
        JdbcClient.StatementSpec escritura = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        when(jdbc.sql(startsWith("insert into hiveon.apiarios"))).thenReturn(escritura);
        when(escritura.update()).thenReturn(1);

        mvc.perform(post("/apiarios").with(user(CORREO).roles("APICULTOR")).with(csrf())
                        .param("nombre", " Apiario de prueba ").param("departamento", "68")
                        .param("municipio", "68001").param("propietarioId", "999"))
                .andExpect(redirectedUrl("/apiarios"));

        verify(escritura).param("correo", CORREO);
        verify(escritura).param("nombre", "Apiario de prueba");
        verify(escritura).param("municipio", "68001");
    }

    @Test
    void muestraErroresDeFormularioSinInsertar() throws Exception {
        catalogos();
        mvc.perform(post("/apiarios").with(user(CORREO).roles("APICULTOR")).with(csrf())
                        .param("departamento", "68").param("municipio", "68001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingresa el nombre del apiario.")));
        verify(jdbc, never()).sql(startsWith("insert into"));
    }

    @Test
    void muestraUnErrorSiLaCombinacionGeograficaNoSeInserta() throws Exception {
        catalogos();
        JdbcClient.StatementSpec escritura = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        when(jdbc.sql(startsWith("insert into hiveon.apiarios"))).thenReturn(escritura);

        mvc.perform(post("/apiarios").with(user(CORREO).roles("APICULTOR")).with(csrf())
                        .param("nombre", "Apiario").param("departamento", "68").param("municipio", "05001"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Verifica la ubicación")));
        verify(escritura).param("departamento", "68");
        verify(escritura).param("municipio", "05001");
    }

    @Test
    void muestraElListadoVacioYElDetallePropioPeroNoUnRegistroAjeno() throws Exception {
        JdbcClient.MappedQuerySpec<ApiariosController.Apiario> listado =
                consulta("order by a.nombre", ApiariosController.Apiario.class);
        when(listado.list()).thenReturn(List.of());
        mvc.perform(get("/apiarios").with(user(CORREO).roles("APICULTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aún no tienes apiarios registrados.")));

        JdbcClient.MappedQuerySpec<ApiariosController.Apiario> detalle =
                consulta("and a.id = :id", ApiariosController.Apiario.class);
        when(detalle.optional()).thenReturn(
                Optional.of(new ApiariosController.Apiario(7, "Apiario propio", null,
                        "BUCARAMANGA", "SANTANDER", false)))
                .thenReturn(Optional.empty());
        mvc.perform(get("/apiarios/7").with(user(CORREO).roles("APICULTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Apiario propio")));
        mvc.perform(get("/apiarios/8").with(user(CORREO).roles("APICULTOR")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Apiario propio"))));
        mvc.perform(get("/apiarios/7").with(user(CORREO).roles("OPERARIO")))
                .andExpect(status().isForbidden());
    }

    private void catalogos() {
        when(consulta("from hiveon.departamentos order", ApiariosController.Ubicacion.class).list())
                .thenReturn(List.of(new ApiariosController.Ubicacion("68", "SANTANDER")));
        when(consulta("from hiveon.municipios", ApiariosController.Ubicacion.class).list())
                .thenReturn(List.of(new ApiariosController.Ubicacion("68001", "BUCARAMANGA")));
    }

    @SuppressWarnings("unchecked")
    private <T> JdbcClient.MappedQuerySpec<T> consulta(String fragmento, Class<T> tipo) {
        JdbcClient.StatementSpec sentencia = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        JdbcClient.MappedQuerySpec<T> resultados = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(contains(fragmento))).thenReturn(sentencia);
        when(sentencia.query(tipo)).thenReturn(resultados);
        return resultados;
    }
}
