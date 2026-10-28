import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * WM_FO - Field Operator (Operario de Campo)
 * 
 * Aplicación de los operarios de mantenimiento para solicitar activaciones
 * de riego de forma manual o automatizada mediante fichero de servicios.
 * Se comunica con WM_Central a través de sockets siguiendo el protocolo
 * <STX><DATA><ETX><LRC> y mantiene conexión lógica con el gestor de colas
 * (Kafka).
 */
public class WM_FO {

    private final String brokerAddress;
    private final String idOperador;
    private final String ipCentral;
    private final int puertoCentral;

    private Socket socketCentral;
    private DataOutputStream salidaCentral;
    private DataInputStream entradaCentral;

    private boolean enEjecucion = true;
    private boolean servicioConcluido = false;
    private String ultimoMensajeConclusion = "";

    // Objeto de sincronización para esperar la conclusión de servicios en modo
    // automático
    private final Object lockServicio = new Object();

    // Caracteres de control ASCII del protocolo
    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char ACK = 0x06;
    private static final char NACK = 0x15;

    public WM_FO(String brokerAddress, String idOperador, String ipCentral, int puertoCentral) {
        this.brokerAddress = brokerAddress;
        this.idOperador = idOperador;
        this.ipCentral = ipCentral;
        this.puertoCentral = puertoCentral;
    }

    public void iniciar() {
        System.out.println("===============================================================");
        System.out.println("   WATER MANAGEMENT - FIELD OPERATOR (WM_FO)");
        System.out.println("   Operador ID: [" + idOperador + "] | Broker: [" + brokerAddress + "]");
        System.out.println("===============================================================");

        // 1. Conexión y autenticación con WM_Central
        if (!conectarYRegistrarEnCentral()) {
            System.err.println("[OPERADOR] Error: No se pudo registrar en WM_Central (" + ipCentral + ":"
                    + puertoCentral + "). Abortando...");
            return;
        }

        // 2. Hilo de recepción asíncrona de mensajes de WM_Central
        new Thread(this::bucleEscuchaCentral, "Hilo-Escucha-Central").start();

        // 3. Hilo para el gestor de eventos/colas (Kafka)
        new Thread(this::hiloConsumidorBroker, "Hilo-Broker").start();

        // 4. Menú interactivo por consola
        menuPrincipal();
    }

    /**
     * Conecta con WM_Central y envía la trama de registro del operario.
     */
    private boolean conectarYRegistrarEnCentral() {
        try {
            System.out.println("[OPERADOR] Conectando a WM_Central (" + ipCentral + ":" + puertoCentral + ")...");
            socketCentral = new Socket(ipCentral, puertoCentral);
            salidaCentral = new DataOutputStream(socketCentral.getOutputStream());
            entradaCentral = new DataInputStream(socketCentral.getInputStream());

            // Envío de la trama con formato <STX>REGISTRO_OPERARIO#<ID_OPERADOR><ETX><LRC>
            enviarTrama("REGISTRO_OPERARIO#" + idOperador);

            // Esperar confirmación ACK de la Central
            String respuesta = entradaCentral.readUTF();
            boolean aceptado = respuesta != null && respuesta.contains(String.valueOf(ACK));

            if (aceptado) {
                System.out.println("[OPERADOR] ¡Registro exitoso en WM_Central! (ACK recibido)");
                return true;
            } else {
                System.err.println("[OPERADOR] El registro fue rechazado por WM_Central (NACK).");
                return false;
            }
        } catch (IOException e) {
            System.err.println("[OPERADOR] Error al conectar con WM_Central: " + e.getMessage());
            return false;
        }
    }

    /**
     * Hilo en segundo plano que escucha constantemente los mensajes de WM_Central.
     * Permite recibir el progreso en tiempo real (PROCESANDO, INFO, AUTORIZADO,
     * DENEGADO, RESUMEN).
     */
    private void bucleEscuchaCentral() {
        while (enEjecucion) {
            try {
                String mensaje = entradaCentral.readUTF();
                if (mensaje == null) {
                    break;
                }

                // Log formateado en consola
                imprimirMensajeCentral(mensaje);

                // Comprobar si el mensaje marca el fin del servicio en curso
                if (esFinDeServicio(mensaje)) {
                    synchronized (lockServicio) {
                        servicioConcluido = true;
                        ultimoMensajeConclusion = mensaje;
                        lockServicio.notifyAll();
                    }
                }

            } catch (IOException e) {
                if (enEjecucion) {
                    System.err.println("\n[OPERADOR] Conexión cerrada con WM_Central: " + e.getMessage());
                    enEjecucion = false;
                }
                break;
            }
        }
    }

    /**
     * Comprueba si un mensaje recibido indica conclusión (éxito o fallo) de la
     * petición de riego.
     */
    private boolean esFinDeServicio(String msg) {
        if (msg == null)
            return false;
        return msg.startsWith("RESUMEN_FIN_RIEGO:") ||
                msg.startsWith("DENEGADO:") ||
                msg.startsWith("ERROR:") ||
                msg.startsWith("ABORTADO:") ||
                msg.equals(String.valueOf(NACK));
    }

