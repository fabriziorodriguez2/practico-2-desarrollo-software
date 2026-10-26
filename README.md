# Práctico 4 - Pruebas de seguridad

**Estudiante:** Fabrizio Rodríguez

En este práctico agregué pruebas para comprobar las vulnerabilidades del proyecto y las correcciones del Práctico 2. Los mismos tests están en `main` y en `practico-2`: siempre esperan un comportamiento seguro, sin consultar la rama actual ni forzar fallos.

Hay una diferencia de numeración con el pedido del Práctico 4: en este repositorio `Ejercicio3` contiene File Upload, mientras que SSTI y RCE están en `Ejercicio4`. Por eso las dos pruebas de SpEL apuntan a `Ejercicio4` y también incluí pruebas de upload para cubrir la aplicación de `Ejercicio3`. El informe anterior se conserva en `Practico2.md`, en la rama `practico-2`.

## Qué comprueba cada prueba

### SQL Injection - Ejercicio1/test_sqli.py

La función `buscar_funciones` de `main` concatena la búsqueda y el sentido de ordenamiento en el SQL. En `practico-2` usa un parámetro para la búsqueda y valores permitidos para ordenar.

- La búsqueda normal de `Dune` debe devolver sus tres funciones.
- `' OR 1=1 --` debe devolver cero resultados, porque ninguna película tiene ese nombre. Si devuelve todas las funciones, la entrada está cambiando la consulta.
- `DESC LIMIT 1` en el sentido de ordenamiento no debe recortar los resultados. Esto comprueba que tampoco se puede agregar SQL desde ese parámetro.

Llamo a la función real con SQLite y los datos del inicializador original. Cada test usa una base temporal; el único parche redirige la conexión del inicializador a esa base. No simulo las consultas ni sus resultados y no modifico la base de la aplicación.

### XSS - Ejercicio2/test_xss.py

El problema está en la vista previa de la descripción de `edit.html`: `main` usa el filtro `safe` y deja pasar HTML. La corrección elimina ese filtro para mantener el escape automático de Jinja.

Renderizo la plantilla real con una descripción que contiene un `<script>` y otra que contiene un `<img>` con `onerror`. Verifico que los payloads no aparezcan como etiquetas y que su versión escapada esté tanto en la vista previa como en el textarea. También pruebo una descripción normal.

Son pruebas unitarias del renderizado: la descripción representa el dato almacenado, pero no pruebo el formulario ni ejecuto JavaScript en un navegador.

### File Upload - Ejercicio3/test_upload.py

En `main` se guarda cualquier archivo usando el nombre enviado por el cliente. La mitigación valida extensión, MIME y firma del contenido, limita el tamaño y genera un nombre aleatorio dentro del directorio de uploads.

Envío HTML con extensión `.png` y MIME `image/png`. El test espera que se rechace y que el archivo no quede disponible para descargar. También subo un PNG real de un píxel y verifico que pueda descargar exactamente los mismos bytes desde la ruta que aparece en el listado. Así compruebo el rechazo del contenido falso sin bloquear la subida válida. Estas pruebas no cubren todas las defensas de upload, como el límite de tamaño o path traversal.

### SSTI y RCE - Ejercicio4/test_integracion.py

En `main`, el buscador evalúa la entrada como SpEL con `StandardEvaluationContext`. La corrección elimina el evaluador y usa el texto como búsqueda literal.

- Compruebo que el listado inicial responda HTTP 200 y muestre `Inception`.
- Envío `7*7`: debe conservarse como entrada y no producir el resultado `49`.
- Envío `T(java.lang.Runtime).getRuntime().exec('printf DSS_RCE_CONTROLADA')`: no debe iniciar un proceso ni devolver `Process[pid=...]` como resultado de búsqueda.

El comando de RCE solo imprime una cadena dentro del contenedor, sin escribir archivos ni hacer conexiones externas. El indicador observado es el objeto `Process` devuelto por Java, no la salida de `printf`. Los tests exigen HTTP 200 y el mensaje de búsqueda sin coincidencias: una respuesta 500 tampoco se considera una mitigación correcta.

### Almacenamiento inseguro - Ejercicio5/.../AuthControllerTest.java

En `main`, las contraseñas se cifran con AES/ECB y una clave fija incluida en `EncryptionService`. Es reversible y la misma contraseña produce el mismo cifrado. En `practico-2` se usa BCrypt con salt aleatorio y se deja de enviar la contraseña almacenada a la vista.

Los cuatro tests JUnit comprueban que:

- El valor enviado a `save` sea un BCrypt que reconozca la contraseña correcta y rechace otra.
- Dos usuarios con la misma contraseña reciban valores almacenados diferentes.
- El login siga aceptando la contraseña correcta y rechazando una incorrecta.
- El registro y el login no agreguen `encryptedPassword` al modelo de la vista.

Uso el controlador real y Mockito únicamente para reemplazar el repositorio y capturar lo que se guardaría. No levanto Spring ni H2 para estas pruebas unitarias. Agregué `spring-boot-starter-test` al POM; en `main`, BCrypt se agrega solamente como dependencia de test para verificar el valor guardado. No cambié el código de las aplicaciones.

## Preparación

