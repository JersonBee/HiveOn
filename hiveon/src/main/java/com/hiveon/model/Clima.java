package com.hiveon.model;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Consulta meteorológica municipal; no recibe direcciones externas del navegador. */
@Component
public class Clima {
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final Duration LIMITE = Duration.ofSeconds(2);
    private final HttpClient http;
    private final Clock reloj;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Cache<Coordenadas> ubicaciones = new Cache<>();
    private final Cache<Pronostico> pronosticos = new Cache<>();
    private final Cache<Estado> errores = new Cache<>();

    public Clima() {
        this(HttpClient.newBuilder().connectTimeout(LIMITE)
                .followRedirects(HttpClient.Redirect.NEVER).build(), Clock.systemUTC());
    }

    Clima(HttpClient http, Clock reloj) {
        this.http = http;
        this.reloj = reloj;
    }

    public Resultado consultar(String municipio, String departamento) {
        String clave = normalizar(municipio) + "|" + normalizar(departamento);
        Instant ahora = reloj.instant();
        Pronostico previo = pronosticos.obtener(clave, ahora);
        if (previo != null) return new Resultado(previo, Estado.DISPONIBLE);
        Estado error = errores.obtener(clave, ahora);
        if (error != null) return new Resultado(null, error);
        try {
            Coordenadas punto = ubicaciones.obtener(clave, ahora);
            if (punto == null) {
                punto = localizar(municipio, departamento);
                ubicaciones.guardar(clave, punto, reloj.instant().plus(Duration.ofDays(7)));
            }
            Pronostico pronostico = pronosticar(punto);
            Instant finHora = pronostico.consultadoEn().plus(Duration.ofHours(1));
            Instant medianoche = LocalDate.ofInstant(pronostico.consultadoEn(), ZONA)
                    .plusDays(1).atStartOfDay(ZONA).toInstant();
            pronosticos.guardar(clave, pronostico, finHora.isBefore(medianoche) ? finHora : medianoche);
            return new Resultado(pronostico, Estado.DISPONIBLE);
        } catch (Fallo fallo) {
            error = fallo.estado;
        } catch (HttpTimeoutException fallo) {
            error = Estado.TIEMPO_AGOTADO;
        } catch (IOException fallo) {
            error = Estado.CONEXION;
        } catch (InterruptedException fallo) {
            Thread.currentThread().interrupt();
            error = Estado.CONEXION;
        } catch (JacksonException | IllegalArgumentException | DateTimeException fallo) {
            error = Estado.RESPUESTA_INVALIDA;
        }
        errores.guardar(clave, error, reloj.instant().plus(Duration.ofMinutes(5)));
        return new Resultado(null, error);
    }

    private Coordenadas localizar(String municipio, String departamento) throws IOException, InterruptedException {
        String nombre = URLEncoder.encode(municipio + ", " + departamento, StandardCharsets.UTF_8);
        JsonNode respuesta = solicitar("https://geocoding-api.open-meteo.com/v1/search?name=" + nombre
                + "&countryCode=CO&language=es&count=100&format=json");
        JsonNode resultados = respuesta.path("results");
        if (resultados.isMissingNode()) throw new Fallo(Estado.UBICACION_NO_ENCONTRADA);
        if (!resultados.isArray()) throw new Fallo(Estado.RESPUESTA_INVALIDA);
        List<Coordenadas> candidatos = new ArrayList<>();
        for (JsonNode lugar : resultados) {
            if ("CO".equals(lugar.path("country_code").asString())
                    && normalizar(municipio).equals(normalizar(lugar.path("name").asString()))
                    && normalizar(departamento).equals(normalizar(lugar.path("admin1").asString()))) {
                double latitud = numero(lugar.path("latitude"));
                double longitud = numero(lugar.path("longitude"));
                if (latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
                    throw new Fallo(Estado.RESPUESTA_INVALIDA);
                }
                Coordenadas candidato = new Coordenadas(latitud, longitud);
                if (!candidatos.contains(candidato)) candidatos.add(candidato);
            }
        }
        if (candidatos.isEmpty()) throw new Fallo(Estado.UBICACION_NO_ENCONTRADA);
        if (candidatos.size() != 1) throw new Fallo(Estado.UBICACION_AMBIGUA);
        return candidatos.getFirst();
    }

