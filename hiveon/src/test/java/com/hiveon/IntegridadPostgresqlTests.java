package com.hiveon;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "HIVEON_DATABASE_INTEGRATION", matches = "true")
class IntegridadPostgresqlTests {

    @Autowired JdbcClient jdbc;

    private long propietario;
    private long apiario;

    @BeforeEach
    void prepararApiarioPropio() {
        propietario = cuenta("APICULTOR");
        apiario = jdbc.sql("""
                insert into hiveon.apiarios(propietario_id,nombre,municipio_codigo)
                values(:propietario,'Apiario de pruebas SQL','68001') returning id
                """).param("propietario", propietario).query(Long.class).single();
    }

    @Test
    void exigeUnMunicipioDelCatalogo() {
        rechaza("23503", "municipio", () -> jdbc.sql("""
                update hiveon.apiarios set municipio_codigo='00000' where id=:apiario
                """).param("apiario", apiario).update());
    }

    @ParameterizedTest
    @ValueSource(strings = {"(clock_timestamp() at time zone 'America/Bogota')::date + 1", "'infinity'::date"})
    void rechazaFechasDeCosechaFuturasOInfinitas(String fecha) {
        rechaza("23514", "cosechas.fecha", () -> cosecha(fecha));
    }

    @ParameterizedTest
    @ValueSource(strings = {"clock_timestamp() + interval '1 day'", "'infinity'::timestamptz"})
    void rechazaMarcasDeArchivoFuturasOInfinitas(String fecha) {
        rechaza("23514", "apiarios.archivado_en", () -> jdbc.sql(
                "update hiveon.apiarios set archivado_en=" + fecha + " where id=:apiario")
                .param("apiario", apiario).update());
    }

    @Test
    void exigeQueLaCosechaLaRegistreElPropietario() {
        long ajeno = cuenta("APICULTOR");
        rechaza("23514", "propietario del apiario", () -> jdbc.sql("""
                insert into hiveon.cosechas(apiario_id,autor_id,fecha,cantidad_kg)
                values(:apiario,:autor,(clock_timestamp() at time zone 'America/Bogota')::date,2.125)
                """).param("apiario", apiario).param("autor", ajeno).update());
    }

    @Test
    void permiteVencimientoFuturoYRechazaUnVencimientoAnteriorALaCreacion() {
        long vinculacion = vincularOperario();
        long tarea = jdbc.sql("""
                insert into hiveon.tareas(apiario_id,vinculacion_id,creador_id,descripcion,vencimiento)
                values(:apiario,:vinculacion,:propietario,'Tarea de prueba',
                    (current_timestamp at time zone 'America/Bogota')::date + 1) returning id
                """).param("apiario", apiario).param("vinculacion", vinculacion)
                .param("propietario", propietario).query(Long.class).single();

        rechaza("23514", "tareas_vencimiento_valido", () -> jdbc.sql("""
                update hiveon.tareas set vencimiento=(creada_en at time zone 'America/Bogota')::date - 1
                where id=:tarea
                """).param("tarea", tarea).update());
    }

    @Test
    void impideAsignarTareasConUnaVinculacionRetirada() {
        long vinculacion = vincularOperario();
        assertEquals(1, jdbc.sql("""
                update hiveon.vinculaciones_apiario set retirada_en=clock_timestamp() where id=:id
                """).param("id", vinculacion).update());

        rechaza("23514", "vigente de operario", () -> jdbc.sql("""
                insert into hiveon.tareas(apiario_id,vinculacion_id,creador_id,descripcion,vencimiento)
                values(:apiario,:vinculacion,:propietario,'Tarea de prueba',
                    (current_timestamp at time zone 'America/Bogota')::date)
                """).param("apiario", apiario).param("vinculacion", vinculacion)
                .param("propietario", propietario).update());
    }

    @Test
    void exigeCosechasParaPublicar() {
        long lote = loteBorrador();
        rechaza("23514", "al menos una cosecha", () -> publicar(lote));
    }

    @Test
    void conservaLaComposicionDeUnLotePublicado() {
        long lote = loteBorrador();
        long cosecha = cosecha("(clock_timestamp() at time zone 'America/Bogota')::date");
        assertEquals(1, jdbc.sql("""
                insert into hiveon.lote_cosechas(apiario_id,lote_id,cosecha_id)
                values(:apiario,:lote,:cosecha)
                """).param("apiario", apiario).param("lote", lote).param("cosecha", cosecha).update());
        assertEquals(1, publicar(lote));

        rechaza("23514", "lote sin publicar", () -> jdbc.sql("""
                delete from hiveon.lote_cosechas where cosecha_id=:cosecha
                """).param("cosecha", cosecha).update());
    }

    private long cuenta(String rol) {
        return jdbc.sql("""
                insert into hiveon.usuarios(rol_codigo,nombre,correo,contrasena_hash)
                values(:rol,'Prueba SQL',:correo,'ficticio') returning id
                """).param("rol", rol).param("correo", UUID.randomUUID() + "@example.test")
                .query(Long.class).single();
    }

    private long cosecha(String fechaSql) {
        return jdbc.sql("""
                insert into hiveon.cosechas(apiario_id,autor_id,fecha,cantidad_kg)
                values(:apiario,:autor,%s,2.125) returning id
                """.formatted(fechaSql)).param("apiario", apiario).param("autor", propietario)
                .query(Long.class).single();
    }

    private long loteBorrador() {
        return jdbc.sql("""
                insert into hiveon.lotes_miel(apiario_id,codigo)
                values(:apiario,:codigo) returning id
                """).param("apiario", apiario).param("codigo", UUID.randomUUID().toString())
                .query(Long.class).single();
    }

    private long vincularOperario() {
        return jdbc.sql("""
                insert into hiveon.vinculaciones_apiario(apiario_id,usuario_id,funcion)
                values(:apiario,:operario,'OPERARIO') returning id
                """).param("apiario", apiario).param("operario", cuenta("OPERARIO")).query(Long.class).single();
    }

    private int publicar(long lote) {
        return jdbc.sql("update hiveon.lotes_miel set publicado_en=clock_timestamp() where id=:lote")
                .param("lote", lote).update();
    }

    private void rechaza(String estado, String mensaje, Runnable operacion) {
        var error = assertThrows(DataIntegrityViolationException.class, operacion::run);
        var postgres = assertInstanceOf(SQLException.class, error.getMostSpecificCause());
        assertEquals(estado, postgres.getSQLState());
        assertTrue(postgres.getMessage().contains(mensaje), postgres::getMessage);
    }
}
