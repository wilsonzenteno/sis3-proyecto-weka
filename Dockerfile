# Etapa de construcción
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY ./demo /app
RUN mvn clean package

# Etapa de ejecución
FROM eclipse-temurin:21-jdk-alpine
VOLUME /tmp
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]