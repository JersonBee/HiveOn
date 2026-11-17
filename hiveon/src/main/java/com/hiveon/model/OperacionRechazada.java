package com.hiveon.model;

public final class OperacionRechazada extends RuntimeException {

    public enum Motivo { DATOS_INVALIDOS, SIN_ACCESO, NO_ENCONTRADO, CONFLICTO }

    private final Motivo motivo;

    private OperacionRechazada(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }

    static OperacionRechazada datosInvalidos(String mensaje) {
        return new OperacionRechazada(Motivo.DATOS_INVALIDOS, mensaje);
    }

    static OperacionRechazada sinAcceso() {
        return new OperacionRechazada(Motivo.SIN_ACCESO, null);
    }

    static OperacionRechazada noEncontrado() {
        return new OperacionRechazada(Motivo.NO_ENCONTRADO, null);
    }

    static OperacionRechazada conflicto(String mensaje) {
        return new OperacionRechazada(Motivo.CONFLICTO, mensaje);
    }
}
