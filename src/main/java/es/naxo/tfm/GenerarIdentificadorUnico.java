package es.naxo.tfm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Base64;

import es.naxo.tfm.utils.Cifrado;

/**
 * Clase que se encarga de generar un identificador unico de cada Rasperry dentro del proceso de Bootstrapping. 
 * Para ello obtiene el Serial Number, le concatena por delante y por detrás unos SALT, y posteriormente cifra 
 * la cadena con un clave compartida, para no enviarla en claro. 
 * @author Nacho
 *
 */
public class GenerarIdentificadorUnico {

	// Clave usada compartida para generar el certificado unico. 
	private static final String claveSecretoCompartido1 = "6849576002387456";
	private static final String claveSecretoCompartido2 = "9472652849608709";
	
	/*
	 * Metodo para obtener el serial number de una Rasperry con Linux. 
	 * Si detecta que estamos en un Windows de pruebas, hardcodea un serial number con un formato valido. 
	 */
	public static String getSerialNumber()    {
		
		// Si estamos en modo pruebas (Windows) devolvemos un S/N inventado. 
		boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

		if (isWindows == true)    {
			return "00000000FFFFFFFF";    // Ejemplo de Serial Number de Rasperry. 
		}
		
		String command = "cat /proc/cpuinfo";
		
		Process p;
		String serialNumber = null; 

		try {
			p = Runtime.getRuntime().exec(command);
			p.waitFor();
		
		    String line;
		    BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
		    while ((line = input.readLine()) != null) {
		    	if (line.contains("Serial"))    {
		    		String textos[] = line.split(":");
		    		serialNumber = textos[1].trim();
		        }
		    }

		    input.close();	
		} 
		
		catch (Exception e) {
			System.err.println("Excepcion al obtener el Serial Number del dispositivo");
			e.printStackTrace();
		}
		
		if (serialNumber == null || "".equals(serialNumber))    {
			
			// Si no hay Serial Number es que estamos simulando en un dispositivo NO Rasperry, enviamos el de Test
			return "00000000FFFFFFFF";
		}
		
        return serialNumber; 
	}
	
	/** 
	 * Genera el identificador unico que usaremos para la primera conexión HTTP
	 * @return
	 */
	public static String getIdentificadorUnico()     {
		
		// Primero obtenemos el serial number de la Rasperry. 
		String identificadorClaro = getSerialNumber();
		
		if (identificadorClaro == null)    {
			return null; 
		}
		
		// Le concatemos los secretos compartidos, uno por delante y el otro por detrás. 
		identificadorClaro = claveSecretoCompartido1 + identificadorClaro + claveSecretoCompartido2;
	
		// Lo ciframos con AES y la clave también secreta
		byte[] cifrado = Cifrado.cifra(identificadorClaro);
		
		// Lo convertimos a base64 para poder enviarlo por HTTP.
		String cifradoString = Base64.getEncoder().encodeToString(cifrado);
		
		return cifradoString;
	}	
}