    /**
     * Imprime los mensajes procedentes de Central con fecha y formato claro.
     */
    private void imprimirMensajeCentral(String msg) {
        String hora = new SimpleDateFormat("HH:mm:ss").format(new Date());

        if (msg.startsWith("AUTORIZADO:")) {
            System.out.println("\n>>> [" + hora + "] [CENTRAL - ÉXITO] " + msg);
        } else if (msg.startsWith("DENEGADO:") || msg.startsWith("ERROR:") || msg.startsWith("ABORTADO:")) {
            System.err.println("\n>>> [" + hora + "] [CENTRAL - AVISO/ERROR] " + msg);
        } else if (msg.startsWith("RESUMEN_FIN_RIEGO:")) {
            System.out.println("\n=======================================================");
            System.out.println(">>> [" + hora + "] [RESUMEN FINAL DE RIEGO]");
            System.out.println("    " + msg.replace("RESUMEN_FIN_RIEGO:", "").trim());
            System.out.println("=======================================================");
        } else if (msg.startsWith("LISTA_ESTACIONES:") || msg.startsWith("ESTACIONES:")) {
            System.out.println("\n--- ESTADO DE ESTACIONES EN CENTRAL ---" + msg.substring(msg.indexOf(':') + 1));
            System.out.println("---------------------------------------");
        } else {
            System.out.println("[" + hora + "] [CENTRAL] " + msg);
        }
        System.out.print("> ");
    }

    /**
     * Envía una trama empaquetada según el protocolo <STX><DATA><ETX><LRC>
     */
    private synchronized void enviarTrama(String datos) throws IOException {
        if (salidaCentral != null) {
            String mensaje = STX + datos + ETX;
            char lrc = 0;
            for (int i = 0; i < mensaje.length(); i++) {
                lrc ^= mensaje.charAt(i);
            }
            salidaCentral.writeUTF(mensaje + lrc);
            salidaCentral.flush();
        }
    }

    /**
     * Solicita la activación de una estación de riego a la Central.
     */
    public void solicitarRiego(String idWs) {
        try {
            System.out.println("\n[OPERADOR] Enviando solicitud de riego para WS [" + idWs + "]...");
            synchronized (lockServicio) {
                servicioConcluido = false;
                ultimoMensajeConclusion = "";
            }
            // Formato: ACTIVAR#<ID_WS>#<ID_OPERARIO>
            enviarTrama("ACTIVAR#" + idWs + "#" + idOperador);
        } catch (IOException e) {
            System.err.println("[OPERADOR] Error al enviar solicitud de activación: " + e.getMessage());
        }
    }

