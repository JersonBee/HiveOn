package com.hiveon.controller;

import com.hiveon.model.OperacionRechazada;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = {ProduccionController.class, RevisionSanitariaController.class,
        ReportesController.class, AdministracionController.class})
class ErroresSprint3 {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ModelAndView integridad() {
        return vista(HttpStatus.BAD_REQUEST, "Revisa los datos. El código, correo o nombre puede estar en uso "
                + "o la operación ya no está disponible.");
    }

    @ExceptionHandler(OperacionRechazada.class)
    ModelAndView estado(OperacionRechazada error) {
        String mensaje = switch (error.motivo()) {
            case SIN_ACCESO -> "Tu cuenta no tiene acceso a esta operación.";
            case NO_ENCONTRADO -> "El registro solicitado no está disponible.";
            default -> error.getMessage();
        };
        return vista(estadoHttp(error), mensaje);
    }

    static HttpStatus estadoHttp(OperacionRechazada error) {
        return switch (error.motivo()) {
            case DATOS_INVALIDOS -> HttpStatus.BAD_REQUEST;
            case SIN_ACCESO -> HttpStatus.FORBIDDEN;
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case CONFLICTO -> HttpStatus.CONFLICT;
        };
    }

    private static ModelAndView vista(HttpStatus estado, String mensaje) {
        ModelAndView vista = new ModelAndView("apiarios/operacion-invalida");
        vista.setStatus(estado);
        vista.addObject("mensaje", mensaje);
        vista.addObject("regreso", "/inicio");
        vista.addObject("etiquetaRegreso", "Volver al inicio");
        return vista;
    }
}
