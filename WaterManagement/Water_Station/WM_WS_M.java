import java.io.*;
import java.net.*;

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

    private boolean enEjecucion = true;
    private boolean averiaNotificada = false;

    public WM_WS_M(int puertoLocalEngine, String ipCentral, int puertoCentral, String idWS) {
        this.puertoLocalEngine = puertoLocalEngine;
        this.ipCentral = ipCentral;
        this.puertoCentral = puertoCentral;
        this.idWS = idWS;
    }

    public void iniciar() {
        System.out.println("=== ARRANCANDO MONITOR (WM_WS_M) - WS ID: " + idWS + " ===");

        // Conexión y autenticación con WM_Central
        if (!autenticarEnCentral()) {
            System.err.println("[MONITOR] Autenticación fallida o denegada por WM_Central. Abortando...");
            return;
        }

        // Esperar conexión de su Engine local (WM_WS_E)
        if (!esperarConexionEngine()) {
            System.err.println("[MONITOR] No se pudo establecer conexión con WM_WS_E. Abortando...");
            return;
        }

        // NUEVO: Arrancamos el hilo que escucha órdenes de la Central en segundo plano
        iniciarEscuchaCentral();

        // Bucle de supervisión de salud (Health Check cada 1 segundo)
        bucleSupervisionSalud();
    }

    private boolean autenticarEnCentral() {
        try {
            System.out.println("[MONITOR] Conectando a WM_Central (" + ipCentral + ":" + puertoCentral + ")...");
            socketCentral = new Socket(ipCentral, puertoCentral);
            salidaCentral = new DataOutputStream(socketCentral.getOutputStream());
            entradaCentral = new DataInputStream(socketCentral.getInputStream());

            // Trama de autenticación / registro
            enviarACentral("REGISTRO#" + idWS + "#Ubicacion_N/A");

            String respuesta = entradaCentral.readUTF();
            char ACK = 0x06;
            boolean aceptado = respuesta != null && respuesta.equals(String.valueOf(ACK));
            System.out
                    .println("[MONITOR] Respuesta de WM_Central: " + (aceptado ? "ACK (Aceptado)" : "NACK/Rechazado"));

            return aceptado;
        } catch (IOException e) {
            System.err.println("[MONITOR] Error conectando con WM_Central: " + e.getMessage());
            return false;
        }
    }

    private boolean esperarConexionEngine() {
        try {
            ServerSocket serverSocket = new ServerSocket(puertoLocalEngine);
            System.out.println(
                    "[MONITOR] Servidor local abierto en puerto " + puertoLocalEngine + ". Esperando a WM_WS_E...");
            socketEngine = serverSocket.accept();
            // Timeout de lectura de 2 segundos para detectar caída del Engine
            socketEngine.setSoTimeout(2000);

            salidaEngine = new DataOutputStream(socketEngine.getOutputStream());
            entradaEngine = new DataInputStream(socketEngine.getInputStream());
            System.out.println("[MONITOR] Engine (WM_WS_E) conectado correctamente.");
            serverSocket.close(); // No esperamos más conexiones
            return true;
        } catch (IOException e) {
            System.err.println("[MONITOR] Error esperando conexión de WM_WS_E: " + e.getMessage());
            return false;
        }
    }

    private void bucleSupervisionSalud() {
        while (enEjecucion) {
            try {
                Thread.sleep(1000); // Frecuencia de 1 segundo

                // Envío de comprobación de salud al Engine
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
                    // Respuesta KO simulada
                    reportarAveriaCentral("Respuesta KO recibida desde Engine");
                }

            } catch (SocketTimeoutException e) {
                reportarAveriaCentral("Timeout: Engine no responde");
            } catch (IOException e) {
                reportarAveriaCentral("Pérdida de enlace con Engine");
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void iniciarEscuchaCentral() {
        new Thread(() -> {
            while (enEjecucion) {
                try {
                    String mensaje = entradaCentral.readUTF();
                    // Quitamos los caracteres de control STX, ETX y LRC para leer el texto
                    if (mensaje != null && mensaje.length() > 3) {
                        String datos = mensaje.substring(1, mensaje.indexOf(0x03));
                        String[] partes = datos.split("#");

                        if ("SOLICITUD_RIEGO".equals(partes[0])) {
                            String idOperario = partes[1];
                            System.out.println("[MONITOR] Solicitud de riego recibida del operario: " + idOperario);

                            // Aquí el Monitor debería pedirle permiso al Engine.
                            // Como simulación rápida, simplemente autorizamos directamente:
                            System.out.println("[MONITOR] Riego Autorizado. Informando a Central...");
                            enviarACentral("RIEGO_AUTORIZADO#" + idOperario);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[MONITOR] Conexión con Central perdida.");
                    break;
                }
            }
        }).start();
    }

    private void reportarAveriaCentral(String motivo) {
        if (!averiaNotificada) {
            System.err.println("[MONITOR] ALERTA DE AVERÍA (" + motivo + "). Notificando FUGA a WM_Central...");
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

    private void enviarACentral(String datos) throws IOException {
        if (salidaCentral != null) {
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