    private Pronostico pronosticar(Coordenadas punto) throws IOException, InterruptedException {
        JsonNode respuesta = solicitar("https://api.open-meteo.com/v1/forecast?latitude=" + punto.latitud()
                + "&longitude=" + punto.longitud()
                + "&current=temperature_2m,wind_speed_10m"
                + "&daily=temperature_2m_min,temperature_2m_max,precipitation_probability_max,wind_speed_10m_max,weather_code"
                + "&forecast_days=3&timezone=America%2FBogota&temperature_unit=celsius&wind_speed_unit=kmh");
        if (!"America/Bogota".equals(respuesta.path("timezone").asString())) {
            throw new Fallo(Estado.RESPUESTA_INVALIDA);
        }
        JsonNode actual = respuesta.path("current");
        JsonNode unidades = respuesta.path("current_units");
        JsonNode diarias = respuesta.path("daily_units");
        if (!"°C".equals(unidades.path("temperature_2m").asString())
                || !"km/h".equals(unidades.path("wind_speed_10m").asString())
                || !"°C".equals(diarias.path("temperature_2m_min").asString())
                || !"°C".equals(diarias.path("temperature_2m_max").asString())
                || !"%".equals(diarias.path("precipitation_probability_max").asString())
                || !"km/h".equals(diarias.path("wind_speed_10m_max").asString())) {
            throw new Fallo(Estado.DATOS_INCOMPLETOS);
        }
        double temperatura = numero(actual.path("temperature_2m"));
        double viento = noNegativo(numero(actual.path("wind_speed_10m")));
        LocalDateTime horaActual = LocalDateTime.parse(texto(actual.path("time")));
        JsonNode diario = respuesta.path("daily");
        for (String campo : List.of("time", "temperature_2m_min", "temperature_2m_max",
                "precipitation_probability_max", "wind_speed_10m_max", "weather_code")) {
            if (!diario.path(campo).isArray() || diario.path(campo).size() != 3) {
                throw new Fallo(Estado.DATOS_INCOMPLETOS);
            }
        }
        Instant consultadoEn = reloj.instant();
        LocalDate hoy = LocalDate.ofInstant(consultadoEn, ZONA);
        List<Dia> dias = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LocalDate fecha = LocalDate.parse(texto(diario.path("time").path(i)));
            double minima = numero(diario.path("temperature_2m_min").path(i));
            double maxima = numero(diario.path("temperature_2m_max").path(i));
            double lluvia = noNegativo(numero(diario.path("precipitation_probability_max").path(i)));
            double vientoMaximo = noNegativo(numero(diario.path("wind_speed_10m_max").path(i)));
            JsonNode codigo = diario.path("weather_code").path(i);
            if (!codigo.isIntegralNumber() || !fecha.equals(hoy.plusDays(i)) || minima > maxima || lluvia > 100) {
                throw new Fallo(Estado.RESPUESTA_INVALIDA);
            }
            dias.add(new Dia(fecha, minima, maxima, lluvia, vientoMaximo, condicion(codigo.intValue())));
        }
        return new Pronostico(temperatura, viento, horaActual, consultadoEn, List.copyOf(dias));
    }

    private JsonNode solicitar(String direccion) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(direccion)).timeout(LIMITE)
                .header("Accept", "application/json").GET().build();
        // El plazo cubre también la recepción completa del cuerpo, no solo las cabeceras.
        var pendiente = http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponse<String> respuesta;
        try {
            respuesta = pendiente.get(LIMITE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException fallo) {
            pendiente.cancel(true);
            throw new HttpTimeoutException("Se agotó el plazo de la consulta meteorológica.");
        } catch (InterruptedException fallo) {
            pendiente.cancel(true);
            throw fallo;
        } catch (ExecutionException fallo) {
            if (fallo.getCause() instanceof IOException causa) throw causa;
            throw new IOException("No se pudo completar la consulta meteorológica.", fallo.getCause());
        }
        if (respuesta.statusCode() == 429) throw new Fallo(Estado.LIMITE_SOLICITUDES);
        if (respuesta.statusCode() != 200) throw new Fallo(Estado.ERROR_PROVEEDOR);
        JsonNode cuerpo = json.readTree(respuesta.body());
        if (cuerpo == null || !cuerpo.isObject() || cuerpo.path("error").asBoolean(false)) {
            throw new Fallo(Estado.RESPUESTA_INVALIDA);
        }
        return cuerpo;
    }

    private static double numero(JsonNode nodo) {
        if (!nodo.isNumber()) throw new Fallo(Estado.DATOS_INCOMPLETOS);
        double valor = nodo.doubleValue();
        if (!Double.isFinite(valor)) throw new Fallo(Estado.RESPUESTA_INVALIDA);
        return valor;
    }

    private static double noNegativo(double valor) {
        if (valor < 0) throw new Fallo(Estado.RESPUESTA_INVALIDA);
        return valor;
    }

    private static String texto(JsonNode nodo) {
        if (!nodo.isString() || nodo.asString().isBlank()) throw new Fallo(Estado.DATOS_INCOMPLETOS);
        return nodo.asString();
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
    }

    private static Condicion condicion(int codigo) {
        return switch (codigo) {
            case 0 -> new Condicion("Despejado", "sol");
            case 1, 2 -> new Condicion("Parcialmente nublado", "nube");
            case 3 -> new Condicion("Nublado", "nube");
            case 45, 48 -> new Condicion("Niebla", "nube");
            case 51, 53, 55, 56, 57 -> new Condicion("Llovizna", "lluvia");
            case 61, 63, 65, 66, 67, 80, 81, 82 -> new Condicion("Lluvia", "lluvia");
            case 71, 73, 75, 77, 85, 86 -> new Condicion("Nieve", "nube");
            case 95, 96, 99 -> new Condicion("Tormenta", "tormenta");
            default -> new Condicion("Condición no clasificada", "nube");
        };
    }

    public enum Estado {
        DISPONIBLE(""),
        UBICACION_NO_ENCONTRADA("No se encontró una ubicación coincidente para este municipio y departamento."),
        UBICACION_AMBIGUA("La ubicación municipal es ambigua. No se seleccionó un punto de referencia."),
        DATOS_INCOMPLETOS("El proveedor no devolvió todos los datos necesarios para mostrar el pronóstico."),
        RESPUESTA_INVALIDA("El proveedor devolvió una respuesta que no se pudo validar."),
        LIMITE_SOLICITUDES("El proveedor alcanzó su límite de consultas. Inténtalo más tarde."),
        TIEMPO_AGOTADO("El proveedor tardó demasiado en responder. Inténtalo más tarde."),
        CONEXION("No fue posible conectar con el proveedor. Inténtalo más tarde."),
        ERROR_PROVEEDOR("El proveedor no pudo atender la consulta. Inténtalo más tarde.");

        private final String mensaje;
        Estado(String mensaje) { this.mensaje = mensaje; }
    }

    public record Resultado(Pronostico pronostico, Estado estado) {
        public boolean disponible() { return pronostico != null; }
        public String mensaje() { return estado.mensaje; }
    }
    public record Pronostico(double temperatura, double viento, LocalDateTime horaActual,
                             Instant consultadoEn, List<Dia> dias) {
        public LocalDateTime consultaLocal() { return LocalDateTime.ofInstant(consultadoEn, ZONA); }
    }
    public record Dia(LocalDate fecha, double minima, double maxima, double lluvia,
                      double vientoMaximo, Condicion condicion) {}
    public record Condicion(String texto, String icono) {}
    private record Coordenadas(double latitud, double longitud) {}
    private record Entrada<T>(T valor, Instant venceEn) {}

    private static final class Cache<T> {
        private final LinkedHashMap<String, Entrada<T>> entradas = new LinkedHashMap<>(16, .75f, true);
        synchronized T obtener(String clave, Instant ahora) {
            Entrada<T> entrada = entradas.get(clave);
            if (entrada == null) return null;
            if (!ahora.isBefore(entrada.venceEn())) { entradas.remove(clave); return null; }
            return entrada.valor();
        }
        synchronized void guardar(String clave, T valor, Instant venceEn) {
            entradas.put(clave, new Entrada<>(valor, venceEn));
            if (entradas.size() > 512) entradas.remove(entradas.keySet().iterator().next());
        }
    }

    private static final class Fallo extends RuntimeException {
        private final Estado estado;
        Fallo(Estado estado) { this.estado = estado; }
    }
}
