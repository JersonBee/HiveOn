package com.hiveon.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.hiveon.config.SeguridadConfig;
import com.hiveon.model.Apiarios;
import com.hiveon.model.Clima;
import com.hiveon.model.Cuentas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClimaController.class)
@Import(SeguridadConfig.class)
class ClimaControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean Apiarios apiarios;
    @MockitoBean Clima clima;
    @MockitoBean Cuentas cuentas;

    @ParameterizedTest
    @ValueSource(strings = {"APICULTOR", "OPERARIO", "TECNICO"})
    void muestraHtmlSoloTrasComprobarElAccesoAlApiario(String rol) throws Exception {
        when(apiarios.consultar(7, "clima@example.test")).thenReturn(Optional.of(apiario(false)));
        var fecha = LocalDate.of(2026, 11, 22);
        var dias = List.of(new Clima.Dia(fecha, 20, 28, 40, 12, new Clima.Condicion("Lluvia", "lluvia")),
                new Clima.Dia(fecha.plusDays(1), 21, 29, 20, 10, new Clima.Condicion("Despejado", "sol")),
                new Clima.Dia(fecha.plusDays(2), 21, 30, 15, 11, new Clima.Condicion("Nublado", "nube")));
        when(clima.consultar("BUCARAMANGA", "SANTANDER")).thenReturn(new Clima.Resultado(
                new Clima.Pronostico(24, 8, LocalDateTime.of(2026, 11, 22, 7, 0),
                        Instant.parse("2026-11-22T12:00:00Z"), dias), Clima.Estado.DISPONIBLE));
        mvc.perform(get("/apiarios/7/clima").with(user("clima@example.test").roles(rol)))
                .andExpect(status().isOk()).andExpect(view().name("apiarios/clima"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Pronóstico de referencia municipal")))
                .andExpect(content().string(containsString("Pasado mañana")))
                .andExpect(content().string(containsString("Open-Meteo")))
                .andExpect(content().string(containsString("GeoNames")));
        var orden = inOrder(apiarios, clima);
        orden.verify(apiarios).consultar(7, "clima@example.test");
        orden.verify(clima).consultar("BUCARAMANGA", "SANTANDER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"APICULTOR", "OPERARIO", "TECNICO"})
    void deniegaRegistroAjenoOAccesoRevocadoSinConsultarProveedor(String rol) throws Exception {
        when(apiarios.consultar(7, "sin-acceso@example.test")).thenReturn(Optional.empty());
        mvc.perform(get("/apiarios/7/clima").with(user("sin-acceso@example.test").roles(rol)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(clima);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "INSPECTOR", "COMPRADOR"})
    void deniegaRolesNoOperativosSinConsultarModeloNiProveedor(String rol) throws Exception {
        mvc.perform(get("/apiarios/7/clima").with(user("otra-cuenta@example.test").roles(rol)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(apiarios, clima);
    }

    @Test
    void requiereSesion() throws Exception {
        mvc.perform(get("/apiarios/7/clima")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(apiarios, clima);
    }

    @Test
    void permiteVolverAlApiarioAunqueElProveedorFalleOEsteArchivado() throws Exception {
        when(apiarios.consultar(7, "clima@example.test")).thenReturn(Optional.of(apiario(true)));
        when(clima.consultar("BUCARAMANGA", "SANTANDER"))
                .thenReturn(new Clima.Resultado(null, Clima.Estado.TIEMPO_AGOTADO));
        mvc.perform(get("/apiarios/7/clima").with(user("clima@example.test").roles("APICULTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pronóstico temporalmente no disponible")))
                .andExpect(content().string(containsString("href=\"/apiarios/7\"")))
                .andExpect(content().string(containsString("tardó demasiado")));
    }

    private static Apiarios.Apiario apiario(boolean archivado) {
        return new Apiarios.Apiario(7, "Apiario El Manantial", null, "BUCARAMANGA", "SANTANDER", archivado);
    }
}
