# HiveOn

Aplicación académica de gestión de apiarios. El proyecto usa Java 21 y el
Maven Wrapper incluido.

## Ejecución local en Windows

Usa la cadena JDBC de Supabase y configura `sslmode=verify-full` junto con
`sslrootcert`, que apunta al certificado raíz descargado desde
[Database Settings](https://supabase.com/docs/guides/platform/ssl-enforcement).
El usuario de conexión debe tener acceso al esquema `hiveon`. Las versiones
del esquema están en [las migraciones](supabase/README.md).

```powershell
$env:DB_URL = Read-Host 'URL JDBC de PostgreSQL'
$env:DB_USERNAME = Read-Host 'Usuario de PostgreSQL'
$env:DB_PASSWORD = Read-Host 'Contraseña de PostgreSQL' -MaskInput
.\mvnw.cmd spring-boot:run
```

Usa PowerShell 7 para `-MaskInput`. Al arrancar, Hibernate valida las tablas
que estén mapeadas por entidades. Para ejecutar las pruebas de integración,
configura estas variables con una base de pruebas y ejecuta `.\mvnw.cmd test`.

## Sprint 1

| Historia | Trabajo |
| --- | --- |
| HU-01 | Implementados la bienvenida, el ingreso y el cierre de sesión. El acceso se verificó con cuentas temporales de Apicultor en Supabase. |
| HU-02 | Implementado el registro de apiarios propios con departamentos y municipios DIVIPOLA. |
| HU-03 | Implementados el listado y el detalle de apiarios propios. Los registros ajenos quedan fuera de la consulta. |

Verificar persistencia, validaciones, autorización, navegación móvil y
tiempos de respuesta antes de preparar la demostración.

El ingreso consulta `hiveon.usuarios` por correo. Para una cuenta de prueba,
usa un hash BCrypt sin prefijo en `contrasena_hash`, el rol `APICULTOR` y
`habilitado = true`. Las pruebas de las vistas y los flujos de autenticación
pueden ejecutarse sin base de datos con
`.\mvnw.cmd '-Dtest=AccesoControllerTests,ApiariosControllerTests' test`.

### Verificación del Sprint 1 con PostgreSQL

En el panel de HiveOn en Supabase, abre **Connect** y copia el host de
**Session pooler** si tu red usa IPv4. Conserva el usuario con el sufijo del
proyecto que indica el panel. Para la prueba usa una cuenta PostgreSQL de
pruebas con permisos de lectura y escritura en `hiveon`.

En Windows, codifica la ruta del certificado si la incluyes en la URL JDBC:

```powershell
$certificado = [Uri]::EscapeDataString((Resolve-Path -LiteralPath 'C:/ruta/prod-ca-2021.crt').Path)
$env:DB_URL = "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=verify-full&sslrootcert=$certificado"
```

Con las tres variables JDBC ya configuradas, ejecuta en la misma terminal:

```powershell
$env:HIVEON_SPRINT1_INTEGRATION = 'true'
.\mvnw.cmd '-Dtest=Sprint1IntegracionTests' test
Remove-Item Env:HIVEON_SPRINT1_INTEGRATION
```

La prueba crea dos apicultores ficticios con claves generadas en memoria,
comprueba el alta y el aislamiento de apiarios desde Spring MVC y revierte
las filas de prueba al terminar. Para la demostración visual, abre la
aplicación en un navegador con una cuenta ficticia provisionada aparte y
repite el recorrido en escritorio y móvil.

**Evidencia local.** Con PostgreSQL 16 y las migraciones aplicadas, pasaron
las ocho pruebas MVC, la prueba de arranque y la de integración del Sprint 1.
En Edge se comprobó el registro y la consulta en escritorio, la vista móvil a
390 px, la denegación de un apiario ajeno y el cierre de sesión. Una medición
local del flujo dio 1.502 ms para el registro y 204 ms para la consulta.
PostgreSQL conservó el registro al detener Spring.

**Evidencia en Supabase.** `Sprint1IntegracionTests` pasó con PostgreSQL 17
a través del Session pooler de HiveOn y TLS con `verify-full`. Se comprobaron
el inicio y cierre de sesión, el rechazo de una combinación geográfica
inválida, el registro y la consulta de un apiario propio y la denegación de
acceso para una segunda cuenta.

El recorrido en Edge también pasó con Supabase. El registro tardó 1.509 ms
y la consulta 328 ms en esa ejecución. Se comprobó el aislamiento de una
segunda cuenta en la vista móvil y que el registro permanecía en Supabase
tras detener Spring. Los datos de demostración fueron retirados al terminar.

## Sprint 2

Desde el detalle de un apiario, **Colmenas y seguimiento** abre su gestión.
El Apicultor puede editar y archivar sus registros, gestionar vinculaciones
por correo, registrar inspecciones y asignar tareas. El Operario ve los
apiarios asignados, registra inspecciones y actualiza sus tareas. El Técnico
consulta los apiarios que asesora y registra tratamientos y recomendaciones.

Retirar una vinculación elimina el acceso del usuario al apiario. El archivo
conserva los registros previos y habilita su consulta según el rol.

Para ejecutar la prueba de integración con las migraciones aplicadas:

```powershell
$env:HIVEON_SPRINT2_INTEGRATION = 'true'
.\mvnw.cmd '-Dtest=Sprint2IntegracionTests' test
Remove-Item Env:HIVEON_SPRINT2_INTEGRATION
```

La prueba usa cuentas ficticias y revierte sus datos.

## Sprint 3

- El Apicultor registra cosechas en kilogramos, prepara lotes con cosechas
  de un mismo apiario, publica lotes y consulta reportes sanitarios,
  productivos y de tareas pendientes. La composición se edita mientras el
  lote es un borrador.
- El Apicultor remite expedientes y el Inspector Sanitario simulado revisa
  antecedentes y registra una decisión interna por expediente.
- El Comprador accede con su cuenta a los lotes publicados. La vista muestra
  el origen registrado y las fechas de cosecha.
- El Administrador crea y actualiza cuentas con un único rol, mantiene el
  catálogo de tipos de colmena y consulta los eventos de auditoría. La
  primera cuenta de Administrador se provisiona directamente en PostgreSQL
  con un hash BCrypt, siguiendo las mismas reglas de las cuentas de prueba.

Con las variables JDBC de una base de pruebas y las migraciones aplicadas,
ejecuta las integraciones y la regresión en PowerShell:

```powershell
$env:HIVEON_SPRINT1_INTEGRATION = 'true'
$env:HIVEON_SPRINT2_INTEGRATION = 'true'
$env:HIVEON_SPRINT3_INTEGRATION = 'true'
.\mvnw.cmd test
Remove-Item Env:HIVEON_SPRINT1_INTEGRATION,Env:HIVEON_SPRINT2_INTEGRATION,Env:HIVEON_SPRINT3_INTEGRATION
```