Los comandos siguientes se ejecutan desde la raíz del repositorio en PowerShell. Necesitás Python y Docker Desktop funcionando con contenedores Linux. Para JUnit uso una imagen con Maven y Java 17, así no hace falta instalarlos en Windows. La primera ejecución necesita Internet para descargar las dependencias e imágenes.

Elegí la rama que quieras comprobar:

```powershell
git switch main
# Para repetir después con las mitigaciones: git switch practico-2
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-test.txt
```

## Ejecutar las pruebas unitarias

```powershell
.\.venv\Scripts\python.exe -m pytest Ejercicio1/test_sqli.py -v
.\.venv\Scripts\python.exe -m pytest Ejercicio2/test_xss.py -v
docker run --rm --mount "type=bind,source=$((Get-Location).Path)\Ejercicio5,target=/app" --mount type=volume,source=practico4-maven,target=/root/.m2 -w /app maven:3.9.11-eclipse-temurin-17-noble mvn -B test
```

Ejecuto los dos ejercicios Flask en procesos separados porque ambos tienen módulos llamados `app.py` y `config.py`. Así Python carga los del ejercicio correspondiente. Maven deja el detalle de JUnit en `Ejercicio5/target/surefire-reports`; el volumen `practico4-maven` conserva las dependencias descargadas.

## Ejecutar las pruebas de integración

Las dos aplicaciones publican el puerto 8080, por eso las ejecuto de a una. Uso los Dockerfiles y Compose existentes. Recreo los contenedores para partir de una base H2 limpia y reconstruyo las imágenes al cambiar de rama, para probar el código correspondiente.

Primero levanto File Upload:

```powershell
docker compose -f Ejercicio3/docker-compose.yml up -d --build --force-recreate
docker compose -f Ejercicio3/docker-compose.yml logs -f
```

Espero a ver `Started CineBuscadorApplication` y salgo de los logs con Ctrl+C. Después:

```powershell
.\.venv\Scripts\python.exe -m pytest Ejercicio3/test_upload.py -v
docker compose -f Ejercicio3/docker-compose.yml down
```

Luego levanto la aplicación que contiene SSTI y RCE:

```powershell
docker compose -f Ejercicio4/docker-compose.yml up -d --build --force-recreate
docker compose -f Ejercicio4/docker-compose.yml logs -f
```

Espero el mismo mensaje de inicio, salgo de los logs con Ctrl+C y ejecuto:

```powershell
.\.venv\Scripts\python.exe -m pytest Ejercicio4/test_integracion.py -v
docker compose -f Ejercicio4/docker-compose.yml down
```

Los bloques de unitarias e integración ejecutan las 15 pruebas. Para comparar las ramas, repito esos bloques después de `git switch practico-2`. Los fallos esperados de `main` hacen que Pytest o Maven terminen con código de salida 1; igual hay que continuar con los demás comandos y bajar los contenedores.

## Resultados obtenidos

Ejecuté las mismas pruebas en ambas ramas el 26/10/2026 con Python 3.13.15, Pytest 8.3.5, Flask 3.0.0 y Requests 2.32.5. Las aplicaciones de integración y JUnit se ejecutaron con Java 17 en Docker; Maven fue 3.9.11.

En `main` obtuve **10 fallos y 5 pruebas aprobadas**:

- SQLi: 2 fallos y 1 aprobada. La inyección devolvió las 21 funciones y el `LIMIT 1` redujo las tres funciones de Dune a una.
- XSS: 2 fallos y 1 aprobada. Ambos payloads aparecieron sin escapar en la vista previa.
- Upload: 1 fallo y 1 aprobada. El HTML disfrazado de PNG quedó disponible con HTTP 200.
- SSTI/RCE: 2 fallos y 1 aprobada. `7*7` produjo `49` y el payload de RCE devolvió `Process[pid=...]`. Ambas solicitudes respondieron HTTP 200.
- Almacenamiento: 3 fallos y 1 aprobada. El valor guardado no era BCrypt, se repetía para la misma contraseña y se exponía al modelo. El login normal funcionó.

En `practico-2` obtuve **15 aprobadas y ningún fallo**: SQLi 3, XSS 3, upload 2, SSTI/RCE 3 y almacenamiento 4. Los casos normales pasaron en ambas ramas. No hubo errores de instalación, compilación, conexión ni tests omitidos en las ejecuciones finales.

Durante la implementación corregí dos detalles de las pruebas: la redirección de upload podía incluir `;jsessionid=...`, y una primera versión del payload RCE fallaba al leer la salida del proceso por las restricciones de reflexión de Java 17. La versión final evita esa lectura y comprueba el proceso devuelto. Volví a ejecutar los tests afectados en ambas ramas.

No verifiqué la ejecución de XSS en un navegador ni la persistencia de usuarios en H2, porque esos casos se probaron como unitarios. Tampoco hay una consigna del Práctico 4 en el repositorio para contrastar su numeración; seguí los requisitos del pedido y la implementación real.

## Conclusión personal

Aprendí que no alcanza con enviar un payload y mirar si la aplicación responde. Tuve que definir qué resultado sería seguro y comprobar también que las funciones normales siguieran andando. Usar las mismas pruebas en las dos ramas me permitió ver la diferencia entre concatenar SQL y parametrizarlo, entre mostrar HTML y escaparlo, y entre evaluar una entrada y tratarla como texto. También entendí por qué cifrar una contraseña con una clave fija no reemplaza un hash con salt.
