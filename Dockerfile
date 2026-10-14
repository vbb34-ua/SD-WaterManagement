# =============================================================================
# STAGE 1: BUILD
# Usamos una imagen con Maven y Java 21 para compilar el proyecto.
# Este stage no llega a producción, solo sirve para generar el JAR.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copiamos primero solo el pom.xml para aprovechar la caché de Docker
COPY CoreSystem/pom.xml ./

# Pre-descargamos dependencias de Maven
RUN mvn dependency:go-offline -B --no-transfer-progress

# Ahora copiamos el código fuente completo
COPY CoreSystem/src ./src

# Compilamos y empaquetamos. El plugin maven-shade genera un fat JAR
# con SQLite embebido en: target/central-1.0-SNAPSHOT.jar
RUN mvn clean package -B --no-transfer-progress -DskipTests


# =============================================================================
# STAGE 2: RUNTIME
# Imagen final ligera (solo JRE, sin JDK ni Maven).
# Solo copiamos el JAR compilado y el frontend web.
# =============================================================================
FROM eclipse-temurin:21-jre

# Metadatos descriptivos
LABEL maintainer="WaterManagement Team"
LABEL description="CENTRAL - Sistema de gestión de riego WaterManagement"

WORKDIR /app

# Copiamos el fat JAR desde el stage de build
COPY --from=build /build/target/central-1.0-SNAPSHOT.jar app.jar

# Copiamos el frontend web (index.html) que sirve WebDashboard.
# El servidor HTTP busca el archivo en la ruta relativa "web/index.html"
# desde el directorio de trabajo /app, por lo que debe quedar en /app/web/
COPY CoreSystem/src/main/java/web/ ./web/

# Puerto de sockets TCP: WS (Watering Stations) y operarios se conectan aquí
EXPOSE 6000

# Puerto HTTP: Dashboard Web (WebDashboard.java sirve en el 8080)
EXPOSE 8080

# Variables de entorno con valores por defecto.
# SOCKET_PORT: puerto en que escucha WM_Central (args[0] en main)
# KAFKA_BROKER: URL del broker Kafka (args[1] en main)
# Puedes sobreescribirlas al hacer: docker run -e SOCKET_PORT=7000 -e KAFKA_BROKER=mi-kafka:9092
ENV SOCKET_PORT=6000
ENV KAFKA_BROKER=localhost:9092

# Ejecutamos la Central pasando puerto de sockets y broker Kafka.
# Usamos la forma EXEC con sh -c para que las variables de entorno se expandan correctamente.
CMD ["sh", "-c", "java -jar app.jar ${SOCKET_PORT} ${KAFKA_BROKER}"]
