import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;

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
    
    private static final Map<String, WSInfo> estaciones = new ConcurrentHashMap<>();
    private static final LinkedList<String> logs = new LinkedList<>();
    private static final int MAX_LOGS = 15;
    
    public static void iniciar() {
        log("Sistema CENTRAL iniciado. En modo Solo-Logs (La interfaz está en el Dashboard Web).");
    }

    public static synchronized void registrarEstacion(String id, String location) {
        estaciones.put(id, new WSInfo(id, location, Estado.DISPONIBLE));
        log("Alta de WS exitosa: [" + id + "] en [" + location + "]");
    }

    public static synchronized void cargarEstacion(String id, String location, Estado estado) {
        estaciones.put(id, new WSInfo(id, location, estado));
        WebDashboard.broadcast("ESTADO_ACTUALIZADO", obtenerEstadoJSON());
    }
    
    public static synchronized void actualizarEstado(String id, Estado estado) {
        if (estaciones.containsKey(id)) {
            estaciones.get(id).estado = estado;
            if (estado != Estado.REGANDO) {
                estaciones.get(id).caudal = "0.0";
                estaciones.get(id).operario = "-";
            }
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
        
        // Mantener histórico en memoria para la web
        logs.add(finalMsg);
        if (logs.size() > MAX_LOGS) {
            logs.removeFirst();
        }
        
        // Imprimir de manera limpia en la consola (sin borrar pantalla)
        if (mensaje.contains("¡ALERTA!") || mensaje.contains("FUGA")) {
            System.err.println(finalMsg); // Imprime en rojo en la mayoría de consolas
        } else {
            System.out.println(finalMsg);
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

    public static synchronized String obtenerListadoEstaciones() {
        if (estaciones.isEmpty()) {
            return "No hay estaciones registradas actualmente.";
        }
        StringBuilder sb = new StringBuilder();
        for (WSInfo ws : estaciones.values()) {
            sb.append("\n  - Estación: ").append(ws.id)
              .append(" | Ubicación: ").append(ws.location)
              .append(" | Estado: ").append(ws.estado.name());
            if (ws.estado == Estado.REGANDO) {
                sb.append(" [Caudal: ").append(ws.caudal).append(" L/min | Operario: ").append(ws.operario).append("]");
            }
        }
        return sb.toString();
    }
}
