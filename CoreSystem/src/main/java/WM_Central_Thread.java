import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class WM_Central_Thread extends Thread {

    private Socket skCliente;
    public static java.util.concurrent.ConcurrentHashMap<String, WM_Central_Thread> conexiones = new java.util.concurrent.ConcurrentHashMap<>();
    private String idConexion = null;

    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char ACK = 0x06;
    private static final char NACK = 0x15;

    public WM_Central_Thread(Socket p_cliente) {
        this.skCliente = p_cliente;
    }
    
    public String leeSocket(Socket p_sk) throws SocketTimeoutException, EOFException, Exception {
        InputStream aux = p_sk.getInputStream();
        DataInputStream flujo = new DataInputStream(aux);
        return flujo.readUTF();
    }

    public void escribeSocket(Socket p_sk, String p_Datos) {
        try {
            OutputStream aux = p_sk.getOutputStream();
            DataOutputStream flujo = new DataOutputStream(aux);
            flujo.writeUTF(p_Datos);      
        } catch (Exception e) {
            System.out.println("Error escribiendo en socket: " + e.getMessage());
        }
    }

    public void enviarTrama(String datos) {
        String mensaje = STX + datos + ETX;
        char lrc = calcularLRC(mensaje);
        escribeSocket(skCliente, mensaje + lrc);
    }
    
    public void enviarTextoOperario(String texto) {
        escribeSocket(skCliente, texto);
    }
    
    private char calcularLRC(String mensaje) {
        char lrc = 0;
        for (int i = 0; i < mensaje.length(); i++) {
            lrc ^= mensaje.charAt(i);
        }
        return lrc;
    }

    private String procesarTrama(String trama) {
        if (trama == null || trama.length() < 3) return null;
        if (trama.charAt(0) != STX) return null;

        int etxIndex = trama.indexOf(ETX);
        if (etxIndex == -1 || etxIndex == trama.length() - 1) return null;

        char lrcRecibido = trama.charAt(trama.length() - 1);
        String mensajeParaLRC = trama.substring(0, trama.length() - 1);
        char lrcCalculado = calcularLRC(mensajeParaLRC);
        
        if (lrcRecibido != lrcCalculado) {
            System.out.println("SRV: Error de validación LRC.");
            return null;
        }

        return trama.substring(1, etxIndex);
    }

    private String ejecutarOperacion(String data) {
        String[] partes = data.split("#");
        String operacion = partes[0];

        switch (operacion) {
            case "PING":
                return String.valueOf(ACK);

            case "REGISTRO":
                if (partes.length >= 3) {
                    String id = partes[1];
                    String location = partes[2];
                    boolean exito = DatabaseManager.registrarEstacion(id, location);
                    if (exito) {
                        this.idConexion = id;
                        conexiones.put(id, this);
                        Dashboard.registrarEstacion(id, location);
                        return String.valueOf(ACK);
                    }
                }
                break;

            case "REGISTRO_OPERARIO":
                if (partes.length >= 2) {
                    String idOp = partes[1];
                    this.idConexion = idOp;
                    conexiones.put(idOp, this);
                    return String.valueOf(ACK);
                }
                break;

            case "ACTIVAR":
                if (partes.length >= 3) {
                    String idWs = partes[1];
                    String idOperario = partes[2];
                    
                    if (this.idConexion == null) {
                        this.idConexion = idOperario;
                        conexiones.put(idOperario, this);
                    }
                    
                    Dashboard.log("Petición de activación para WS [" + idWs + "] por [" + idOperario + "].");
                    enviarTextoOperario("PROCESANDO: Solicitando estado de " + idWs + "...");
                    
                    String estadoWS = DatabaseManager.obtenerEstado(idWs);
                    if ("DISPONIBLE".equals(estadoWS)) {
                        WM_Central_Thread hiloWS = conexiones.get(idWs);
                        if (hiloWS != null) {
                            hiloWS.enviarTrama("SOLICITUD_RIEGO#" + idOperario);
                        } else {
                            enviarTextoOperario("ERROR: La estación " + idWs + " está desconectada del socket.");
                        }
                    } else {
                        enviarTextoOperario("DENEGADO: La estación " + idWs + " se encuentra en estado " + estadoWS);
                    }
                    return null;
                }
                break;

            case "RIEGO_AUTORIZADO":
                if (partes.length >= 2) {
                    String idOperario = partes[1];
                    String idWs = this.idConexion;
                    
                    Dashboard.log("WS [" + idWs + "] AUTORIZA el riego. Riego en curso.");
                    DatabaseManager.actualizarEstado(idWs, "REGANDO");
                    Dashboard.actualizarEstado(idWs, Dashboard.Estado.REGANDO);
                    Dashboard.asignarOperario(idWs, idOperario);

                    KafkaManager.publicarEvento("INICIO_RIEGO", idWs + "#" + idOperario);

                    WM_Central_Thread hiloOperario = conexiones.get(idOperario);
                    if (hiloOperario != null) {
                        hiloOperario.enviarTextoOperario("AUTORIZADO: El riego ha comenzado en " + idWs);
                    }
                    return String.valueOf(ACK);
                }
                break;

            case "TELEMETRIA":
                if (partes.length >= 4) {
                    String idWs = partes[1];
                    String caudal = partes[2];
                    String volumen = partes[3];
                    Dashboard.actualizarTelemetria(idWs, caudal, volumen);
                    return String.valueOf(ACK);
                }
                break;

            case "AVERIA":
                if (partes.length >= 2) {
                    String idWs = partes[1];
                    Dashboard.log("¡ALERTA! Avería/Fuga detectada en WS [" + idWs + "].");
                    
                    String estadoActual = DatabaseManager.obtenerEstado(idWs);
                    if ("REGANDO".equals(estadoActual)) {
                        String idOp = Dashboard.getOperario(idWs);
                        if (idOp != null && !"-".equals(idOp)) {
                            WM_Central_Thread hiloOp = conexiones.get(idOp);
                            if (hiloOp != null) {
                                hiloOp.enviarTextoOperario("ABORTADO: Fuga detectada en estación [" + idWs + "].");
                            }
                        }
                        KafkaManager.publicarEvento("ABORTO_RIEGO", idWs);
                    }

                    DatabaseManager.actualizarEstado(idWs, "FUGA");
                    Dashboard.actualizarEstado(idWs, Dashboard.Estado.FUGA);
                    return String.valueOf(ACK);
                }
                break;

            case "RESUELTA":
                if (partes.length >= 2) {
                    String idWs = partes[1];
                    Dashboard.log("Avería resuelta en WS [" + idWs + "].");
                    DatabaseManager.actualizarEstado(idWs, "DISPONIBLE");
                    Dashboard.actualizarEstado(idWs, Dashboard.Estado.DISPONIBLE);
                    return String.valueOf(ACK);
                }
                break;

            case "FIN_RIEGO":
                if (partes.length >= 4) {
                    String idWs = partes[1];
                    String volumenTotal = partes[2];
                    String duracion = partes[3];
                    
                    String resumen = String.format("Volumen Consumido: %s L | Tiempo: %s s", volumenTotal, duracion);
                    Dashboard.log("Riego Finalizado en WS [" + idWs + "]. " + resumen);
                    
                    String idOp = Dashboard.getOperario(idWs);
                    DatabaseManager.actualizarEstado(idWs, "DISPONIBLE");
                    Dashboard.actualizarEstado(idWs, Dashboard.Estado.DISPONIBLE);

                    KafkaManager.publicarEvento("FIN_RIEGO", idWs + "#" + volumenTotal + "#" + duracion);

                    if (!"-".equals(idOp)) {
                        WM_Central_Thread hiloOp = conexiones.get(idOp);
                        if (hiloOp != null) {
                            hiloOp.enviarTextoOperario("RESUMEN_FIN_RIEGO: " + resumen);
                        }
                    }
                    return String.valueOf(ACK);
                }
                break;

            case "LISTAR_WS":
                String listado = Dashboard.obtenerListadoEstaciones();
                enviarTextoOperario("LISTA_ESTACIONES:" + listado);
                return null;
        }
        
        return String.valueOf(NACK);
    }

    private void manejarDesconexionOCaida() {
        if (this.idConexion != null) {
            conexiones.remove(this.idConexion);
            Dashboard.log("Caída/Timeout detectado en socket: " + this.idConexion);
            
            if (!this.idConexion.startsWith("OP_") && !this.idConexion.startsWith("FO_")) {
                String estadoAnterior = DatabaseManager.obtenerEstado(this.idConexion);
                
                // Estrategia 3: Aborto en Central si estaba regando
                if ("REGANDO".equals(estadoAnterior)) {
                    Dashboard.log("ABORTADO: La estación [" + this.idConexion + "] perdió conexión durante el riego.");
                    
                    String operarioAsignado = Dashboard.getOperario(this.idConexion);
                    if (operarioAsignado != null && !"-".equals(operarioAsignado)) {
                        WM_Central_Thread hiloOp = conexiones.get(operarioAsignado);
                        if (hiloOp != null) {
                            hiloOp.enviarTextoOperario("ABORTADO: La estación [" + this.idConexion + "] ha perdido la conexión durante el riego");
                        }
                    }
                    
                    // Publicar en Kafka para trazabilidad
                    KafkaManager.publicarEvento("ABORTO_RIEGO", this.idConexion);
                }

                // Estrategia 2: Marcar como DESCONECTADA
                DatabaseManager.actualizarEstado(this.idConexion, "DESCONECTADA");
                Dashboard.actualizarEstado(this.idConexion, Dashboard.Estado.DESCONECTADA);
            }
        }
    }

    @Override
    public void run() {
        try {
            // Estrategia 2: Timeout de 15s para detectar caídas/inactividad
            skCliente.setSoTimeout(15000);
            
            boolean conectado = true;
            while (conectado) {
                try {
                    String cadena = this.leeSocket(skCliente);
                    if (cadena == null) {
                        conectado = false;
                        continue;
                    }

                    String data = procesarTrama(cadena);
                    String respuesta = (data != null) ? ejecutarOperacion(data) : String.valueOf(NACK);

                    if (respuesta != null) {
                        this.escribeSocket(skCliente, respuesta);
                    }
                } catch (SocketTimeoutException te) {
                    System.err.println("[CENTRAL] Timeout 15s expirado en socket: " + idConexion);
                    conectado = false;
                } catch (EOFException eofe) {
                    conectado = false;
                }
            }
        } catch (Exception e) {
            System.err.println("[CENTRAL] Excepción en hilo de cliente: " + e.getMessage());
        } finally {
            manejarDesconexionOCaida();
            try {
                if (skCliente != null && !skCliente.isClosed()) skCliente.close();
            } catch (Exception ignored) {}
        }
    }
}