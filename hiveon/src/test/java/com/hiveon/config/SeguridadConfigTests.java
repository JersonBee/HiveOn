package com.hiveon.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeguridadConfigTests {

    @Test
    void protegeCredencialesConHashAleatorioYRechazaUnaClaveErronea() {
        var codificador = new SeguridadConfig().codificadorContrasenas();
        String clave = "clave-ficticia";
        String hash = codificador.encode(clave);
        assertNotEquals(clave, hash);
        assertNotEquals(hash, codificador.encode(clave));
        assertTrue(codificador.matches(clave, hash));
        assertFalse(codificador.matches("otra-clave", hash));
    }
}
