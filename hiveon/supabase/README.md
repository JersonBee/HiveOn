# Base de datos de HiveOn

Las migraciones se aplican en el orden de su nombre. La primera crea
usuarios, roles y apiarios. La segunda incorpora colmenas, vinculaciones,
inspecciones, tareas, tratamientos, recomendaciones y tipos de colmena. La
tercera añade expedientes, producción, lotes y auditoría. La cuarta carga los
catálogos DIVIPOLA y añade restricciones y disparadores de validación. Los
reportes consultan las tablas operativas.

Las tablas están en el esquema privado `hiveon`. Al configurar Supabase, mantén
ese esquema fuera de los esquemas expuestos por la Data API. Conecta Spring por
JDBC/JPA con un usuario PostgreSQL dedicado que tenga los permisos necesarios
sobre el esquema, las tablas y las secuencias. Provisiona la cuenta de prueba
con un hash generado por el codificador de Spring Security. Las migraciones
cargan los seis roles y los catálogos geográficos.

## Decisiones del modelo

- Cada apiario guarda un `municipio_codigo` obligatorio y una vereda opcional.
  El municipio referencia el catálogo DIVIPOLA y determina el departamento.
  El catálogo incluye 33 códigos departamentales, incluido Bogotá D.C., y
  1.103 municipios o distritos. Las islas y áreas no municipalizadas no se
  ofrecen como municipios. Se usa el corte del 30 de diciembre de 2024 del
  [catálogo DANE](https://www.datos.gov.co/Mapas-Nacionales/DIVIPOLA-C-digos-municipios/gdxc-w37w).
- Archivar registra una fecha y conserva el historial. Las vinculaciones
  retiradas permanecen registradas y las tareas retienen su vinculación original.
- Las cosechas se registran en kilogramos. Un lote puede contener varias cosechas
  del mismo apiario y cada cosecha completa se asigna a un solo lote. Las fechas
  de cosecha se consultan desde las cosechas vinculadas.
- Los estados iniciales de tareas son pendiente, en curso y completada. La
  aplicación debe validar las transiciones acordadas al implementar esa historia.

PostgreSQL valida los códigos geográficos, los roles y vínculos necesarios
para registrar operaciones, la vigencia de las asignaciones de tareas, la
resolución de expedientes y los requisitos para publicar un lote. Una vez
publicado, su composición queda protegida. La aplicación obtiene el actor
de la sesión y comprueba los permisos de lectura y escritura en cada
solicitud. El formulario de apiarios envía el código del municipio y usa
el departamento para filtrar el selector.

## Fechas y horas

Las fechas de instalación, inspección, tratamiento y cosecha deben ser finitas
y no pueden ser posteriores al día actual en America/Bogota. Las marcas de
tiempo de hechos registrados tampoco pueden ser infinitas o futuras. El
vencimiento de una tarea puede ser futuro y debe ser igual o posterior a su
día de creación en Bogotá. Las restricciones de orden entre creación,
archivo, retiro, resolución y publicación se mantienen.
