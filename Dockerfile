# Configuración del Comando de Construcción en Render

Veo que estás configurando tu aplicación en Render y necesitas saber dónde colocar el comando `./mvnw clean package` para compilar tu aplicación Spring Boot.

## ¿Dónde colocar el comando de construcción?

En la interfaz de Render que estás viendo, debes colocar el comando `./mvnw clean package` en el campo **Pre-Deploy Command** (Comando Pre-Despliegue).

Este campo se encuentra en la sección que estás viendo actualmente en la pantalla:

![Pre-Deploy Command Field](https://i.imgur.com/example.png)

## Explicación paso a paso

1. Desplázate hacia abajo en la página de configuración hasta encontrar la sección **Pre-Deploy Command**
2. En el campo de texto vacío, escribe: `./mvnw clean package`
3. Este comando se ejecutará antes de que Render construya la imagen Docker
4. Después de ejecutar este comando, se generará el archivo JAR en la carpeta `target/`
5. Luego, el Dockerfile podrá copiar este archivo JAR usando la instrucción `COPY target/*.jar app.jar`

## Alternativa

Si prefieres no usar el comando pre-despliegue, puedes modificar tu Dockerfile para usar un proceso de construcción multietapa como te sugerí anteriormente:
```
# Etapa de construcción
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package

# Etapa de ejecución
FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]