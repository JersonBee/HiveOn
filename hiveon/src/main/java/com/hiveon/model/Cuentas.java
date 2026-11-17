package com.hiveon.model;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class Cuentas {

    private final JdbcClient jdbc;

    Cuentas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Credenciales> buscarCredenciales(String correo) {
        return jdbc.sql("""
                select correo, contrasena_hash, rol_codigo, habilitado
                from hiveon.usuarios
                where lower(correo) = lower(:correo)
                """).param("correo", correo.strip())
                .query((fila, numero) -> new Credenciales(fila.getString("correo"),
                        fila.getString("contrasena_hash"), fila.getString("rol_codigo"),
                        fila.getBoolean("habilitado")))
                .optional();
    }

    public record Credenciales(String correo, String hash, String rol, boolean habilitada) {}
}
