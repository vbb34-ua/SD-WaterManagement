import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;

public class WM_Central {

    public static void main(String[] args) {

        String envPortWeb = System.getenv("PORT");
        if (envPortWeb != null) envPortWeb = envPortWeb.replace("\"", "").trim();

        String envPortSocket = System.getenv("SOCKET_PORT");
        if (envPortSocket != null) envPortSocket = envPortSocket.replace("\"", "").trim();

        String envKafkaBroker = System.getenv("KAFKA_BROKER");
        if (envKafkaBroker != null) envKafkaBroker = envKafkaBroker.replace("\"", "").trim();

        // 2. ASIGNAR PUERTOS DE FORMA SEGURA
        int webPort = (envPortWeb != null && !envPortWeb.isEmpty()) ? Integer.parseInt(envPortWeb) : 8080;
        int socketPort = (envPortSocket != null && !envPortSocket.isEmpty()) ? Integer.parseInt(envPortSocket) : 6000;
        String kafkaBroker = (envKafkaBroker != null && !envKafkaBroker.isEmpty()) ? envKafkaBroker : "localhost:9092";

        System.out.println("--- INICIANDO CENTRAL ---");
        System.out.println("Puerto Web: " + webPort);
        System.out.println("Puerto Sockets: " + socketPort);
        System.out.println("Kafka Broker: " + kafkaBroker);
        System.out.println("-------------------------");

        // 3. Inicializar sistema consultando la BD
        DatabaseManager.inicializarYcargarEstaciones();

        // 4. Inicializar el Dashboard UI de terminal
        Dashboard.iniciar();
        
        // 5. Iniciar el Dashboard Web pasándole el puerto de Railway
        WebDashboard.iniciar(webPort);

        // 6. Inicializar el productor Kafka pasándole la ruta de Railway
        KafkaManager.inicializar(kafkaBroker);

        // Shutdown hook: cerrar Kafka limpiamente al apagar la JVM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Dashboard.log("Apagando servidor CENTRAL...");
            KafkaManager.cerrar();
        }));

        // Bucle de ejecución continuo para mantener el sistema 24/7 (Sockets)
        while (true) {
            try (ServerSocket skServidor = new ServerSocket(socketPort)) { // Usa la variable de entorno
                Dashboard.log("Servidor de SOCKETS iniciado. Escuchando el puerto " + socketPort);
                Dashboard.log("Esperando conexiones de estaciones (WS) y operarios (FO)...");

                while (true) {
                    try {
                        Socket skCliente = skServidor.accept();
                        System.out.println("Nueva conexión Sockets recibida: " + skCliente.getInetAddress().getHostAddress());
                        Thread t = new WM_Central_Thread(skCliente);
                        t.start();
                    } catch (IOException e) {
                        System.err.println("Error al aceptar la conexión de un cliente: " + e.getMessage());
                    } catch (Exception e) {
                        System.err.println("Excepción inesperada en el bucle de clientes: " + e);
                    }
                }
            } catch (BindException e) {
                System.err.println("El puerto " + socketPort + " ya está en uso. Reintentando en 5 segundos...");
                esperar(5000);
            } catch (IOException e) {
                System.err.println("Error grave en el ServerSocket: " + e.getMessage() + ". Reintentando en 5 s...");
                esperar(5000);
            }
        }
    }

    // Método auxiliar (mantén el que ya tenías)
    private static void esperar(int milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}