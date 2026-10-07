import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class WM_Central_Thread extends Thread {

	private Socket skCliente;
	
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
						Dashboard.registrarEstacion(id, location);
						return String.valueOf(ACK);
					}
				}
				break;

			case "ACTIVAR":
				// Subtarea 3.2: Peticiones de activación de riego (desde Operadores o WS manual)
				// Formato esperado: ACTIVAR#<ID_WS>#<ID_OPERARIO>
				if (partes.length >= 3) {
					String idWs = partes[1];
					String idOperario = partes[2];
					Dashboard.log("Petición de activación recibida para WS [" + idWs + "] por operario [" + idOperario + "].");
					// TODO: Validar en BD si la WS está DISPONIBLE y no tiene fuga.
					Dashboard.actualizarEstado(idWs, Dashboard.Estado.REGANDO);
					Dashboard.asignarOperario(idWs, idOperario);
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
				// Subtarea 3.4: Alertas de averías/fugas
				// Formato esperado: AVERIA#<ID_WS>
				if (partes.length >= 2) {
					String idWs = partes[1];
					Dashboard.log("¡ALERTA! Avería/Fuga detectada en WS [" + idWs + "].");
					Dashboard.actualizarEstado(idWs, Dashboard.Estado.FUGA);
					// TODO: Cambiar estado a FUGA (Rojo) en BD y abortar riego si está activo.
					return String.valueOf(ACK);
				}
				break;

			case "RESUELTA":
				// Subtarea 3.4: Notificaciones de avería resuelta
				// Formato esperado: RESUELTA#<ID_WS>
				if (partes.length >= 2) {
					String idWs = partes[1];
					Dashboard.log("Avería resuelta en WS [" + idWs + "].");
					Dashboard.actualizarEstado(idWs, Dashboard.Estado.DISPONIBLE);
					// TODO: Restaurar estado a DISPONIBLE (Verde) en la BD.
					return String.valueOf(ACK);
				}
				break;

			case "FIN_RIEGO":
				// Subtarea 3.5: Notificaciones de fin de riego
				// Formato esperado: FIN_RIEGO#<ID_WS>#<VOLUMEN_TOTAL>#<DURACION>
				if (partes.length >= 4) {
					String idWs = partes[1];
					String volumenTotal = partes[2];
					String duracion = partes[3];
					Dashboard.log("Fin de riego en WS [" + idWs + "]. Volumen total: " + volumenTotal + " L. Duración: " + duracion + " s.");
					Dashboard.actualizarEstado(idWs, Dashboard.Estado.DISPONIBLE);
					// TODO: Procesar fin de riego y notificar al operario
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

				// Respondemos al cliente con ACK o NACK
				this.escribeSocket(skCliente, respuesta);						
			}
			skCliente.close();
        } catch (Exception e) {
          System.out.println("Error en el hilo de cliente: " + e.getMessage());
        }
    }
}
