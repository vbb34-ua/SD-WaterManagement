import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class WM_Central_Thread extends Thread {

	private Socket skCliente;
	
	// Registro global de hilos para poder enviar mensajes cruzados (Central -> WS o Central -> Operario)
	public static java.util.concurrent.ConcurrentHashMap<String, WM_Central_Thread> conexiones = new java.util.concurrent.ConcurrentHashMap<>();
	private String idConexion = null; // Guardará el ID de esta conexión (ej. WS_01 o OP_01)

	// Caracteres de control ASCII
	private static final char STX = 0x02;
	private static final char ETX = 0x03;
	private static final char ACK = 0x06;
	private static final char NACK = 0x15;

	public WM_Central_Thread(Socket p_cliente) {
		this.skCliente = p_cliente;
	}
	
	// Lee datos del socket utilizando readUTF
	public String leeSocket(Socket p_sk) {
		try {
			InputStream aux = p_sk.getInputStream();
			DataInputStream flujo = new DataInputStream(aux);
			return flujo.readUTF();
		} catch (EOFException e) {
			// El cliente ha cerrado la conexión
			return null;
		} catch (Exception e) {
			System.out.println("Error leyendo socket: " + e.getMessage());
			return null;
		}
	}

	// Escribe datos en el socket
	public void escribeSocket(Socket p_sk, String p_Datos) {
		try {
			OutputStream aux = p_sk.getOutputStream();
			DataOutputStream flujo = new DataOutputStream(aux);
			flujo.writeUTF(p_Datos);      
		} catch (Exception e) {
			System.out.println("Error escribiendo en socket: " + e.getMessage());
		}
	}

	// Envía un mensaje formateado con STX, ETX y LRC a este cliente
	public void enviarTrama(String datos) {
		String mensaje = STX + datos + ETX;
		char lrc = calcularLRC(mensaje);
		escribeSocket(skCliente, mensaje + lrc);
	}
	
	// Envía un texto plano (sin protocolo LRC) a clientes que no lo soporten, como la app básica del operario
	public void enviarTextoOperario(String texto) {
		escribeSocket(skCliente, texto);
	}
	
	// Subtarea 2.1: Función para calcular el LRC (XOR byte a byte)
	private char calcularLRC(String mensaje) {
		char lrc = 0;
		for (int i = 0; i < mensaje.length(); i++) {
			lrc ^= mensaje.charAt(i);
		}
		return lrc;
	}

	// Valida el protocolo <STX><DATA><ETX><LRC> y extrae la <DATA>
	private String procesarTrama(String trama) {
		if (trama == null || trama.length() < 3) return null;
		if (trama.charAt(0) != STX) return null;

		int etxIndex = trama.indexOf(ETX);
		if (etxIndex == -1 || etxIndex == trama.length() - 1) return null;

		char lrcRecibido = trama.charAt(trama.length() - 1);
		
		// Validamos el LRC calculándolo sobre <STX><DATA><ETX>
		String mensajeParaLRC = trama.substring(0, trama.length() - 1);
		char lrcCalculado = calcularLRC(mensajeParaLRC);
		
		if (lrcRecibido != lrcCalculado) {
			System.out.println("SRV: Error de validación LRC.");
			return null;
		}

		// Extraemos el campo DATA
		return trama.substring(1, etxIndex);
	}

	// Subtarea 2.2, 2.3 y 3: Ejecutar la operación de la DATA
	private String ejecutarOperacion(String data) {
		String[] partes = data.split("#");
		String operacion = partes[0];

		switch (operacion) {
			case "REGISTRO":
				// Subtarea 3.1: Peticiones de registro/alta de nuevas WS
				if (partes.length >= 3) {
					String id = partes[1];
					String location = partes[2];
					boolean exito = DatabaseManager.registrarEstacion(id, location);
					if (exito) {
						this.idConexion = id;
						conexiones.put(id, this); // Registramos el hilo de la WS
						Dashboard.registrarEstacion(id, location);
						return String.valueOf(ACK);
					}
				}
				break;

			case "REGISTRO_OPERARIO":
				// Registramos la conexión del operario
				if (partes.length >= 2) {
					String idOp = partes[1];
					this.idConexion = idOp;
					conexiones.put(idOp, this);
					return String.valueOf(ACK);
				}
				break;

			case "ACTIVAR":
				// Subtarea 5.1 y 5.2: Validación y Negociación
				// Formato esperado: ACTIVAR#<ID_WS>#<ID_OPERARIO>
				if (partes.length >= 3) {
					String idWs = partes[1];
					String idOperario = partes[2];
					
					// Registramos al operario si no estaba
					if (this.idConexion == null) {
						this.idConexion = idOperario;
						conexiones.put(idOperario, this);
					}
					
					Dashboard.log("Petición de activación recibida para WS [" + idWs + "] por operario [" + idOperario + "].");
					enviarTextoOperario("PROCESANDO: Solicitando estado de " + idWs + " a la Base de Datos...");
					
					// 5.1.1: Verificar si la WS está en estado "DISPONIBLE"
					String estadoWS = DatabaseManager.obtenerEstado(idWs);
					
					if ("DISPONIBLE".equals(estadoWS)) {
						enviarTextoOperario("INFO: Estación DISPONIBLE. Solicitando autorización a la estación de riego (WS)...");
						Dashboard.log("Solicitando autorización a la estación [" + idWs + "]...");
						
						// 5.2.1: Enviar solicitud de autorización a la WS
						WM_Central_Thread hiloWS = conexiones.get(idWs);
						if (hiloWS != null) {
							hiloWS.enviarTrama("SOLICITUD_RIEGO#" + idOperario);
						} else {
							enviarTextoOperario("ERROR: La estación " + idWs + " está desconectada del socket.");
							Dashboard.log("Fallo: WS [" + idWs + "] desconectada físicamente.");
						}
					} else {
						// WS no apta para regar
						Dashboard.log("Activación DENEGADA para WS [" + idWs + "]. Estado actual: " + estadoWS);
						enviarTextoOperario("DENEGADO: La estación " + idWs + " se encuentra en estado " + estadoWS);
					}
					return null; // No mandamos ACK automático, mandamos texto asíncrono
				}
				break;

			case "RIEGO_AUTORIZADO":
				// La WS responde que acepta el riego: RIEGO_AUTORIZADO#<ID_OPERARIO>
				if (partes.length >= 2) {
					String idOperario = partes[1];
					String idWs = this.idConexion;
					
					Dashboard.log("WS [" + idWs + "] AUTORIZA el riego. Riego en curso.");
					DatabaseManager.actualizarEstado(idWs, "REGANDO");
					Dashboard.actualizarEstado(idWs, Dashboard.Estado.REGANDO);
					Dashboard.asignarOperario(idWs, idOperario);

					// Publicar evento en Kafka para que el Engine (WM_WS_E) sepa que debe abrir la electroválvula
					KafkaManager.publicarEvento("INICIO_RIEGO", idWs + "#" + idOperario);

					// Notificamos al operario
					WM_Central_Thread hiloOperario = conexiones.get(idOperario);
					if (hiloOperario != null) {
						hiloOperario.enviarTextoOperario("AUTORIZADO: El riego ha comenzado en " + idWs);
					}
					return String.valueOf(ACK);
				}
				break;

			case "RIEGO_DENEGADO":
				// La WS responde que rechaza el riego (por fuga local u otro motivo)
				if (partes.length >= 2) {
					String idOperario = partes[1];
					String idWs = this.idConexion;
					
					Dashboard.log("WS [" + idWs + "] DENIEGA el riego (posible avería).");
					
					WM_Central_Thread hiloOperario = conexiones.get(idOperario);
					if (hiloOperario != null) {
						hiloOperario.enviarTextoOperario("DENEGADO: La estación " + idWs + " ha rechazado la orden de riego internamente.");
					}
					return String.valueOf(ACK);
				}
				break;

			case "TELEMETRIA":
				// Subtarea 3.3: Recibir telemetría continua (1 vez por segundo) durante el riego
				// Formato esperado: TELEMETRIA#<ID_WS>#<CAUDAL>#<VOLUMEN_ACUMULADO>
				if (partes.length >= 4) {
					String idWs = partes[1];
					String caudal = partes[2];
					String volumen = partes[3];
					Dashboard.actualizarTelemetria(idWs, caudal, volumen);
					return String.valueOf(ACK);
				}
				break;

			case "AVERIA":
				// Subtarea 4.2.1 y 4.2.2: Alertas de averías/fugas
				// Formato esperado: AVERIA#<ID_WS>
				if (partes.length >= 2) {
					String idWs = partes[1];
					Dashboard.log("¡ALERTA! Avería/Fuga detectada en WS [" + idWs + "].");
					
					// 4.2.2: Verificar si estaba regando para abortar
					String estadoActual = DatabaseManager.obtenerEstado(idWs);
					if ("REGANDO".equals(estadoActual)) {
						Dashboard.log("ABORTANDO RIEGO en WS [" + idWs + "] por fuga. Notificando a Engine via Kafka...");
						// Publica el evento de aborto para que el Engine lo reciba y cierre la electroválvula
						KafkaManager.publicarEvento("ABORTO_RIEGO", idWs);
					}

					// 4.2.1: Cambiar estado a FUGA
					DatabaseManager.actualizarEstado(idWs, "FUGA");
					Dashboard.actualizarEstado(idWs, Dashboard.Estado.FUGA);
					
					return String.valueOf(ACK);
				}
				break;

			case "RESUELTA":
				// Subtarea 4.2.3: Notificaciones de avería resuelta
				// Formato esperado: RESUELTA#<ID_WS>
				if (partes.length >= 2) {
					String idWs = partes[1];
					Dashboard.log("Avería resuelta en WS [" + idWs + "].");
					
					// 4.2.3: Restaurar estado a DISPONIBLE
					DatabaseManager.actualizarEstado(idWs, "DISPONIBLE");
					Dashboard.actualizarEstado(idWs, Dashboard.Estado.DISPONIBLE);
					
					return String.valueOf(ACK);
				}
				break;

			case "FIN_RIEGO":
				// Subtarea 5.3: Cierre correcto del ciclo de riego
				// Formato esperado: FIN_RIEGO#<ID_WS>#<VOLUMEN_TOTAL>#<DURACION>
				if (partes.length >= 4) {
					String idWs = partes[1];
					String volumenTotal = partes[2];
					String duracion = partes[3];
					
					// 5.3.1: Calcular y formatear el resumen final
					String resumen = String.format("Volumen Consumido: %s L | Tiempo: %s s", volumenTotal, duracion);
					Dashboard.log("Riego Finalizado en WS [" + idWs + "]. " + resumen);
					
					// Obtenemos qué operario estaba regando antes de limpiar el estado
					String idOp = Dashboard.getOperario(idWs);
					
					// 5.3.2: Cambiar el estado de la WS a "Disponible"
					DatabaseManager.actualizarEstado(idWs, "DISPONIBLE");
					Dashboard.actualizarEstado(idWs, Dashboard.Estado.DISPONIBLE);

					// Publicar evento de fin de riego en Kafka
					KafkaManager.publicarEvento("FIN_RIEGO", idWs + "#" + volumenTotal + "#" + duracion);

					// 5.3.2: Enviar el resumen final a la app del operario
					if (!"-".equals(idOp)) {
						WM_Central_Thread hiloOp = conexiones.get(idOp);
						if (hiloOp != null) {
							hiloOp.enviarTextoOperario("RESUMEN_FIN_RIEGO: " + resumen);
						}
					}
					
					return String.valueOf(ACK);
				}
				break;
		}
		
		// Operación no reconocida, falló algún proceso o formato incorrecto
		return String.valueOf(NACK);
	}

	@Override
    public void run() {
        try {
			boolean conectado = true;
			while (conectado) {
				String cadena = this.leeSocket(skCliente);
				
				if (cadena == null) {
					conectado = false; // Cliente desconectado
					continue;
				}

				// Extraemos los datos de la trama
				String data = procesarTrama(cadena);
				String respuesta;

				if (data != null) {
					respuesta = ejecutarOperacion(data);
				} else {
					respuesta = String.valueOf(NACK);
				}

				// Respondemos al cliente con ACK o NACK si es necesario
				if (respuesta != null) {
					this.escribeSocket(skCliente, respuesta);
				}
			}
			if (this.idConexion != null) {
				conexiones.remove(this.idConexion);
				Dashboard.log("Desconexión detectada: " + this.idConexion);
				if (!this.idConexion.startsWith("OP_") && !this.idConexion.startsWith("FO_")) {
					DatabaseManager.actualizarEstado(this.idConexion, "DESCONECTADA");
					Dashboard.actualizarEstado(this.idConexion, Dashboard.Estado.DESCONECTADA);
				}
			}
			skCliente.close();
        } catch (Exception e) {
          System.out.println("Error en el hilo de cliente: " + e.getMessage());
          if (this.idConexion != null) {
              conexiones.remove(this.idConexion);
              if (!this.idConexion.startsWith("OP_") && !this.idConexion.startsWith("FO_")) {
                  DatabaseManager.actualizarEstado(this.idConexion, "DESCONECTADA");
                  Dashboard.actualizarEstado(this.idConexion, Dashboard.Estado.DESCONECTADA);
              }
          }
        }
    }
}
