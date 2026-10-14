import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WebDashboard {

    private static HttpServer server;
    // Lista de clientes conectados a la página web esperando eventos en tiempo real
    private static final List<HttpExchange> clientesSSE = new CopyOnWriteArrayList<>();

    public static void iniciar(int puerto) {
        try {
            server = HttpServer.create(new InetSocketAddress(puerto), 0);
            
            // 1. Endpoint para servir la página web HTML
            server.createContext("/", exchange -> {
                File file = new File("web/index.html");
                if (file.exists()) {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } else {
                    String response = "Error: No se encuentra web/index.html";
                    exchange.sendResponseHeaders(404, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
            });

            // 2. Endpoint SSE (Server-Sent Events) para actualizaciones en tiempo real
            server.createContext("/events", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                exchange.getResponseHeaders().add("Connection", "keep-alive");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, 0); // 0 = response de longitud arbitraria
                
                clientesSSE.add(exchange);
                System.out.println("[WEB] Nuevo navegador conectado al Dashboard.");
                
                // Enviamos un evento inicial para forzar que el Dashboard envíe todo su estado
                broadcast("ESTADO_INICIAL", Dashboard.obtenerEstadoJSON());
            });

            // 3. Endpoint API para acciones manuales (Subtarea 6)
            server.createContext("/api/action", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String response = "OK";
                
                if (query != null) {
                    String[] params = query.split("&");
                    String ws = null;
                    String action = null;
                    
                    for (String param : params) {
                        String[] pair = param.split("=");
                        if (pair.length == 2) {
                            if (pair[0].equals("ws")) ws = pair[1];
                            if (pair[0].equals("action")) action = pair[1];
                        }
                    }
                    
                    if (ws != null && action != null) {
                        WM_Central_Thread hiloWS = WM_Central_Thread.conexiones.get(ws);
                        
                        switch (action) {
                            case "START" -> {
                                // 6.1: Iniciar riego desde Central
                                String estadoWS = DatabaseManager.obtenerEstado(ws);
                                if ("DISPONIBLE".equals(estadoWS)) {
                                    Dashboard.log("CENTRAL solicita iniciar riego en [" + ws + "].");
                                    if (hiloWS != null) {
                                        hiloWS.enviarTrama("SOLICITUD_RIEGO#CENTRAL");
                                    }
                                } else {
                                    Dashboard.log("CENTRAL intentó regar en [" + ws + "] pero está en estado " + estadoWS);
                                    response = "DENEGADO";
                                }
                            }
                            case "BLOCK" -> {
                                // 6.2: Bloquear WS y detener riegos
                                Dashboard.log("CENTRAL bloquea la estación [" + ws + "].");
                                DatabaseManager.actualizarEstado(ws, "FUERA_SERVICIO");
                                Dashboard.actualizarEstado(ws, Dashboard.Estado.FUERA_SERVICIO);
                                if (hiloWS != null) {
                                    hiloWS.enviarTrama("ABORTAR_RIEGO#CENTRAL"); // La WS debe abortar
                                }
                            }
                            case "ACTIVATE" -> {
                                // 6.3: Activar WS (devolver a Disponible)
                                Dashboard.log("CENTRAL reactiva la estación [" + ws + "].");
                                DatabaseManager.actualizarEstado(ws, "DISPONIBLE");
                                Dashboard.actualizarEstado(ws, Dashboard.Estado.DISPONIBLE);
                            }
                        }
                    }
                }
                
                // Respondemos al navegador
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });

            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            System.out.println("[WEB] Dashboard moderno disponible en: http://localhost:" + puerto);

        } catch (IOException e) {
            System.err.println("[WEB] Error arrancando el servidor HTTP: " + e.getMessage());
        }
    }

    // Método que el antiguo Dashboard llamará para notificar a todos los navegadores
    public static void broadcast(String tipoEvento, String datosJSON) {
        String mensaje = "event: " + tipoEvento + "\ndata: " + datosJSON + "\n\n";
        
        List<HttpExchange> caidos = new ArrayList<>();
        
        for (HttpExchange cliente : clientesSSE) {
            try {
                OutputStream os = cliente.getResponseBody();
                os.write(mensaje.getBytes("UTF-8"));
                os.flush();
            } catch (IOException e) {
                // Si falla (ej. cerraron la pestaña), lo marcamos para borrarlo
                caidos.add(cliente);
            }
        }
        clientesSSE.removeAll(caidos);
    }
}
