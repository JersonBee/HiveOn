package com.hiveon.controller;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormularioApiarioTests {

    @Test
    void aceptaUbicacionDivipolaYVeredaOpcional() {
        try (var fabrica = Validation.buildDefaultValidatorFactory()) {
            var formulario = new ApiariosController.Formulario("La colmena", "05", "05001", null);
            assertTrue(fabrica.getValidator().validate(formulario).isEmpty());
        }
    }

    @Test
    void rechazaNombreVacioYCodigosMalFormados() {
        try (var fabrica = Validation.buildDefaultValidatorFactory()) {
            var formulario = new ApiariosController.Formulario("   ", "5", "50X01", "");
            var campos = fabrica.getValidator().validate(formulario).stream()
                    .map(error -> error.getPropertyPath().toString()).sorted().toList();
            assertEquals(java.util.List.of("departamento", "municipio", "nombre"), campos);
        }
    }
}
