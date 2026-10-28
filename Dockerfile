# =============================================================================
# STAGE 1: BUILD
# Usamos una imagen con Maven y Java 21 para compilar el proyecto.
# Este stage no llega a producción, solo sirve para generar el JAR.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copiamos primero solo el pom.xml para aprovechar la caché de Docker
COPY CoreSystem/pom.xml ./

# Pre-descargamos dependencias de Maven de forma segura
RUN mvn dependency:resolve -B --no-transfer-progress || true

# Ahora copiamos el código fuente completo
COPY CoreSystem/src ./src

# Compilamos y empaquetamos. El plugin maven-shade genera un fat JAR
# con SQLite embebido en: target/central-1.0-SNAPSHOT.jar
RUN mvn clean package -B --no-transfer-progress -DskipTests


# =============================================================================
# STAGE 2: RUNTIME
# Imagen final ligera (solo JRE, sin JDK ni Maven).
# Solo copiamos el JAR compilado.
# =============================================================================
FROM eclipse-temurin:21-jre

# Metadatos descriptivos
LABEL maintainer="WaterManagement Team"
LABEL description="CENTRAL - Sistema de gestión de riego WaterManagement"

WORKDIR /app

# Copiamos el fat JAR desde el stage de build
COPY --from=build /build/target/central-1.0-SNAPSHOT.jar app.jar

# Puerto HTTP: Dashboard Web (Primero para que Railway detecte PORT=8080)
EXPOSE 8080

# Puerto de sockets TCP: WS (Watering Stations) y operarios (FO)
EXPOSE 6000

# Variables de entorno con valores por defecto.
ENV PORT=8080
ENV SOCKET_PORT=6000
ENV KAFKA_BROKER=localhost:9092

# Ejecutamos la Central pasando puerto de sockets y broker Kafka.
CMD ["sh", "-c", "java -jar app.jar ${SOCKET_PORT} ${KAFKA_BROKER}"]