    /**
     * Lee un fichero con las estaciones a regar de forma secuencial.
     * Tras cada conclusión (éxito o fracaso), espera 4 segundos antes de la
     * siguiente petición.
     */
    public void procesarFicheroServicios(String rutaFichero) {
        File archivo = new File(rutaFichero);
        if (!archivo.exists() || !archivo.isFile()) {
            System.err.println("[OPERADOR] Error: El archivo '" + rutaFichero + "' no existe o no es válido.");
            return;
        }

        System.out.println("\n=======================================================");
        System.out.println("[MODO AUTOMÁTICO] Iniciando servicios desde: " + rutaFichero);
        System.out.println("=======================================================");

        List<String> estaciones = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (!linea.isEmpty() && !linea.startsWith("#")) {
                    // Admite líneas como "WS_01" o "WS_01, 10" o "WS_01#10"
                    String idWs = linea.split("[,#\\s]+")[0].trim();
                    estaciones.add(idWs);
                }
            }
        } catch (IOException e) {
            System.err.println("[OPERADOR] Error leyendo fichero: " + e.getMessage());
            return;
        }

        if (estaciones.isEmpty()) {
            System.out.println("[OPERADOR] El archivo no contiene solicitudes válidas.");
            return;
        }

        System.out.println("[MODO AUTOMÁTICO] Se han cargado " + estaciones.size() + " servicios para ejecutar.");

        for (int i = 0; i < estaciones.size(); i++) {
            if (!enEjecucion)
                break;

            String idWs = estaciones.get(i);
            System.out.println("\n-------------------------------------------------------");
            System.out.println("[SERVICIO " + (i + 1) + "/" + estaciones.size() + "] Activando estación: " + idWs);
            System.out.println("-------------------------------------------------------");

            solicitarRiego(idWs);

            // Esperar a que el servicio concluya (éxito o fallo)
            String resultadoServicio;
            synchronized (lockServicio) {
                long tiempoInicio = System.currentTimeMillis();
                long timeoutMaximo = 60000; // 60 segundos de seguridad por servicio
                while (!servicioConcluido && enEjecucion) {
                    try {
                        long transcurrido = System.currentTimeMillis() - tiempoInicio;
                        if (transcurrido >= timeoutMaximo) {
                            System.err.println("[OPERADOR] Timeout esperando conclusión del servicio para " + idWs);
                            break;
                        }
                        lockServicio.wait(timeoutMaximo - transcurrido);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                resultadoServicio = ultimoMensajeConclusion;
            }

            System.out.println("\n[MODO AUTOMÁTICO] Servicio en " + idWs + " concluido."
                    + (resultadoServicio.isEmpty() ? "" : " (Resultado: " + resultadoServicio + ")"));

            // Requisito de la práctica: Esperar 4 segundos antes de solicitar el siguiente
            // servicio
            if (i < estaciones.size() - 1 && enEjecucion) {
                System.out.println("[MODO AUTOMÁTICO] Esperando 4 segundos según especificación...");
                dormir(4000);
            }
        }

        System.out.println("\n=======================================================");
        System.out.println("[MODO AUTOMÁTICO] Todos los servicios del fichero han finalizado.");
        System.out.println("=======================================================");
    }

    /**
     * Solicita la lista de estaciones y sus estados a WM_Central.
     */
    public void consultarEstacionesDisponibles() {
        try {
            System.out.println("[OPERADOR] Consultando estaciones disponibles a WM_Central...");
            enviarTrama("LISTAR_WS#" + idOperador);
        } catch (IOException e) {
            System.err.println("[OPERADOR] Error al consultar estaciones: " + e.getMessage());
        }
    }

    /**
     * Obtiene el último mensaje de conclusión recibido de la Central.
     */
    public String getUltimoMensajeConclusion() {
        synchronized (lockServicio) {
            return ultimoMensajeConclusion;
        }
    }

    /**
     * Hilo preparado para el consumo de eventos y streaming de Kafka.
     */
    private void hiloConsumidorBroker() {
        System.out.println("[OPERADOR] Hilo consumidor del Broker (" + brokerAddress + ") a la espera de eventos...");
        // Aquí se conectará el KafkaConsumer para recibir telemetría/alertas cuando
        // esté activo Kafka
    }

    /**
     * Menú interactivo por consola para el usuario.
     */
    private void menuPrincipal() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (enEjecucion) {
                System.out.println("\n-------------------------------------------");
                System.out.println("   PANEL DE OPERARIO - ID: " + idOperador);
                System.out.println("-------------------------------------------");
                System.out.println("[1] Solicitar activación puntual de riego (Manual)");
                System.out.println("[2] Cargar y ejecutar servicios desde archivo");
                System.out.println("[3] Consultar estaciones de riego (Estados)");
                System.out.println("[4] Salir");
                System.out.print("Seleccione una opción: ");

                String opcion = scanner.nextLine().trim();

                switch (opcion) {
                    case "1":
                        System.out.print("Introduzca el ID de la estación de riego (ej. WS_01): ");
                        String idWs = scanner.nextLine().trim();
                        if (!idWs.isEmpty()) {
                            solicitarRiego(idWs);
                        } else {
                            System.err.println("ID de estación inválido.");
                        }
                        break;

                    case "2":
                        System.out.print("Introduzca la ruta o nombre del archivo (ej. servicios.txt): ");
                        String ruta = scanner.nextLine().trim();
                        if (!ruta.isEmpty()) {
                            procesarFicheroServicios(ruta);
                        } else {
                            System.err.println("Ruta de archivo no proporcionada.");
                        }
                        break;

                    case "3":
                        consultarEstacionesDisponibles();
                        break;

                    case "4":
                        System.out.println("[OPERADOR] Cerrando sesión y saliendo...");
                        enEjecucion = false;
                        cerrarConexion();
                        System.exit(0);
                        break;

                    default:
                        System.out.println("Opción no válida. Intente de nuevo.");
                        break;
                }
            }
        }
    }

    private void cerrarConexion() {
        try {
            if (socketCentral != null && !socketCentral.isClosed()) {
                socketCentral.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void dormir(int milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("=========================================================================");
            System.out.println(
                    "Uso obligatorio: java WM_FO <ip_broker:puerto> <id_operador> [ip_central] [puerto_central]");
            System.out.println("Ejemplos:");
            System.out.println("  java WM_FO localhost:9092 FO_01");
            System.out.println("  java WM_FO localhost:9092 FO_01 localhost 5000");
            System.out.println("  java WM_FO broker:29092 FO_02 192.168.1.50 5000");
            System.out.println("=========================================================================");
            System.exit(1);
        }

        String broker = args[0];
        String idOperador = args[1];

        // Valores por defecto para la Central si no se especifican
        String ipCentral = "localhost";
        int puertoCentral = 6000;

        if (args.length >= 4) {
            ipCentral = args[2];
            puertoCentral = Integer.parseInt(args[3]);
        } else if (args.length == 3) {
            if (args[2].contains(":")) {
                String[] partes = args[2].split(":");
                ipCentral = partes[0];
                puertoCentral = Integer.parseInt(partes[1]);
            } else {
                ipCentral = args[2];
            }
        }

        WM_FO fo = new WM_FO(broker, idOperador, ipCentral, puertoCentral);
        fo.iniciar();
    }
}
