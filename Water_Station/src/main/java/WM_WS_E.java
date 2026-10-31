import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.Scanner;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public class WM_WS_E {

    private final String brokerAddress;
    private final String ipMonitor;
    private final int puertoMonitor;
    private final String idWS;

    private Socket socketMonitor;
    private DataOutputStream salidaMonitor;
    private DataInputStream entradaMonitor;

    private volatile boolean valvulaAbierta = false;
    private volatile boolean estadoSaludCorrecto = true;
    private volatile boolean enEjecucion = true;

    public WM_WS_E(String brokerAddress, String ipMonitor, int puertoMonitor, String idWS) {
        this.brokerAddress = brokerAddress;
        this.ipMonitor = ipMonitor;
        this.puertoMonitor = puertoMonitor;
        this.idWS = idWS;
    }

    public void iniciar() {
        System.out.println("=== ARRANCANDO ENGINE (WM_WS_E) - WS ID: " + idWS + " ===");
        System.out.println("[ENGINE] Broker asignado: " + brokerAddress);

        // Estrategia 1: Conexión con reintentos al Monitor local
        new Thread(this::gestorConexionMonitor, "Hilo-Conexion-Monitor").start();

        new Thread(this::hiloConsolaSimulacion, "Hilo-Consola-Simulacion").start();

        // Estrategia 5: Consumidor real de Kafka
        new Thread(this::hiloConsumidorBroker, "Hilo-Kafka-Consumidor").start();
    }

    // Estrategia 1: Reintentos continuos al conectar con Monitor
    private void gestorConexionMonitor() {
        while (enEjecucion) {
            if (socketMonitor == null || socketMonitor.isClosed()) {
                if (conectarConMonitor()) {
                    bucleAtencionMonitor();
                } else {
                    dormir(2000);
                }
            } else {
                dormir(1000);
            }
        }
    }

    private boolean conectarConMonitor() {
        try {
            System.out.println("[ENGINE] Conectando a Monitor local (" + ipMonitor + ":" + puertoMonitor + ")...");
            socketMonitor = new Socket(ipMonitor, puertoMonitor);
            salidaMonitor = new DataOutputStream(socketMonitor.getOutputStream());
            entradaMonitor = new DataInputStream(socketMonitor.getInputStream());
            System.out.println("[ENGINE] Conectado exitosamente al Monitor.");
            return true;
        } catch (IOException e) {
            System.err.println("[ENGINE] No se pudo conectar con el Monitor: " + e.getMessage() + ". Reintentando...");
            return false;
        }
    }

    private void bucleAtencionMonitor() {
        try {
            while (enEjecucion && socketMonitor != null && !socketMonitor.isClosed()) {
                String mensaje = entradaMonitor.readUTF();

                if ("HEALTH_CHECK".equals(mensaje)) {
                    salidaMonitor.writeUTF(estadoSaludCorrecto ? "OK" : "KO");
                    salidaMonitor.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("\n[ENGINE] ¡ALERTA! Pérdida de enlace con el Monitor local: " + e.getMessage());
            
            // Estrategia 3: Válvula a prueba de fallos (Cierre inmediato por seguridad)
            if (valvulaAbierta) {
                valvulaAbierta = false;
                System.err.println(">>> [FAIL-SAFE] ELECTROVÁLVULA CERRADA DE EMERGENCIA por pérdida de comunicación con Monitor.");
            }
        } finally {
            try {
                if (socketMonitor != null && !socketMonitor.isClosed()) socketMonitor.close();
            } catch (IOException ignored) {}
        }
    }

    // Estrategia 5: Desacoplamiento y Persistencia con Kafka
    private void hiloConsumidorBroker() {
        System.out.println("[ENGINE] Iniciando consumidor Kafka para topic 'wm-eventos'...");
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerAddress);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ws-engine-" + idWS); // Grupo persistente
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList("wm-eventos"));

            while (enEjecucion) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    String key = record.key();
                    String valor = record.value();

                    // Filtrar eventos destinados a esta estación de riego (Key == idWS o Payload incluye idWS)
                    if (idWS.equals(key) || (valor != null && valor.contains(idWS))) {
                        if (valor.startsWith("INICIO_RIEGO")) {
                            valvulaAbierta = true;
                            System.out.println("\n>>> [KAFKA EVENT] INICIO_RIEGO recibido. Electroválvula ABIERTA.");
                        } else if (valor.startsWith("ABORTO_RIEGO") || valor.startsWith("FIN_RIEGO")) {
                            valvulaAbierta = false;
                            System.out.println("\n>>> [KAFKA EVENT] " + valor.split("#")[0] + " recibido. Electroválvula CERRADA.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ENGINE] Error en el consumidor Kafka: " + e.getMessage());
        }
    }

    private void hiloConsolaSimulacion() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("\n[SIMULADOR DE INCIDENCIAS ACTIVO]");
            System.out.println("Pulsa [K + Enter] para forzar respuesta KO (Simular avería/fuga)");
            System.out.println("Pulsa [O + Enter] para restablecer respuesta OK (Normalidad)");

            while (enEjecucion && scanner.hasNextLine()) {
                String comando = scanner.nextLine().trim().toUpperCase();
                if ("K".equals(comando)) {
                    this.estadoSaludCorrecto = false;
                    System.err.println("\n>>> [SIMULACIÓN] Estado cambiado a KO. El Monitor reportará FUGA.");
                } else if ("O".equals(comando)) {
                    this.estadoSaludCorrecto = true;
                    System.out.println("\n>>> [SIMULACIÓN] Estado restablecido a OK.");
                }
            }
        }
    }

    private void dormir(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java WM_WS_E <ip_broker:puerto> <ip_Monitor> <puerto_Monitor> [id_WS]");
            System.exit(1);
        }

        String broker = args[0];
        String ipMonitor = args[1];
        int puertoMonitor = Integer.parseInt(args[2]);
        String idWS = (args.length >= 4) ? args[3] : "WS_01";

        WM_WS_E engine = new WM_WS_E(broker, ipMonitor, puertoMonitor, idWS);
        engine.iniciar();
    }
}