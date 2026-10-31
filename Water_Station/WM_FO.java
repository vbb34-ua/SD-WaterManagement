import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class WM_FO {

    private final String brokerAddress;
    private final String idOperador;
    private final String ipCentral;
    private final int puertoCentral;

    private Socket socketCentral;
    private DataOutputStream salidaCentral;
    private DataInputStream entradaCentral;

    private volatile boolean enEjecucion = true;
    private volatile boolean registradoEnCentral = false;

    private boolean servicioConcluido = false;
    private String ultimoMensajeConclusion = "";
    private final Object lockServicio = new Object();

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

        // Estrategia 1: Hilo de conexión y reconexión continua a la Central
        new Thread(this::gestorConexionCentral, "Hilo-Conexion-Central").start();

        new Thread(this::hiloConsumidorBroker, "Hilo-Broker").start();

        menuPrincipal();
    }

    // Estrategia 1: Reconexión periódica y re-autenticación automática
    private void gestorConexionCentral() {
        int esperaMs = 2000;
        int maxEsperaMs = 30000;

        while (enEjecucion) {
            if (!registradoEnCentral) {
                boolean exito = conectarYRegistrarEnCentral();
                if (exito) {
                    registradoEnCentral = true;
                    esperaMs = 2000;
                    bucleEscuchaCentral(); // Escuchar hasta desconexión
                } else {
                    System.err.println("[OPERADOR] Reintentando conexión con Central en " + (esperaMs / 1000) + "s...");
                    dormir(esperaMs);
                    esperaMs = Math.min(esperaMs * 2, maxEsperaMs);
                }
            } else {
                dormir(1000);
            }
        }
    }

    private boolean conectarYRegistrarEnCentral() {
        try {
            System.out.println("[OPERADOR] Conectando a WM_Central (" + ipCentral + ":" + puertoCentral + ")...");
            socketCentral = new Socket(ipCentral, puertoCentral);
            salidaCentral = new DataOutputStream(socketCentral.getOutputStream());
            entradaCentral = new DataInputStream(socketCentral.getInputStream());

            enviarTrama("REGISTRO_OPERARIO#" + idOperador);

            String respuesta = entradaCentral.readUTF();
            boolean aceptado = respuesta != null && respuesta.contains(String.valueOf(ACK));

            if (aceptado) {
                System.out.println("[OPERADOR] ¡Registro exitoso en WM_Central! (ACK recibido)");
                return true;
            } else {
                System.err.println("[OPERADOR] Registro rechazado por Central (NACK).");
                return false;
            }
        } catch (IOException e) {
            System.err.println("[OPERADOR] Fallo al conectar con WM_Central: " + e.getMessage());
            return false;
        }
    }

    private void bucleEscuchaCentral() {
        while (enEjecucion && registradoEnCentral) {
            try {
                String mensaje = entradaCentral.readUTF();
                if (mensaje == null) break;

                imprimirMensajeCentral(mensaje);

                if (esFinDeServicio(mensaje)) {
                    synchronized (lockServicio) {
                        servicioConcluido = true;
                        ultimoMensajeConclusion = mensaje;
                        lockServicio.notifyAll();
                    }
                }
            } catch (IOException e) {
                System.err.println("\n[OPERADOR] Conexión perdida con WM_Central.");
                registradoEnCentral = false;
                break;
            }
        }
    }

    private boolean esFinDeServicio(String msg) {
        if (msg == null) return false;
        return msg.startsWith("RESUMEN_FIN_RIEGO:") ||
                msg.startsWith("DENEGADO:") ||
                msg.startsWith("ERROR:") ||
                msg.startsWith("ABORTADO:") ||
                msg.equals(String.valueOf(NACK));
    }

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

    private synchronized void enviarTrama(String datos) throws IOException {
        if (salidaCentral != null && socketCentral != null && !socketCentral.isClosed()) {
            String mensaje = STX + datos + ETX;
            char lrc = 0;
            for (int i = 0; i < mensaje.length(); i++) {
                lrc ^= mensaje.charAt(i);
            }
            salidaCentral.writeUTF(mensaje + lrc);
            salidaCentral.flush();
        } else {
            throw new IOException("Socket no disponible");
        }
    }

    public void solicitarRiego(String idWs) {
        try {
            System.out.println("\n[OPERADOR] Enviando solicitud de riego para WS [" + idWs + "]...");
            synchronized (lockServicio) {
                servicioConcluido = false;
                ultimoMensajeConclusion = "";
            }
            enviarTrama("ACTIVAR#" + idWs + "#" + idOperador);
        } catch (IOException e) {
            System.err.println("[OPERADOR] Error al solicitar activación (Sin conexión con Central): " + e.getMessage());
        }
    }

    public void procesarFicheroServicios(String rutaFichero) {
        File archivo = new File(rutaFichero);
        if (!archivo.exists() || !archivo.isFile()) {
            System.err.println("[OPERADOR] Error: El archivo '" + rutaFichero + "' no existe.");
            return;
        }

        System.out.println("\n[MODO AUTOMÁTICO] Cargando servicios desde: " + rutaFichero);
        List<String> estaciones = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (!linea.isEmpty() && !linea.startsWith("#")) {
                    String idWs = linea.split("[,#\\s]+")[0].trim();
                    estaciones.add(idWs);
                }
            }
        } catch (IOException e) {
            System.err.println("[OPERADOR] Error leyendo fichero: " + e.getMessage());
            return;
        }

        for (int i = 0; i < estaciones.size(); i++) {
            if (!enEjecucion) break;

            String idWs = estaciones.get(i);
            System.out.println("\n[SERVICIO " + (i + 1) + "/" + estaciones.size() + "] Activando estación: " + idWs);

            solicitarRiego(idWs);

            String resultadoServicio;
            synchronized (lockServicio) {
                long tiempoInicio = System.currentTimeMillis();
                long timeoutMaximo = 60000;
                while (!servicioConcluido && enEjecucion) {
                    try {
                        long transcurrido = System.currentTimeMillis() - tiempoInicio;
                        if (transcurrido >= timeoutMaximo) {
                            System.err.println("[OPERADOR] Timeout esperando conclusión de servicio " + idWs);
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

            System.out.println("[MODO AUTOMÁTICO] Servicio en " + idWs + " concluido: " + resultadoServicio);

            if (i < estaciones.size() - 1 && enEjecucion) {
                dormir(4000);
            }
        }
    }

    public void consultarEstacionesDisponibles() {
        try {
            enviarTrama("LISTAR_WS#" + idOperador);
        } catch (IOException e) {
            System.err.println("[OPERADOR] Error al consultar estaciones: " + e.getMessage());
        }
    }

    private void hiloConsumidorBroker() {
        System.out.println("[OPERADOR] Hilo consumidor del Broker (" + brokerAddress + ") iniciado.");
    }

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
                        System.out.print("ID de estación (ej. WS_01): ");
                        String idWs = scanner.nextLine().trim();
                        if (!idWs.isEmpty()) solicitarRiego(idWs);
                        break;

                    case "2":
                        System.out.print("Ruta de archivo: ");
                        String ruta = scanner.nextLine().trim();
                        if (!ruta.isEmpty()) procesarFicheroServicios(ruta);
                        break;

                    case "3":
                        consultarEstacionesDisponibles();
                        break;

                    case "4":
                        enEjecucion = false;
                        cerrarConexion();
                        System.exit(0);
                        break;

                    default:
                        System.out.println("Opción no válida.");
                        break;
                }
            }
        }
    }

    private void cerrarConexion() {
        try {
            if (socketCentral != null && !socketCentral.isClosed()) socketCentral.close();
        } catch (IOException ignored) {}
    }

    private void dormir(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java WM_FO <ip_broker:puerto> <id_operador> [ip_central] [puerto_central]");
            System.exit(1);
        }

        String broker = args[0];
        String idOperador = args[1];
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