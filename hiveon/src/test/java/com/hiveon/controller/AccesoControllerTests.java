package com.hiveon.controller;

import com.hiveon.config.SeguridadConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccesoController.class)
@Import(SeguridadConfig.class)
class AccesoControllerTests {

    private static final String CORREO = "apicultor@example.test";
    private static final String CORREO_INHABILITADO = "inhabilitado@example.test";
    private static final String CLAVE = "clave-ficticia-de-pruebas";

    @Autowired MockMvc mvc;
    @TestBean(enforceOverride = true) UserDetailsService usuarios;

    static UserDetailsService usuarios() {
        String hash = new BCryptPasswordEncoder().encode(CLAVE);
        return correo -> switch (correo) {
            case CORREO -> User.withUsername(correo).password(hash).roles("APICULTOR").build();
            case CORREO_INHABILITADO -> User.withUsername(correo).password(hash)
                    .roles("APICULTOR").disabled(true).build();
            default -> throw new UsernameNotFoundException("Cuenta de prueba inexistente");
        };
    }

    @Test
    void muestraBienvenidaYFormularioConCsrf() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/img/hiveon-logo.png")));
        mvc.perform(get("/ingresar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/inicio"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/ingresar")));
    }

    @Test
    void iniciaYCierraSesion() throws Exception {
        var ingreso = mvc.perform(post("/ingresar").with(csrf())
                        .param("username", CORREO).param("password", CLAVE))
                .andExpect(redirectedUrl("/inicio"))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) ingreso.getRequest().getSession(false);

        mvc.perform(get("/inicio").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cerrar sesión")));
        mvc.perform(post("/salir").session(sesion))
                .andExpect(status().isForbidden());
        mvc.perform(post("/salir").session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/ingresar?salida"));
        mvc.perform(get("/inicio"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", endsWith("/ingresar")));
    }

    @Test
    void rechazaContrasenaIncorrectaYCuentaInhabilitadaConElMismoMensaje() throws Exception {
        mvc.perform(post("/ingresar").with(csrf())
                        .param("username", CORREO).param("password", "incorrecta"))
                .andExpect(redirectedUrl("/ingresar?error"));
        mvc.perform(post("/ingresar").with(csrf())
                        .param("username", CORREO_INHABILITADO).param("password", CLAVE))
                .andExpect(redirectedUrl("/ingresar?error"));
        mvc.perform(get("/ingresar").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No se pudo iniciar sesión.")));
    }
}
