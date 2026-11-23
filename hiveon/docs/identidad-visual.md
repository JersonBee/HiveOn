# Identidad visual de HiveOn

HiveOn conserva la historia sanitaria y productiva de los apiarios. La identidad
relaciona el cuidado de las abejas con la organización de los registros y el origen
de la miel. La abeja aporta cercanía y el hexágono identifica la colmena.

## Colorimetría

| Color | Valor | Uso |
| --- | --- | --- |
| Miel | `#F1CE7B` | Acciones principales y selección |
| Salvia | `#DCE8D5` | Contexto del proyecto, estados y encabezados de listas |
| Crema | `#FAF8F0` | Fondo de páginas y espacio de trabajo |
| Cacao | `#49392C` | Títulos, texto principal y marca |
| Bosque | `#2D4938` | Enlaces, foco de teclado y texto sobre fondos claros |
| Blanco | `#FFFFFF` | Formularios y superficies de lectura |

Los estados combinan texto y color. Los errores usan una superficie roja suave
y un mensaje explícito. El foco de teclado tiene un contorno visible.

## Marca y tipografía

El símbolo presenta una abeja sonriente dentro de un hexágono. Su versión WebP
tiene fondo transparente y se acompaña del nombre HiveOn en texto HTML.
El único archivo de marca del aplicativo es `static/img/hiveon-simbolo.webp`.
Cabeceras, favicon y README usan ese mismo símbolo. Los archivos anteriores
`hiveon-logo.png` y `hiveon-marca.webp` se retiraron. La marca de navegación se
presenta sin rotación, recoloración ni filtros; los iconos funcionales de hojas,
colmenas y registros no son variantes del logo. Se retiró el sello decorativo del
acceso. La landing conserva su composición y sus recursos aprobados.
Bricolage Grotesque construye la jerarquía de títulos y Nunito facilita la lectura
de formularios y listas. Ambas tipografías se sirven desde la aplicación.

## Composición

La landing presenta HiveOn, explica el valor de los registros y conecta las
funciones con referencias de la FAO y el ICA. Sus fotografías son ilustraciones
generadas con IA, identificadas en la página.

Las vistas operativas comparten navegación por rol, indicación de la sección
activa, listas y formularios. La administración presenta las cuentas en una tabla
que se reorganiza en móvil. Los reportes y antecedentes usan registros legibles.
Los estados vacíos, archivados y de error mantienen el mismo sistema visual.

El interior se complementa con `interior.css`, sin modificar los estilos públicos.
Cada rol tiene una bienvenida, sus accesos y una guía de trabajo correspondiente
a sus permisos. Los listados de apiarios, expedientes y lotes muestran recuentos
de los registros disponibles en la consulta, incluidas las consultas vacías.
Las imágenes de contexto son ilustrativas: no representan fotografías de los
apiarios registrados. Los formularios conservan sus campos y operaciones.

El fondo del espacio de trabajo utiliza una trama vectorial de panal. Los
hexágonos reaparecen en los accesos, la numeración y los encabezados de sección;
los cortes angulares de las fotografías conectan esas divisiones. Los paneles
de lectura conservan superficies opacas para mantener el contraste. La trama
es ornamental y no incorpora variantes ni repeticiones del logo.

Las vistas internas usan una entrada breve y transiciones de navegación CSS
en navegadores compatibles. Los enlaces tienen estados hover y los formularios
resaltan el foco. La preferencia de movimiento reducido desactiva la animación
y los desplazamientos decorativos.

## Interacciones y restricciones

Los enlaces, botones, filas y formularios disponen de estados hover y foco.
La landing usa animaciones CSS de entrada, movimiento suave de la abeja y
revelado de secciones. El contenido permanece accesible cuando el navegador
carece de soporte para animaciones ligadas al desplazamiento.

`prefers-reduced-motion` desactiva el movimiento y el desplazamiento animado.
Las páginas internas usan HTML, CSS y formularios Thymeleaf. El rediseño mantiene
Spring MVC, la autorización por rol y los flujos GET/POST.

## Verificación del rediseño

Se ejecutó la suite completa: 62 pruebas, sin fallos ni omisiones. La revisión
con Edge y Playwright comprobó 20 recorridos de vistas para los seis roles en
anchos de 320, 390, 768 y 1440 píxeles: 80 comprobaciones sin desbordamiento horizontal.
También se verificaron hover, navegación por anclas, movimiento reducido, foco, sección activa, alta administrativa y cierre de sesión. Las cuentas y los registros pertenecen a una base local ficticia.

Se contrastaron la composición, tipografía, color, marca, campos y navegación con
los conceptos visuales. La implementación conserva los campos y operaciones
existentes; el catálogo permite editar sus valores. Las fotografías finales son
recursos independientes y los textos explican el alcance académico del sistema.

## Referencias del proyecto

- El trabajo con polinizadores da contexto al proyecto. [FAO](https://www.fao.org/pollination/es).
- El seguimiento sanitario aporta contexto a los registros de campo. [Programa Sanitario Apícola del ICA](https://www.ica.gov.co/areas/pecuaria/servicios/enfermedades-animales/programa-apicola).
