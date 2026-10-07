import java.net.*;
import java.io.IOException;

public class WM_Central {

	public static void main(String[] args) {
		String puerto = "";

		if (args.length < 1) {
			System.out.println("Debe indicar el puerto de escucha del ser	vidor");
			System.out.println("Uso: java WM_Central puerto_servidor");
			System.exit(1);
		}
		puerto = args[0];

		//  Inicializar sistema consultando la BD
		DatabaseManager.inicializarYcargarEstaciones();

		// Inicializar el Dashboard UI de terminal
		Dashboard.iniciar();

		// Subtarea 1.1.1: Bucle de ejecución continuo para mantener el sistema 24/7
		while (true) {
			try (ServerSocket skServidor = new ServerSocket(Integer.parseInt(puerto))) {
				Dashboard.log("Servidor CENTRAL iniciado. Escuchando el puerto " + puerto);
				Dashboard.log("Esperando conexiones de estaciones (WS) y operarios (FO)...");

				while (true) {
					try {
						// Se espera un cliente que quiera conectarse
						Socket skCliente = skServidor.accept();
						System.out.println("Nueva conexión recibida: " + skCliente.getInetAddress().getHostAddress());
						
						// Iniciar hilo para manejar la conexión del cliente
						Thread t = new WM_Central_Thread(skCliente);
						t.start();
					} catch (IOException e) {
						// Subtarea 1.1.2: Manejo de excepciones al aceptar un cliente
						System.err.println("Error al aceptar la conexión de un cliente: " + e.getMessage());
					} catch (Exception e) {
						// Subtarea 1.1.2: Captura de cualquier otra excepción para evitar caídas
						System.err.println("Excepción inesperada en el bucle de clientes: " + e.getMessage());
						e.printStackTrace();
					}
				}
			} catch (BindException e) {
				System.err.println("El puerto " + puerto + " ya está en uso. Reintentando en 5 segundos...");
				esperar(5000);
			} catch (IOException e) {
				System.err.println("Error grave en el ServerSocket: " + e.getMessage() + ". Reintentando en 5 segundos...");
				esperar(5000);
			} catch (Exception e) {
				System.err.println("Excepción crítica en el servidor principal: " + e.getMessage() + ". Reintentando en 5 segundos...");
				e.printStackTrace();
				esperar(5000);
			}
		}
	}

	// Método auxiliar para evitar sobrecargar la CPU en caso de fallos continuos
	private static void esperar(int milisegundos) {
		try {
			Thread.sleep(milisegundos);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}
}
