import java.io.*;
import java.net.*;
import java.util.Scanner;

public class WM_WS_E {

    private final String brokerAddress;
    private final String ipMonitor;
    private final int puertoMonitor;

    private Socket socketMonitor;
    private DataOutputStream salidaMonitor;
    private DataInputStream entradaMonitor;

    // Estado simulado que se conmuta por consola (true = OK, false = KO)
    private volatile boolean estadoSaludCorrecto = true;
    private volatile boolean enEjecucion = true;

    public WM_WS_E(String brokerAddress, String ipMonitor, int puertoMonitor) {
        this.brokerAddress = brokerAddress;
        this.ipMonitor = ipMonitor;
        this.puertoMonitor = puertoMonitor;
    }

    public void iniciar() {
        System.out.println("=== ARRANCANDO ENGINE (WM_WS_E) ===");
        System.out.println("[ENGINE] Broker asignado: " + brokerAddress);

        // Conexión con el Monitor local
        if (!conectarConMonitor()) {
            System.err.println("[ENGINE] No se pudo conectar con el Monitor. Abortando...");
            return;
        }

        // Hilo interactivo de consola para simular fallo/recuperación
        new Thread(this::hiloConsolaSimulacion).start();

        // Hilo para escuchar peticiones de la Central a través del Broker de colas
        new Thread(this::hiloConsumidorBroker).start();

        // Bucle principal de respuesta al Health Check del Monitor
        bucleAtencionMonitor();
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
            System.err.println("[ENGINE] Error al conectar con Monitor: " + e.getMessage());
            return false;
        }
    }

    private void bucleAtencionMonitor() {
        try {
            while (enEjecucion) {
                // Espera el mensaje "HEALTH_CHECK" cada segundo
                String mensaje = entradaMonitor.readUTF();

                if ("HEALTH_CHECK".equals(mensaje)) {
                    if (estadoSaludCorrecto) {
                        salidaMonitor.writeUTF("OK");
                    } else {
                        salidaMonitor.writeUTF("KO");
                    }
                    salidaMonitor.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("[ENGINE] Conexión interrumpida con el Monitor: " + e.getMessage());
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
                    System.err.println("\n>>> [SIMULACIÓN] Estado cambiado a KO. El Monitor reportará FUGA a Central.");
                } else if ("O".equals(comando)) {
                    this.estadoSaludCorrecto = true;
                    System.out.println("\n>>> [SIMULACIÓN] Estado restablecido a OK.");
                }
            }
        }
    }

    private void hiloConsumidorBroker() {
        // Simulación del punto de escucha del Broker(Kafka)
        System.out.println("[ENGINE] Hilo consumidor del Broker (" + brokerAddress + ") a la espera de solicitudes...");
        // Aquí se conectará el KafkaConsumer cuando se configure el gestor
        // de colas definitivo
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Uso: java WM_WS_E <ip_broker:puerto> <ip_Monitor> <puerto_Monitor>");
            System.exit(1);
        }

        String broker = args[0];
        String ipMonitor = args[1];
        int puertoMonitor = Integer.parseInt(args[2]);

        WM_WS_E engine = new WM_WS_E(broker, ipMonitor, puertoMonitor);
        engine.iniciar();
    }
}