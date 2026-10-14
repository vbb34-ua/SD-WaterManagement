# Water Management System (WMS)

Sistema distribuido de gestión de redes de distribución de agua con monitorización en tiempo real, control de fugas y predicción de demanda.

## 🏗️ Arquitectura

El sistema sigue una arquitectura distribuida basada en **Apache Kafka** y **WebSockets**:

```
+-------------------------------------------------------------------------------------+
|                           WATER MANAGEMENT SYSTEM (WMS)                               |
+-------------------------------------------------------------------------------------+
|                                                                                     |
|   +----------------+     +----------------+     +----------------+                |
|   |      WS-1      |     |      WS-2      |     |      WS-3      |                |
|   |  Operario 1    |     |  Operario 2    |     |  Operario 3    |                |
|   +----------------+     +----------------+     +----------------+                |
|          |                     |                     |                              |
|          +---------------------|---------------------+
|                                |                      (WS) - TCP 6000
|                                v
|   +----------------------------------------------------+                        |
|   |                 WEB DASHBOARD                        |                        |
|   |        (HTML + JavaScript + Chart.js)              |                        |
|   +----------------------------------------------------+                        |
|                                |                      (HTTP) 8080
|                                v
|   +----------------------------------------------------+                        |
|   |                    CENTRAL SERVER                  |                        |
|   |   (Java - WM_Central.java)                         |                        |
|   | - Maneja WS y Dashboard                            |
|   | - Lee sensores del suelo                           |
|   | - Predice demanda futura                            |
|   | - Detecta fugas                                     |
|   +----------------------------------------------------+                        |
|                                |                      (Kafka Client)
|                                v
|   +----------------------------------------------------+                        |
|   |               APACHE KAFKA BROKER                  |                        |
|   |   (broker:29092 - TCP)                             |                        |
|   +----------------------------------------------------+                        |
|          |                      |                        |
|   (Topic: sensores-agua)      (Topic: alertas)        (Topic: consumo)
|          |                      |                        |
|          v                      v                        v
| +-----------------------------------------------------------------------------+   |
| |                        INTERNET OF THINGS (IoT)                             |   |
| |  Sensores de humedad, caudalímetros, presión, calidad del agua              |   |
| +-----------------------------------------------------------------------------+   |

```

## 💻 Tecnologías Utilizadas

- **Core System**: Java 11
- **Messaging**: Apache Kafka (Broker + Client Library)
- **Real-time Communication**: Java WebSocket API
- **Persistence**: SQLite
- **Frontend**: HTML, JavaScript, Chart.js
- **Deployment**: Docker Compose

## 📦 Componentes

### 1. 🧠 WM_Central (Core System)

**Archivo**: `CoreSystem/src/main/java/WM_Central.java`

Es el cerebro del sistema:

#### Funcionalidades
- **Conexión a Kafka**: Se conecta al broker Kafka en `broker:29092`
- **Escucha sensores**: Lee mensajes del topic `sensores-agua`
- **WebSockets**: Gestiona conexiones simultáneas de usuarios (WS-1, WS-2, WS-3)
- **Dashboard Web**: Proporciona datos en tiempo real al dashboard (HTTP 8080)
- **Predicción de demanda**: Usa un modelo simple (`M`) para predecir consumo futuro
- **Detección de fugas**: Identifica anomalías en el consumo de agua
- **Base de datos**: Guarda sensores, operadores, alertas y estadísticas

#### Tópicos Kafka Manejados
- `sensores-agua`: Sensores de humedad y calidad del agua
- `alertas`: Alertas de fugas o problemas
- `consumo`: Registro de consumo de agua
- `prediccion`: Predicción de demanda futura

### 2. 🌐 Web Dashboard (Frontend)

**Archivos**: `CoreSystem/src/main/resources/web/dashboard.html`, `script.js`, `styles.css`

- Interfaz web moderna con gráficos en tiempo real
- Conexión WebSocket al servidor central
- Vista de sensores, predicciones y alertas

### 3. 🔌 Sensores IoT

**Archivos**: `CoreSystem/src/main/resources/data/sensores.db`, `CoreSystem/src/main/java/SensorEmulator.java`

- Base de datos SQLite con 3 sensores de prueba
- Emulador en Java para simular datos en tiempo real
- Valores de humedad, calidad del agua y predicciones

## 🚀 Instalación y Ejecución

### Requisitos Previos

- **Docker** y **Docker Compose** instalados
- **Java 11** (para desarrollo local)

### Opción 1: Ejecución con Docker Compose (Recomendada)

1. **Navegar al directorio raíz**:
```bash
cd /path/to/SD-WaterManagement
```

2. **Construir y ejecutar los contenedores**:
```bash
docker-compose up -d --build
```

3. **Ver logs**:
```bash
docker-compose logs -f
```

4. **Detener servicios**:
```bash
docker-compose down
```

### Opción 2: Ejecución Local (Java + Kafka)

Si prefieres ejecutar localmente sin Docker:

1. **Instalar Apache Kafka** (ver Kafka documentation)
2. **Iniciar Kafka broker**
3. **Compilar y ejecutar el servidor central**:
```bash
cd CoreSystem/src/main/java
javac -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-simple.jar;kafka-clients-3.7.0.jar" *.java
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-simple.jar;kafka-clients-3.7.0.jar" WM_Central 6000 localhost:9092
```

### 📊 Acceso al Sistema

Una vez en ejecución:

- **Dashboard Web**: http://localhost:8080
- **Operarios**: Conectarse vía WebSocket al puerto 6000

## 🛠️ Desarrollo Local

### Crear un sensor personalizado

1. **Editar `CoreSystem/src/main/resources/data/sensores.db`** (usar SQLite CLI o GUI)
2. **Crear tabla `sensores`**:
```sql
CREATE TABLE sensores (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL,
    tipo TEXT NOT NULL,
    ubicacion TEXT,
    umbral_baja REAL,
    umbral_alta REAL,
    ultima_lectura REAL,
    ultima_fecha TEXT,
    CONSTRAINT uk_nombre UNIQUE (nombre)
);
```

3. **Insertar sensores**:
```sql
INSERT INTO sensores (nombre, tipo, ubicacion, umbral_baja, umbral_alta)
VALUES ('sensor-humedad-parque', 'humedad', 'parque_central', 30.0, 60.0);

INSERT INTO sensores (nombre, tipo, ubicacion, umbral_baja, umbral_alta)
VALUES ('sensor-calidad-rio', 'calidad_agua', 'rio_sur', 1.0, 0.5);
```

4. **Reiniciar el sistema** para que lea los nuevos sensores

## 📋 Configuración de Tópicos

El sistema crea automáticamente los tópicos necesarios:

```bash
# Verificar tópicos en Kafka
kafka-topics --bootstrap-server localhost:9092 --list

# Salida esperada:
sensores-agua
alertas
consumo
prediccion
```

## 📈 Métricas del Sistema

El servidor monitorea:
- Número de sensores conectados
- Número de operadores conectados
- Conexiones WS activas
- Lecturas procesadas por segundo
- Alertas generadas
- Predicciones generadas

## 🔐 Seguridad

- Todas las comunicaciones entre componentes están en la red interna de Docker
- El puerto 6000 (WS) y 8080 (HTTP)
