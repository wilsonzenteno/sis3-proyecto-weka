# Etapa de construcción
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY ./demo /app
RUN mvn clean package

# Etapa de ejecución
FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]