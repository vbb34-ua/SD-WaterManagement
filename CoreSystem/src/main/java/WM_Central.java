import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;

public class WM_Central {

    public static void main(String[] args) {
        // 1. LEER LAS VARIABLES DE ENTORNO DE RAILWAY (Prioridad nube)
        String envPortWeb = System.getenv("PORT");
        String envPortSocket = System.getenv("SOCKET_PORT");
        String envKafkaBroker = System.getenv("KAFKA_BROKER");

        // 2. ASIGNAR PUERTOS (Si no hay variables de Railway, usa por defecto los de local)
        int webPort = (envPortWeb != null) ? Integer.parseInt(envPortWeb) : 8080;
        int socketPort = (envPortSocket != null) ? Integer.parseInt(envPortSocket) : (args.length > 0 ? Integer.parseInt(args[0]) : 6000);
        String kafkaBroker = (envKafkaBroker != null) ? envKafkaBroker : (args.length > 1 ? args[1] : "localhost:9092");

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