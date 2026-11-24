# Foro Moderación

Microservicio de la Sumativa 2 para banear y restaurar temas y comentarios de un
foro. Spring Boot 3.5.16 y Java 17 como versión objetivo. Puerto predeterminado
8083. Conserva la última decisión de cada recurso en su propia base de datos.

## Cómo funciona

El administrador obtiene un JWT de `foro-usuarios`. Moderación valida su identidad
vigente mediante HTTP, comprueba que el recurso exista en `foro-contenido` y
guarda la decisión. El ID del administrador procede del token validado.

Contenido consulta las decisiones por HTTP antes de mostrar o modificar recursos.
Un tema baneado oculta sus comentarios y bloquea nuevas respuestas. Banear no borra
datos. Restaurar un tema no restaura los comentarios baneados por separado.
Si un tema visible contiene comentarios baneados, su eliminación se rechaza con
409 para conservarlos; los temas sin comentarios baneados conservan su borrado
habitual en cascada.

La consulta administrativa de existencia en contenido usa rutas separadas que
no vuelven a consultar moderación; así se evitan llamadas circulares. No hay tablas,
credenciales de base de datos ni dependencias Java compartidas entre servicios.

## Compilar y probar

Desde esta carpeta, con JDK 17 o superior y `JAVA_HOME` configurado:

```powershell
.\mvnw.cmd verify
```

Maven Wrapper descarga Maven 3.9.14 y las dependencias si faltan. Después puede
usarse `-o` para trabajar sin descargas. El JAR queda en
`target/foro-moderacion-0.0.1-SNAPSHOT.jar`. Los informes están en
`target/surefire-reports/`.

## Ejecución local de los tres servicios

### Arranque de pruebas con Oracle desde PowerShell

`scripts/Iniciar-Oracle.ps1` es compatible con Windows PowerShell 5.1 y
PowerShell 7. Reutiliza el JDK 17 disponible en `BIBLIOTECA/.tools/jdk17`, el
Wallet `C:\Wallet_BIBLIOTECA` y el alias `biblioteca_low`. Sus parámetros
`-JavaPath`, `-WalletPath` y `-Alias` permiten otras ubicaciones.

Desde esta carpeta, ejecutar cada comando en una ventana independiente:

```powershell
.\scripts\Iniciar-Oracle.ps1 -Servicio Usuarios
.\scripts\Iniciar-Oracle.ps1 -Servicio Moderacion
.\scripts\Iniciar-Oracle.ps1 -Servicio Contenido
```

Cada ventana solicita únicamente la contraseña de su esquema. La clave JWT de
usuarios se genera con una API compatible con PowerShell 5.1 si no existe y se
conserva solo en esa terminal para reinicios. Ninguna clave se guarda en disco.
Las variables de conexión se restauran al detener el proceso.

Cada intento genera un log independiente en `SEMANA 5/.verification/oracle`.
El intervalo keepalive de 30 segundos corresponde a una prueba de diagnóstico
de las conexiones intermitentes; no se considera una solución confirmada.
Para validar rutas y alias sin iniciar procesos ni pedir contraseñas:

```powershell
.\scripts\Iniciar-Oracle.ps1 -Servicio Usuarios -ValidarSolamente
```

Para aislar cortes de conexion antes de iniciar aplicaciones, usar:

```powershell
.\scripts\Iniciar-Oracle.ps1 -Servicio Usuarios -ProbarConexion
```

El diagnostico utiliza exactamente los controladores JDBC empaquetados en el JAR
del servicio. No utiliza Spring ni Hikari, no guarda contraseñas y no modifica
datos. Abre tres conexiones en serie, mantiene consultas a la tabla del esquema
durante dos minutos y comprueba una conexion nueva final. `PASS` acredita esas
lecturas durante el intervalo probado; no sustituye las pruebas de negocio.

Usar los proyectos `foro-usuarios` y `foro-contenido` de `SEMANA 2`, con la versión
de contenido modificada para moderación, y este proyecto de `SEMANA 5`.
Cada aplicación se inicia en una terminal independiente.

1. Iniciar usuarios en 8081 siguiendo su README. Requiere `JWT_SECRET_BASE64`.
   Para crear el primer administrador, utilizar su inicializador por entorno.
2. Iniciar moderación en 8083:

```powershell
$env:USUARIOS_BASE_URL = 'http://localhost:8081'
$env:CONTENIDO_BASE_URL = 'http://localhost:8082'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

3. Desde la carpeta de contenido, iniciar en 8082:

```powershell
$env:USUARIOS_BASE_URL = 'http://localhost:8081'
$env:MODERACION_BASE_URL = 'http://localhost:8083'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Los procesos arrancan aunque sus contrapartes aún no estén disponibles. Para
ejecutar el flujo deben estar los tres activos. El perfil local usa H2 en memoria;
sus decisiones se pierden al detener moderación. Para conservarlas usar Oracle.
Mantener las bases de contenido y moderación coherentes: reiniciar una sola base
H2 vacía puede reutilizar IDs mientras la otra conserva decisiones anteriores.

## Endpoints

Enviar `Authorization: Bearer <token>` en todas las peticiones.

