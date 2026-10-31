import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class WM_WS_M {

    private final int puertoLocalEngine;
    private final String ipCentral;
    private final int puertoCentral;
    private final String idWS;

    private Socket socketCentral;
    private DataOutputStream salidaCentral;
    private DataInputStream entradaCentral;

    private Socket socketEngine;
    private DataOutputStream salidaEngine;
    private DataInputStream entradaEngine;

    private volatile boolean enEjecucion = true;
    private volatile boolean averiaNotificada = false;

    public WM_WS_M(int puertoLocalEngine, String ipCentral, int puertoCentral, String idWS) {
        this.puertoLocalEngine = puertoLocalEngine;
        this.ipCentral = ipCentral;
        this.puertoCentral = puertoCentral;
        this.idWS = idWS;
    }

    public void iniciar() {
        System.out.println("=== ARRANCANDO MONITOR (WM_WS_M) - WS ID: " + idWS + " ===");

        // Estrategia 4: Mantener ServerSocket del Engine abierto en segundo plano
        new Thread(this::bucleAceptarEngine, "Hilo-ServerSocket-Engine").start();

        // Estrategia 1: Conectar y reconectarse dinámicamente a la Central
        new Thread(this::bucleConexionCentral, "Hilo-Conexion-Central").start();

        // Estrategia 2: Hilo de latido/ping hacia Central para mantener socket activo
        new Thread(this::buclePingCentral, "Hilo-Ping-Central").start();

        // Bucle principal de supervisión de salud del Engine
        bucleSupervisionSalud();
    }

    // Estrategia 1: Reconexión con Backoff Exponencial a Central
    private void bucleConexionCentral() {
        int esperaMs = 2000;
        int maxEsperaMs = 30000;

        while (enEjecucion) {
            try {
                System.out.println("[MONITOR] Conectando a WM_Central (" + ipCentral + ":" + puertoCentral + ")...");
                socketCentral = new Socket(ipCentral, puertoCentral);
                salidaCentral = new DataOutputStream(socketCentral.getOutputStream());
                entradaCentral = new DataInputStream(socketCentral.getInputStream());

                // Reenviar trama de registro
                enviarACentral("REGISTRO#" + idWS + "#Ubicacion_N/A");

                String respuesta = entradaCentral.readUTF();
                char ACK = 0x06;
                if (respuesta != null && respuesta.equals(String.valueOf(ACK))) {
                    System.out.println("[MONITOR] Autenticado correctamente en WM_Central.");
                    esperaMs = 2000; // Resetear backoff
                    
                    // Escuchar a Central mientras la conexión siga viva
                    iniciarEscuchaCentral();
                } else {
                    System.err.println("[MONITOR] Registro rechazado por Central. Reintentando...");
                }
            } catch (IOException e) {
                System.err.println("[MONITOR] Error/Pérdida de enlace con WM_Central: " + e.getMessage());
            }

            // Espera progresiva antes del siguiente reintento
            dormir(esperaMs);
            esperaMs = Math.min(esperaMs * 2, maxEsperaMs);
        }
    }

    // Estrategia 4: ServerSocket continuo que no se cierra con .close()
    private void bucleAceptarEngine() {
        try (ServerSocket serverSocket = new ServerSocket(puertoLocalEngine)) {
            System.out.println("[MONITOR] ServerSocket local abierto en puerto " + puertoLocalEngine + " (Esperando a WM_WS_E)...");
            while (enEjecucion) {
                try {
                    Socket nuevoEngine = serverSocket.accept();
                    nuevoEngine.setSoTimeout(2000);

                    synchronized (this) {
                        if (socketEngine != null && !socketEngine.isClosed()) {
                            try { socketEngine.close(); } catch (IOException ignored) {}
                        }
                        socketEngine = nuevoEngine;
                        salidaEngine = new DataOutputStream(socketEngine.getOutputStream());
                        entradaEngine = new DataInputStream(socketEngine.getInputStream());
                    }
                    System.out.println("[MONITOR] Engine (WM_WS_E) conectado/reconectado exitosamente.");
                } catch (IOException e) {
                    if (enEjecucion) System.err.println("[MONITOR] Error aceptando conexión de Engine: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[MONITOR] Error fatal en ServerSocket local: " + e.getMessage());
        }
    }

    private void bucleSupervisionSalud() {
        while (enEjecucion) {
            dormir(1000);

            synchronized (this) {
                if (salidaEngine == null || entradaEngine == null || socketEngine == null || socketEngine.isClosed()) {
                    reportarAveriaCentral("Engine no conectado");
                    continue;
                }

                try {
                    salidaEngine.writeUTF("HEALTH_CHECK");
                    salidaEngine.flush();

                    String respuesta = entradaEngine.readUTF();

                    if ("OK".equalsIgnoreCase(respuesta)) {
                        if (averiaNotificada) {
                            System.out.println("[MONITOR] Engine recuperado. Reportando OK a Central.");
                            notificarIncidenciaCentral("RECUPERADO");
                            averiaNotificada = false;
                        }
                    } else {
                        reportarAveriaCentral("Respuesta KO recibida desde Engine");
                    }
                } catch (SocketTimeoutException e) {
                    reportarAveriaCentral("Timeout: Engine no responde");
                } catch (IOException e) {
                    reportarAveriaCentral("Pérdida de enlace con Engine");
                }
            }
        }
    }

    // Estrategia 2: Ping periódico cuando no hay tráfico
    private void buclePingCentral() {
        while (enEjecucion) {
            dormir(5000); // Enviar ping cada 5s
            try {
                if (socketCentral != null && !socketCentral.isClosed() && salidaCentral != null) {
                    enviarACentral("PING#" + idWS);
                }
            } catch (IOException ignored) {}
        }
    }

    private void iniciarEscuchaCentral() {
        try {
            while (enEjecucion && socketCentral != null && !socketCentral.isClosed()) {
                String mensaje = entradaCentral.readUTF();
                if (mensaje != null && mensaje.length() > 3) {
                    int etxIndex = mensaje.indexOf(0x03);
                    if (etxIndex > 1) {
                        String datos = mensaje.substring(1, etxIndex);
                        String[] partes = datos.split("#");

                        if ("SOLICITUD_RIEGO".equals(partes[0])) {
                            String idOperario = partes[1];
                            System.out.println("[MONITOR] Solicitud de riego del operario: " + idOperario);
                            enviarACentral("RIEGO_AUTORIZADO#" + idOperario);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[MONITOR] Bucle de escucha con Central interrumpido.");
        }
    }

    private void reportarAveriaCentral(String motivo) {
        if (!averiaNotificada) {
            System.err.println("[MONITOR] ALERTA DE AVERÍA (" + motivo + "). Notificando FUGA...");
            notificarIncidenciaCentral("FUGA");
            averiaNotificada = true;
        }
    }

    private synchronized void notificarIncidenciaCentral(String estado) {
        try {
            if ("FUGA".equals(estado)) {
                enviarACentral("AVERIA#" + idWS);
            } else if ("RECUPERADO".equals(estado)) {
                enviarACentral("RESUELTA#" + idWS);
            }
        } catch (IOException e) {
            System.err.println("[MONITOR] Fallo al comunicar incidencia a WM_Central: " + e.getMessage());
        }
    }

    private synchronized void enviarACentral(String datos) throws IOException {
        if (salidaCentral != null && socketCentral != null && !socketCentral.isClosed()) {
            char STX = 0x02;
            char ETX = 0x03;
            String mensaje = STX + datos + ETX;
            char lrc = 0;
            for (int i = 0; i < mensaje.length(); i++) {
                lrc ^= mensaje.charAt(i);
            }
            salidaCentral.writeUTF(mensaje + lrc);
            salidaCentral.flush();
        }
    }

    private void dormir(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Uso: java WM_WS_M <puerto_para_Engine> <IP_Central> <puerto_Central> <id_WS>");
            System.exit(1);
        }

        int puertoLocal = Integer.parseInt(args[0]);
        String ipCentral = args[1];
        int puertoCentral = Integer.parseInt(args[2]);
        String idWS = args[3];

        WM_WS_M monitor = new WM_WS_M(puertoLocal, ipCentral, puertoCentral, idWS);
        monitor.iniciar();
    }
}