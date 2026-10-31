FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copiamos primero solo el pom.xml para aprovechar la caché de Docker
COPY CoreSystem/pom.xml ./

# Pre-descargamos dependencias de Maven de forma segura
RUN mvn dependency:resolve -B --no-transfer-progress || true

# Copiamos el código fuente completo
COPY CoreSystem/src ./src

# Compilamos y empaquetamos el fat JAR
RUN mvn clean package -B --no-transfer-progress -DskipTests


FROM eclipse-temurin:21-jre

LABEL maintainer="WaterManagement Team"
LABEL description="CENTRAL - Sistema de gestión de riego WaterManagement"

WORKDIR /app

# Estrategia 6: Crear directorio para el volumen persistente de SQLite
RUN mkdir -p /app/data

# Copiamos el fat JAR desde el stage de build
COPY --from=build /build/target/central-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
EXPOSE 6000

# Variables de entorno con valores por defecto
ENV PORT=8080
ENV SOCKET_PORT=6000
ENV KAFKA_BROKER=localhost:9092
ENV DB_PATH=/app/data/water_management.db

CMD ["sh", "-c", "java -jar app.jar ${SOCKET_PORT} ${KAFKA_BROKER}"]