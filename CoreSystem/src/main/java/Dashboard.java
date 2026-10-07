import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Dashboard {
    
    public enum Estado {
        DISPONIBLE, REGANDO, FUGA, FUERA_SERVICIO, DESCONECTADA
    }
    
    public static class WSInfo {
        public String id;
        public String location;
        public Estado estado;
        public String caudal = "0.0";
        public String volumen = "0.0";
        public String operario = "-";
        
        public WSInfo(String id, String location, Estado estado) {
            this.id = id;
            this.location = location;
            this.estado = estado;
        }
    }
    
    private static Map<String, WSInfo> estaciones = new ConcurrentHashMap<>();
    private static LinkedList<String> logs = new LinkedList<>();
    private static final int MAX_LOGS = 15;
    
    // Códigos ANSI para colores y estilos en terminal
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String GREEN_BG = "\u001B[42;30m"; // Fondo verde, texto negro
    private static final String RED_BG = "\u001B[41;37m"; // Fondo rojo, texto blanco
    private static final String YELLOW_BG = "\u001B[43;30m"; // Fondo naranja/amarillo
    private static final String GRAY_BG = "\u001B[100;37m"; // Fondo gris
    private static final String BOLD = "\u001B[1m";

    // Timer para refrescar la pantalla automáticamente y simular el parpadeo
    private static Timer refreshTimer;
    private static boolean blinkState = false;

    public static void iniciar() {
        if (refreshTimer == null) {
            refreshTimer = new Timer(true);
            refreshTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    blinkState = !blinkState;
                    printDashboard();
                }
            }, 0, 500); // Refresca cada 500ms (medio segundo)
        }
        log("Sistema CENTRAL iniciado. Dashboard activo.");
    }

    public static synchronized void registrarEstacion(String id, String location) {
        estaciones.put(id, new WSInfo(id, location, Estado.DISPONIBLE));
        log("Alta de WS exitosa: [" + id + "] en [" + location + "]");
        printDashboard();
    }
    
    public static synchronized void actualizarEstado(String id, Estado estado) {
        if (estaciones.containsKey(id)) {
            estaciones.get(id).estado = estado;
            if (estado != Estado.REGANDO) {
                estaciones.get(id).caudal = "0.0";
                estaciones.get(id).operario = "-";
            }
            printDashboard();
            WebDashboard.broadcast("ESTADO_ACTUALIZADO", obtenerEstadoJSON());
        } else {
            estaciones.put(id, new WSInfo(id, "Desconocida", estado));
            WebDashboard.broadcast("ESTADO_ACTUALIZADO", obtenerEstadoJSON());
        }
    }
    
    public static synchronized void actualizarTelemetria(String id, String caudal, String volumen) {
        if (estaciones.containsKey(id)) {
            WSInfo ws = estaciones.get(id);
            ws.caudal = caudal;
            ws.volumen = volumen;
            ws.estado = Estado.REGANDO;
            WebDashboard.broadcast("TELEMETRIA", "{\"id\":\"" + id + "\",\"caudal\":\"" + caudal + "\",\"volumen\":\"" + volumen + "\"}");
        }
    }
    
    public static synchronized void asignarOperario(String id, String operario) {
        if (estaciones.containsKey(id)) {
            estaciones.get(id).operario = operario;
            WebDashboard.broadcast("ESTADO_ACTUALIZADO", obtenerEstadoJSON());
        }
    }

    public static synchronized String getOperario(String id) {
        if (estaciones.containsKey(id)) {
            return estaciones.get(id).operario;
        }
        return "-";
    }

    public static synchronized void log(String mensaje) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String finalMsg = "[" + time + "] " + mensaje;
        logs.add(finalMsg);
        if (logs.size() > MAX_LOGS) {
            logs.removeFirst();
        }
        
        // Convertimos las comillas dobles para que no rompan el JSON
        String msgLimpio = finalMsg.replace("\"", "\\\"");
        WebDashboard.broadcast("NUEVO_LOG", "{\"mensaje\":\"" + msgLimpio + "\"}");
    }

    public static synchronized String obtenerEstadoJSON() {
        StringBuilder json = new StringBuilder();
        json.append("[");
        boolean primero = true;
        for (WSInfo ws : estaciones.values()) {
            if (!primero) json.append(",");
            json.append(String.format("{\"id\":\"%s\",\"location\":\"%s\",\"estado\":\"%s\",\"caudal\":\"%s\",\"volumen\":\"%s\",\"operario\":\"%s\"}",
                    ws.id, ws.location, ws.estado.name(), ws.caudal, ws.volumen, ws.operario));
            primero = false;
        }
        json.append("]");
        return json.toString();
    }
    
    private static synchronized void printDashboard() {
        // Códigos ANSI para limpiar pantalla y posicionar cursor al inicio
        System.out.print("\033[H\033[2J");  
        System.out.flush();
        
        System.out.println(BOLD + "=================================================================================================" + RESET);
        System.out.println(BOLD + "                        PANEL DE CONTROL CENTRAL - WATER MANAGEMENT SYSTEM                       " + RESET);
        System.out.println(BOLD + "=================================================================================================" + RESET);
        System.out.println();
        
        // Subtarea 2.1.1: Vista general de las WS
        System.out.printf(BOLD + "%-10s | %-20s | %-22s | %-15s | %-10s%n" + RESET, "ID WS", "UBICACION", "ESTADO", "CAUDAL/VOLUMEN", "OPERARIO");
        System.out.println("-------------------------------------------------------------------------------------------------");
        
        if (estaciones.isEmpty()) {
            System.out.println("  No hay estaciones registradas en el sistema.");
        } else {
            for (WSInfo ws : estaciones.values()) {
                String estadoStr = "";
                String datos = "-";
                
                // Subtarea 2.2: Visualización de los estados con fondos de color
                switch (ws.estado) {
                    case DISPONIBLE:
                        estadoStr = GREEN_BG + " DISPONIBLE " + RESET;
                        break;
                    case REGANDO:
                        // Subtarea 2.2.2: Fondo VERDE PARPADEANDO. (Alternamos texto con fondo/sin fondo según blinkState)
                        if (blinkState) {
                            estadoStr = GREEN_BG + " REGANDO... " + RESET;
                        } else {
                            estadoStr = GREEN + " REGANDO... " + RESET;
                        }
                        datos = ws.caudal + "L/m, " + ws.volumen + "L";
                        break;
                    case FUGA:
                        estadoStr = RED_BG + " FUGA! " + RESET;
                        break;
                    case FUERA_SERVICIO:
                        estadoStr = YELLOW_BG + " FUERA DE SERVICIO " + RESET;
                        break;
                    case DESCONECTADA:
                        estadoStr = GRAY_BG + " DESCONECTADA " + RESET;
                        break;
                }
                
                System.out.printf("%-10s | %-20s | %-31s | %-15s | %-10s%n", ws.id, ws.location, estadoStr, datos, ws.operario);
            }
        }
        
        System.out.println();
        System.out.println(BOLD + "--- REGISTRO DE EVENTOS (LOGS) Y NOTIFICACIONES -------------------------------------------------" + RESET);
        
        // Subtarea 2.1.2: Área de notificaciones
        if (logs.isEmpty()) {
            System.out.println("  Sin eventos recientes.");
        } else {
            for (String l : logs) {
                // Pequeño coloreado para las alertas de fuga
                if (l.contains("¡ALERTA!") || l.contains("FUGA")) {
                    System.out.println(RED_BG + l + RESET);
                } else {
                    System.out.println(l);
                }
            }
        }
        System.out.println(BOLD + "=================================================================================================" + RESET);
    }
}
