import java.lang.Exception;
import java.net.Socket;
import java.io.*;

public class WM_Central_Thread extends Thread {

	private Socket skCliente;
	
	public WM_Central_Thread(Socket socket) {
        this.skCliente = socket;
    }

    public String leeSocket(Socket p_sk) {
        try {
            DataInputStream flujo = new DataInputStream(p_sk.getInputStream());
            return flujo.readUTF();
        } catch (Exception e) {
            return null;
        }
    }

    public void escribeSocket(Socket p_sk, String p_Datos) {
        try {
            DataOutputStream flujo = new DataOutputStream(p_sk.getOutputStream());
            flujo.writeUTF(p_Datos);
        } catch (Exception e) {
            System.err.println("Error escribiendo en socket: " + e.getMessage());
        }
    }
	
	public int sumar(int p_a, int p_b)
	{
		return p_a+p_b;
	}

	public int multiplicar(int p_a, int p_b)
	{
		return p_a*p_b;
	}

	public int realizarOperacion(String p_Cadena)
	{
		String[] operacion = p_Cadena.split(",");
		int res=0;
		
		System.out.println("SRV: La operacion es: " + operacion[0]);
		if(operacion.length != 1)
		{
			System.out.println("SRV: El operando 1 es " + operacion[1] + " y el operando 2 es " + operacion[2]);
			if(operacion[0].compareTo("suma")==0)
			{
				res = sumar(Integer.parseInt(operacion[1]),Integer.parseInt(operacion[2]));
			}
			else
			{
				if(operacion[0].compareTo("mult")==0)
				{
					res = multiplicar(Integer.parseInt(operacion[1]),Integer.parseInt(operacion[2]));	
				}	
				else
				{
					
					res = -1;
				}
			}
			System.out.println("SRV: El resultado es: " + res);
		}else
		{
			res = -1;
		}	
		return (res);
	}
	
	
	
	public void run() {
        try {
            // Lectura de la trama inicial
            String trama = leeSocket(skCliente);

            if (trama != null) {
                // Formato esperado: REGISTRO#<ID_ESTACION>#<UBICACION>
                String[] campos = trama.split("#");

                if (campos.length == 3 && "REGISTRO".equalsIgnoreCase(campos[0])) {
                    String idEstacion = campos[1];
                    String ubicacion = campos[2];

                    System.out.println(">> Registro recibido: Estación [" + idEstacion + "] ubicada en [" + ubicacion + "]");

                    // Respuesta confirmando registro
                    String respuesta = "STATUS#OK#Estacion registrada correctamente";
                    escribeSocket(skCliente, respuesta);
                } else {
                    escribeSocket(skCliente, "STATUS#ERROR#Formato de trama invalido");
                }
            }

            skCliente.close();
        } catch (Exception e) {
            System.err.println("Error procesando cliente: " + e.getMessage());
        }
    }
}
