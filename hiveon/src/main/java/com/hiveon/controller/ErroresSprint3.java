package com.hiveon.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = {ProduccionController.class, RevisionSanitariaController.class,
        ReportesController.class, AdministracionController.class})
class ErroresSprint3 {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ModelAndView integridad() {
        return vista(HttpStatus.BAD_REQUEST, "Revisa los datos. El código, correo o nombre puede estar en uso "
                + "o la operación ya no está disponible.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ModelAndView estado(ResponseStatusException error) {
        String mensaje = switch (error.getStatusCode().value()) {
            case 403 -> "Tu cuenta no tiene acceso a esta operación.";
            case 404 -> "El registro solicitado no está disponible.";
            default -> error.getReason();
        };
        return vista(error.getStatusCode(), mensaje);
    }

    private static ModelAndView vista(org.springframework.http.HttpStatusCode estado, String mensaje) {
        ModelAndView vista = new ModelAndView("apiarios/operacion-invalida");
        vista.setStatus(estado);
        vista.addObject("mensaje", mensaje);
        vista.addObject("regreso", "/inicio");
        vista.addObject("etiquetaRegreso", "Volver al inicio");
        return vista;
    }
}
