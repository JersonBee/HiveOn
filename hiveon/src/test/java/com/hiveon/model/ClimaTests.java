package com.hiveon.model;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClimaTests {
    private final HttpClient http = mock(HttpClient.class);
    private final Reloj reloj = new Reloj();
    private final List<HttpRequest> solicitudes = new ArrayList<>();
    private String ubicacion = geo("Bucaramanga", "Santander", "CO", 7.125, -73.11895);
    private String datos;
    private int codigo = 200;
    private IOException problema;
    private Clima clima;

    @BeforeEach
    void preparar() throws Exception {
        clima = new Clima(http, reloj);
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenAnswer(invocacion -> {
                    HttpRequest solicitud = invocacion.getArgument(0);
                    solicitudes.add(solicitud);
                    if (problema != null) return CompletableFuture.failedFuture(problema);
                    boolean esUbicacion = solicitud.uri().getHost().startsWith("geocoding-");
                    return CompletableFuture.completedFuture(respuesta(codigo,
                            esUbicacion ? ubicacion : (datos == null ? pronostico() : datos)));
                });
    }

    @Test
    void consultaDatosRealesDelContratoSinEnviarDatosDeLaCuenta() {
        var resultado = clima.consultar("BUCARAMANGA", "SANTANDER");
        assertTrue(resultado.disponible());
        assertEquals(24.5, resultado.pronostico().temperatura());
        assertEquals(3, resultado.pronostico().dias().size());
        assertEquals("Despejado", resultado.pronostico().dias().getFirst().condicion().texto());
        String busqueda = URLDecoder.decode(solicitudes.getFirst().uri().getQuery(), StandardCharsets.UTF_8);
        assertTrue(busqueda.contains("name=BUCARAMANGA, SANTANDER"));
        assertTrue(busqueda.contains("countryCode=CO"));
        assertTrue(solicitudes.getLast().uri().getQuery().contains("forecast_days=3"));
        assertTrue(solicitudes.stream().allMatch(p -> p.timeout().orElseThrow().equals(Duration.ofSeconds(2))));
        assertTrue(solicitudes.stream().allMatch(p -> p.method().equals("GET")));
    }

    @Test
    void noEscogeElPrimerMunicipioHomonimoNiOtroPais() {
        ubicacion = "{\"results\":[" + lugar("Bucaramanga", "Otro departamento", "CO", 8, -72)
                + "," + lugar("Bucaramanga", "Santander", "VE", 9, -72) + ","
                + lugar("Bucaramanga", "Santander", "CO", 7.125, -73.11895) + "]}";
        assertTrue(clima.consultar("Bucaramanga", "Santander").disponible());
        assertTrue(solicitudes.getLast().uri().getQuery().contains("latitude=7.125"));
    }

    @Test
    void rechazaUbicacionesAmbiguasSinConsultarElPronostico() {
        ubicacion = "{\"results\":[" + lugar("Bucaramanga", "Santander", "CO", 7, -73)
                + "," + lugar("Bucaramanga", "Santander", "CO", 8, -73) + "]}";
        assertEquals(Clima.Estado.UBICACION_AMBIGUA, clima.consultar("Bucaramanga", "Santander").estado());
        assertEquals(1, solicitudes.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"results\":[]}", "{\"results\":[{\"name\":\"Otra ciudad\",\"admin1\":\"Santander\",\"country_code\":\"CO\"}]}"})
    void manejaUbicacionNoEncontrada(String cuerpo) {
        ubicacion = cuerpo;
        assertEquals(Clima.Estado.UBICACION_NO_ENCONTRADA, clima.consultar("Bucaramanga", "Santander").estado());
        assertEquals(1, solicitudes.size());
    }

    @Test
    void normalizaTildesPuntuacionYEspaciosEnLaCoincidenciaYCache() {
        ubicacion = geo("San José", "Bogotá D.C.", "CO", 4.6, -74);
        assertTrue(clima.consultar("SAN JOSE", "BOGOTA DC").disponible());
        assertTrue(clima.consultar("San-José", "Bogotá D. C.").disponible());
        assertEquals(2, solicitudes.size());
    }

    @Test
    void noPublicaUnDatoEsencialNulo() {
        datos = pronostico().replace("[20,21,22]", "[20,null,22]");
        var resultado = clima.consultar("Bucaramanga", "Santander");
        assertEquals(Clima.Estado.DATOS_INCOMPLETOS, resultado.estado());
        assertNull(resultado.pronostico());
    }

    @Test
    void noPublicaArreglosConLongitudesDiferentes() {
        datos = pronostico().replace("[0,3,61]", "[0,3]");
        assertEquals(Clima.Estado.DATOS_INCOMPLETOS, clima.consultar("Bucaramanga", "Santander").estado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"no es JSON", "[]", "{\"error\":true}", "{\"results\":false}"})
    void manejaRespuestaInvalida(String cuerpo) {
        ubicacion = cuerpo;
        assertEquals(Clima.Estado.RESPUESTA_INVALIDA, clima.consultar("Bucaramanga", "Santander").estado());
    }

    @Test
    void rechazaFechasInvalidasYValoresFueraDeRango() {
        datos = pronostico().replace("[10,50,90]", "[10,101,90]");
        assertEquals(Clima.Estado.RESPUESTA_INVALIDA, clima.consultar("Bucaramanga", "Santander").estado());
        reloj.avanzar(Duration.ofMinutes(5));
        datos = pronostico().replace("T07:00", "T99:00");
        assertEquals(Clima.Estado.RESPUESTA_INVALIDA, clima.consultar("Bucaramanga", "Santander").estado());
    }

    @Test
    void distingueLimitesYErroresHttp() {
        codigo = 429;
        assertEquals(Clima.Estado.LIMITE_SOLICITUDES, clima.consultar("Bucaramanga", "Santander").estado());
        codigo = 503;
        assertEquals(Clima.Estado.ERROR_PROVEEDOR, clima.consultar("Medellín", "Antioquia").estado());
    }

    @Test
    void distingueTimeoutYConexion() {
        problema = new HttpTimeoutException("timeout de prueba");
        assertEquals(Clima.Estado.TIEMPO_AGOTADO, clima.consultar("Bucaramanga", "Santander").estado());
        problema = new IOException("conexión de prueba");
        assertEquals(Clima.Estado.CONEXION, clima.consultar("Medellín", "Antioquia").estado());
    }

    @Test
    void cancelaUnaRespuestaCuyoCuerpoNoTerminaDentroDelPlazo() {
        CompletableFuture<HttpResponse<String>> pendiente = new CompletableFuture<>();
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(pendiente);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                assertEquals(Clima.Estado.TIEMPO_AGOTADO, clima.consultar("Bucaramanga", "Santander").estado()));
        assertTrue(pendiente.isCancelled());
    }

    @Test
    void cacheaErroresCincoMinutosSinReintentarEnCadaVisita() {
        codigo = 429;
        clima.consultar("Bucaramanga", "Santander");
        codigo = 200;
        assertFalse(clima.consultar("Bucaramanga", "Santander").disponible());
        assertEquals(1, solicitudes.size());
        reloj.avanzar(Duration.ofMinutes(5));
        assertTrue(clima.consultar("Bucaramanga", "Santander").disponible());
        assertEquals(3, solicitudes.size());
    }

    @Test
    void cacheaPronosticoUnaHoraYUbicacionSieteDias() {
        clima.consultar("Bucaramanga", "Santander");
        reloj.avanzar(Duration.ofMinutes(59));
        clima.consultar("Bucaramanga", "Santander");
        assertEquals(2, solicitudes.size());
        reloj.avanzar(Duration.ofMinutes(1));
        clima.consultar("Bucaramanga", "Santander");
        assertEquals(3, solicitudes.size());
        reloj.avanzar(Duration.ofDays(7));
        clima.consultar("Bucaramanga", "Santander");
        assertEquals(5, solicitudes.size());
    }

    @Test
    void venceElPronosticoAlCambiarElDiaEnColombia() {
        reloj.ahora = Instant.parse("2026-11-23T04:50:00Z");
        var anterior = clima.consultar("Bucaramanga", "Santander");
        reloj.avanzar(Duration.ofMinutes(10));
        var nuevo = clima.consultar("Bucaramanga", "Santander");
        assertEquals(3, solicitudes.size());
        assertEquals(anterior.pronostico().dias().getFirst().fecha().plusDays(1), nuevo.pronostico().dias().getFirst().fecha());
    }

    @Test
    void noMuestraElPronosticoVencidoCuandoFallaLaRenovacion() {
        clima.consultar("Bucaramanga", "Santander");
        reloj.avanzar(Duration.ofHours(1));
        problema = new IOException("sin conexión");
        var resultado = clima.consultar("Bucaramanga", "Santander");
        assertNull(resultado.pronostico());
        assertEquals(Clima.Estado.CONEXION, resultado.estado());
    }

    @Test
    void limitaLaCacheDeErroresA512Municipios() {
        ubicacion = "{}";
        for (int i = 0; i < 513; i++) clima.consultar("Municipio " + i, "Santander");
        clima.consultar("Municipio 0", "Santander");
        assertEquals(514, solicitudes.size());
    }

    private static String geo(String nombre, String departamento, String pais, double latitud, double longitud) {
        return "{\"results\":[" + lugar(nombre, departamento, pais, latitud, longitud) + "]}";
    }

    private static String lugar(String nombre, String departamento, String pais, double latitud, double longitud) {
        return "{\"name\":\"" + nombre + "\",\"admin1\":\"" + departamento + "\",\"country_code\":\""
                + pais + "\",\"latitude\":" + latitud + ",\"longitude\":" + longitud + "}";
    }

    private String pronostico() {
        LocalDate hoy = LocalDate.ofInstant(reloj.instant(), ZoneId.of("America/Bogota"));
        return """
                {"timezone":"America/Bogota",
                "current":{"time":"%sT07:00","temperature_2m":24.5,"wind_speed_10m":4.2},
                "current_units":{"temperature_2m":"°C","wind_speed_10m":"km/h"},
                "daily_units":{"temperature_2m_min":"°C","temperature_2m_max":"°C","precipitation_probability_max":"%%","wind_speed_10m_max":"km/h"},
                "daily":{"time":["%s","%s","%s"],"temperature_2m_min":[20,21,22],"temperature_2m_max":[28,29,30],
                "precipitation_probability_max":[10,50,90],"wind_speed_10m_max":[4,5,6],"weather_code":[0,3,61]}}
                """.formatted(hoy, hoy, hoy.plusDays(1), hoy.plusDays(2));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> respuesta(int codigo, String cuerpo) {
        HttpResponse<String> respuesta = mock(HttpResponse.class);
        when(respuesta.statusCode()).thenReturn(codigo);
        when(respuesta.body()).thenReturn(cuerpo);
        return respuesta;
    }

    private static class Reloj extends Clock {
        Instant ahora = Instant.parse("2026-11-22T12:00:00Z");
        void avanzar(Duration duracion) { ahora = ahora.plus(duracion); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zona) { return Clock.fixed(ahora, zona); }
        @Override public Instant instant() { return ahora; }
    }
}
