import java.io.*;
import java.net.*;

public class Watering_Station {
	
	public enum estado{
		DISPONIBLE("VERDE", false, null),
	    REGANDO("VERDE", true, null),
	    FUGA("ROJO", false, null),
	    FUERA_DE_SERVICIO("NARANJA", false, "Fuera de Servicio"),
	    DESCONECTADA("GRIS", false, null);
		
		private final String colorFondo;
	    private final boolean parpadea;
	    private final String leyenda;

	    estado(String colorFondo, boolean parpadea, String leyenda) {
	        this.colorFondo = colorFondo;
	        this.parpadea = parpadea;
	        this.leyenda = leyenda;
	    }

	    public String getColorFondo() { return colorFondo; }
	    public boolean isParpadea() { return parpadea; }
	    public String getLeyenda() { return leyenda; }
	}

	private final String id;
	
	private double volumen_acumulado;
	
	private final String ubicacion;
	
	private estado EstadoActual;
	
	private double caudal;
	private boolean fuga;
	private boolean electrovalvula;
	
	private String id_operario_actual;
	
	public Watering_Station(String id,String ubicacion) {
		this.id = id;
		this.ubicacion = ubicacion;
		this.EstadoActual = estado.DESCONECTADA;
		this.volumen_acumulado = 0.0;
		this.caudal = 0.0;
		this.fuga = false;
		this.electrovalvula = false;
		this.id_operario_actual = null;
	}
	
	public String getId() { return id ; }
    public String getUbicacion() { return ubicacion; }
    public estado getEstadoActual() { return EstadoActual; }
    public boolean isElectrovalvulaAbierta() { return electrovalvula; }
    public boolean isFuga() { return fuga; }
    public double getCaudalLitrosMinuto() { return caudal; }
    public double getVolumenAcumuladoLitros() { return volumen_acumulado; }
    public String getIdOperarioActual() { return id_operario_actual; }
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public String leeSocket(Socket p_sk) {
        try {
            DataInputStream flujo = new DataInputStream(p_sk.getInputStream());
            return flujo.readUTF();
        } catch (Exception e) {
            System.err.println("Error leyendo respuesta: " + e.getMessage());
            return null;
        }
    }

    public void escribeSocket(Socket p_sk, String p_Datos) {
        try {
            DataOutputStream flujo = new DataOutputStream(p_sk.getOutputStream());
            flujo.writeUTF(p_Datos);
        } catch (Exception e) {
            System.err.println("Error enviando datos: " + e.getMessage());
        }
    }

    public void registrarEstacion(String host, int puerto, String idEstacion, String ubicacion) {
        try (Socket skCliente = new Socket(host, puerto)) {
            // Construcción del mensaje: REGISTRO#<ID>#<UBICACION>
            String tramaEnvio = "REGISTRO#" + idEstacion + "#" + ubicacion;
            System.out.println("Enviando a WM_Central: " + tramaEnvio);
            escribeSocket(skCliente, tramaEnvio);

            // Lectura e impresión de la respuesta
            String respuesta = leeSocket(skCliente);
            System.out.println("Respuesta del servidor: " + respuesta);

            System.out.println("Cerrando conexion...");
        } catch (Exception e) {
            System.err.println("Error de conexion con WM_Central: " + e.getMessage());
        }
    }

	/**
	 * @param args
	 */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java WM_WS_M <host_servidor> <puerto_servidor> [id_estacion] [ubicacion]");
            System.exit(1);
        }

        String host = args[0];
        int puerto = Integer.parseInt(args[1]);
        String idEstacion = (args.length >= 3) ? args[2] : "WS-04";
        String ubicacion = (args.length >= 4) ? args[3] : "River Park";

        Watering_Station cliente = new Watering_Station();
        cliente.registrarEstacion(host, puerto, idEstacion, ubicacion);
    }
}