| Método y ruta | Permiso | Resultado |
|---|---|---|
| PUT /api/moderacion/temas/{id} | Administrador | Banear o restaurar un tema |
| PUT /api/moderacion/comentarios/{id} | Administrador | Banear o restaurar un comentario |
| GET /api/moderacion/decisiones | Administrador | Última decisión por recurso |
| POST /api/moderacion/visibilidad | Usuario o administrador | IDs baneados dentro del lote solicitado |

PUT recibe exclusivamente:

```json
{"baneado": true, "motivo": "Publicación que incumple las normas"}
```

Para restaurar, enviar `baneado: false` y el motivo de la restauración. El motivo
es obligatorio, de 1 a 500 caracteres después de quitar espacios exteriores.
IDs positivos. No se aceptan campos adicionales como `administradorId` o fechas.

Respuesta 200: `id`, `tipo` (`TEMA` o `COMENTARIO`), `recursoId`, `baneado`,
`motivo`, `administradorId` y `fechaDecision` en UTC. Repetir el mismo estado y
motivo conserva la decisión sin cambiar su autor ni fecha. Existe una sola fila
por tipo e ID; las primeras escrituras concurrentes se resuelven sin duplicarla.

GET admite filtros opcionales `tipo=TEMA|COMENTARIO` y `baneado=true|false`.
El listado se ordena por ID ascendente y no incluye historial ni paginación.

Visibilidad recibe ambas listas, con máximo 200 IDs positivos por lista:

```json
{"temaIds": [10, 11], "comentarioIds": [20]}
```

Devuelve el mismo formato, con solo los IDs baneados. No devuelve motivos ni
datos administrativos. Esta operación no comprueba existencia: informa decisiones
ya persistidas. Contenido agrupa sus consultas en lotes acotados.

## Errores y disponibilidad

- 400: JSON, tipos, campos, motivo, filtro o ID inválidos.
- 401: falta de token, token inválido o usuario desactivado.
- 403: usuario sin permisos para moderar o consultar decisiones.
- 404: recurso inexistente al decidir; en contenido, recurso oculto.
- 409 en contenido: intentar eliminar un tema visible con comentarios baneados.
- 503: identidad o recurso no verificables por fallo de dependencia, timeout
  o respuesta HTTP inválida.

Errores en `application/problem+json`, sin tokens ni mensajes privados de otros
servicios. Los clientes no siguen redirecciones y limitan tamaño y espera.
Las variables `USUARIOS_CONNECT_TIMEOUT`, `USUARIOS_READ_TIMEOUT`,
`CONTENIDO_CONNECT_TIMEOUT` y `CONTENIDO_READ_TIMEOUT` ajustan los límites
(por defecto 2 segundos de conexión y 3 de lectura).

Si moderación no está disponible, contenido devuelve 503 en operaciones que
necesiten comprobar visibilidad. Categorías y registro/login siguen funcionando.
No se promete una transacción distribuida: una operación ya iniciada puede
coincidir con una nueva decisión. Las siguientes consultas reflejan el baneo.

## Oracle Cloud

Crear un esquema exclusivo para moderación y ejecutar una sola vez
`database/01_estructura.sql`. No requiere modificar las tablas de los otros dos
servicios. Para comprobar decisiones y restricciones usar `database/03_verificacion.sql`.

Configurar variables sin guardar contraseñas en archivos ni Git:

```powershell
$env:ORACLE_URL = 'jdbc:oracle:thin:@SERVICIO?TNS_ADMIN=C:/Oracle/Wallet_Foro'
$env:ORACLE_USERNAME = 'FORO_MODERACION'
$taskCredential = Get-Credential -UserName $env:ORACLE_USERNAME -Message 'Credenciales del esquema de moderación'
$env:ORACLE_PASSWORD = $taskCredential.GetNetworkCredential().Password
$env:USUARIOS_BASE_URL = 'http://localhost:8081'
$env:CONTENIDO_BASE_URL = 'http://localhost:8082'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=oracle'
# Al detener:
Remove-Item Env:ORACLE_PASSWORD
```

Servicio, esquema y ruta son ejemplos que deben ajustarse a la instancia real.
Oracle usa `ddl-auto=validate` y no carga scripts automáticamente. Spring Boot no
carga `.env` automáticamente. Usar el perfil Oracle también en usuarios y contenido
para conservar las referencias de los tres servicios entre reinicios.

La ejecución de pruebas H2 no acredita una conexión real a Oracle Cloud.

## Demostración reproducible

Importar `postman/Foro-Moderacion.postman_collection.json`. Completar las variables
privadas de administrador y autor en Postman, sin guardarlas en Git. Ejecutar las
peticiones en orden: login, preparación de datos, rechazo de usuario, baneo,
ocultación, restauración y conservación de comentarios.

Para comprobar automáticamente los tres procesos reales sin Oracle, compilar los
tres proyectos y ejecutar con PowerShell 7:

```powershell
.\scripts\smoke-local.ps1
```

El script usa puertos libres y credenciales efímeras, detiene sus procesos al
terminar y conserva logs sin tokens y el resultado en `SEMANA 5/.verification`.
También reinicia moderación con H2 en archivo para comprobar persistencia. Se
pueden indicar `-UsuariosPath`, `-ContenidoPath` y `-OutputPath` si se cambian las
ubicaciones. No modifica bases Oracle ni instancias ya iniciadas.
