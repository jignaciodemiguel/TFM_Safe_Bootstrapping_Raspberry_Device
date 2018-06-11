package es.naxo.tfm;

import java.io.File;
import java.util.Base64;
import java.util.HashMap;

import es.naxo.tfm.aws.Comunicacion_IoT_AWS;
import es.naxo.tfm.utils.ConexionHTTP;
import es.naxo.tfm.utils.Constantes;
import es.naxo.tfm.utils.CryptoUtils;
import es.naxo.tfm.utils.CryptoUtils.KeyStorePasswordPair;
import es.naxo.tfm.utils.Log;

/**
 * Clase que ejecuta el proceso para el que fue creado el Device. 
 * En la primera llamada, comprobar� las credenciales y si no existen llamar� al procedimiento de Bootstrapping de las mismas. 
 * @author Nacho
 *
 */
public class EjecutarProcesoDevice {
	
	GenerarCertificado generarCertificado = null;
	
	/*
	 * Constructor de la clase, 
	 */
	public EjecutarProcesoDevice()     { }

	/* Metodo que ejecuta todo el proceso del dispositivo. Primero realiza una prueba de conexi�n con AWS, 
	 * para validar que hay conexi�n y que los certificados son correctos. 
	 * Si la conexi�n no es correcta, lanza el proceso de Bootstrapping.
	 * Finalmente lanza el proceso en s� de env�o de temperatura de manera c�clica.  
	 */
	public void ejecutarProceso()    {

		Log.escribirLog("\n\nInicializando dispositivo\n");
		
		// Obtengo el serial number, que ser� el identificador del dispositivo de cara a la validaci�n contra AWS. 
		String idDevice = GenerarIdentificadorUnico.getSerialNumber();

		// Valido la configuraci�n para ver si hace falta lanzar el Bootstrapping o no.  
		boolean exito = validarConfiguracion();
		
		if (exito == false)   {
			exito = inicializarBootstrapping();

			if (exito == true)    {
				Log.escribirLog("\nProceso Bootstrapping concluido con exito");
			}
			else    {
				Log.escribirLog("\nError en proceso Bootstrapping. Imposible iniciar comunicaci�n con plataforma AWS IoT");
				return; 
			}
		}
		else   {
			Log.escribirLog(" OK");
		}

		Log.escribirLogPuntos("\nIniciando proceso de env�o continuo de temperaturaT");

		Comunicacion_IoT_AWS.enviarTemperaturaContinuo (idDevice);
	}
	
	/*
	 * Metodo que realiza el proceso de Bootstrapping, que consiste en: 
	 *   - Obtiene el n�mero de serie de la Rasperry, y le aplica un SALT m�s un cifrado para no enviarlo en claro, generando un identificador unico para cualquier Rasperry. 
	 *   - Genera una clave privada, y un CSR asociado a dicha clave privada. La clave privada la graba a disco para usarla m�s adelante. 
	 *   - Env�a petici�n HTTPS remota para que se firme dicha CSR con la clave privada de la CA de la organizaci�n, y se de de alta el Thing bajo la suscripci�n IoT de AWS. 
	 *   - Recibe el certificado firmado como respuesta HTTPS. Le concatena la clave p�blica de la CA y lo graba a fichero.
	 * 
	 * Devuelve el resultado de dicho proceso. 
	 */
	public boolean inicializarBootstrapping()     {
		
		// Inicializo la clase de generaci�n de certificado. 
		generarCertificado = new GenerarCertificado();

		Log.escribirLog("\n\nIniciando proceso seguro de Bootstrapping contra AWS IoT\n");

		// Obtengo el serial number, que ser� el identificador del dispositivo de cara a la validaci�n contra AWS. 
		String idDevice = GenerarIdentificadorUnico.getSerialNumber();
		
		Log.escribirLogPuntos("  - Obteniendo Serial Number unico del dispositivo");

		// Obtengo el identificador unico ya generado a enviar al servidor
		String identificadorUnico = GenerarIdentificadorUnico.getIdentificadorUnico();

		if (identificadorUnico == null)    {
			Log.escribirLog("Error al obtener el identificador unico. ");
			return false; 
		}

		// Obtengo el CSR del certificado a enviar al servidor. 
		byte[] csr = generarCertificado.generarCSR(idDevice);
		
		if (csr == null)    {
			System.err.println("Error en la generaci�n del csr");
			return false; 
		}

		Log.escribirLogPuntos("  - Enviando CSR para firma remota");
		
		try   {
			
			String csrString = Base64.getEncoder().encodeToString(csr);
			String certificadoFirmadoString = null;
			
			// Ejecuto la llamada al servidor HTTPS para que firme el certificado, y cree el Thing en AWS. 
			certificadoFirmadoString = solicitarFirmaCertificadoConCA(csrString, identificadorUnico);
			
			if (certificadoFirmadoString == null || "".equals (certificadoFirmadoString))    {
				return false; 
			}
			
			Log.escribirLogPuntos("  - Certificado firmado recibido. Almacenandolo en el dispositivo");

			boolean resultado = generarCertificado.grabarCertificadoFirmado(certificadoFirmadoString);
			
			if (resultado == false)    {
				Log.escribirLog ("  - Error en el proceso de grabaci�n del certificado firmado");
				return false; 
			}

			Log.escribirLogPuntos("  - Dado de alta el Device en AWS");
			Log.escribirLog("\nProceso de Bootstrapping ejecutado con �xito");
		}
		
		catch (Exception ex)    {
			System.err.println("Excepcion al firmar el CSR");
			ex.printStackTrace();
			return false; 
		}
		
		return true; 
	}

	/*
	 * Metodo que valida si ya se ejecut� el proceso de bootstrapping anteriormente, y por tanto est� listo para 
	 * iniciar la comunicaci�n MQTT con AWS IoT.
	 * Valida que exista el certificado y la clave primaria, y si existen intenta comunicar un mensaje de prueba. 
	 */
	public boolean validarConfiguracion ()    {
		
		Log.escribirLog("\n\nIniciando proceso de validacion de configuracion\n");

		Log.escribirLogPuntosSinLinea("   - Buscando certificado y clave primaria");

		// Primero busca el certificado de cliente y la clave primaria. 
    	KeyStorePasswordPair pair = CryptoUtils.getKeyStorePasswordPair(Constantes.certificadoFirmadoDevice, Constantes.privateKeyCertificadoDevice, "RSA");
    	
    	if (pair == null)    {
    		Log.escribirLog("KO");
    		Log.escribirLog("\nValidaci�n de configuraci�n incorrecta. Es necesario lanzar proceso de Bootstrapping");
    		return false; 
    	}
    	
    	Log.escribirLog("OK");
		
		// Hago una prueba de publicaci�n en AWS, para validar que el certificado es correcto. 
		Log.escribirLogPuntosSinLinea("   - Estableciendo conexi�n de test con Plataforma AWS IoT");

		// Obtengo el serial number, que ser� el identificador del dispositivo de cara a la validaci�n contra AWS. 
		String idDevice = GenerarIdentificadorUnico.getSerialNumber();
		
		boolean exito = Comunicacion_IoT_AWS.pruebaEnviarTemperatura(idDevice);

		if (exito == false)   {
	    	Log.escribirLog("KO");
	    	Log.escribirLog("\nValidaci�n de configuraci�n incorrecta. Es necesario lanzar proceso de Bootstrapping");
    		return false; 
		}

    	Log.escribirLog("OK");
    	Log.escribirLog("\nValidaci�n de configuraci�n correcta. No es necesario lanzar el proceso de Bootstrapping");
		
    	return true; 
	}
	
	/*
	 * Elimina la configuraci�n de certificados, para forzar la siguiente vez a iniciar de nuevo el proceso 
	 * de Bootstrapping. 
	 */
	public void resetearConfiguracion ()    {
		
		Log.escribirLog("\n\nIniciando proceso de reseteo de configuracion\n");
		boolean exito1 = true; 
		boolean exito2 = true; 

		Log.escribirLogPuntosSinLinea("   - Borrando clave primaria");

		// Primero busco la PrimaryKey y si existe la borro. 
        File file = new File(Constantes.privateKeyCertificadoDevice);
        if (file.exists()) {
        	exito1 = file.delete();
        }

        if (exito1 == false)    {
    		Log.escribirLog("KO - No se pudo eliminar el fichero de clave primaria");
        }
        else    {
        	Log.escribirLog("OK");
        }

		Log.escribirLogPuntosSinLinea("   - Borrando el certificado cliente");

		// Primero busco la PrimaryKey y si existe la borro. 
        file = new File(Constantes.certificadoFirmadoDevice);
        if (file.exists()) {
        	exito2 = file.delete();
        }

        if (exito2 == false)    {
    		Log.escribirLog("KO - no se pudo eliminar el fichero del certificado");
        }
        else    {
        	Log.escribirLog("OK");
        }

        if (exito1 == true && exito2 == true)    {
    		Log.escribirLog("\nProceso de reseteo de configuracion finalizado con �xito");
        }
	}
	
	/*
	 * Realiza la petici�n HTTPS al servidor remoto para firmar el CSR.
	 */
	private static String solicitarFirmaCertificadoConCA (String csr, String identificadorUnico)    {

        // Genero un nuevo HashMap de parametros y le agrego los dos. 
        HashMap<String, String> parametros = new HashMap<String, String>();
        parametros.put ("identificador", identificadorUnico);
        parametros.put ("csr", csr);

        String respuesta = ConexionHTTP.peticion("BootstrappingRasperryServlet", ConexionHTTP.POST, parametros);

        if (respuesta == null || respuesta.startsWith("KO"))   {

        	// Si entro es porque el resultado fue incorrecto.
			Log.escribirLog ("  - Error en el proceso de firma del certificado");
			return null;
        }
        
        // Todo ha ido bien, devolvemos el certificado firmado.
        return respuesta; 
	}
	
	/*
	 * Metodo main de lanzamiento del proceso. 
	 */
	public static void main(String[] args) {

		EjecutarProcesoDevice ejecutar = new EjecutarProcesoDevice();
		
		//args = new String[1]; args[0] = "--reset";
		
		if (args.length != 1)    {
			ejecutar.pintaOpciones();
			return; 
		}
		
		String parametro = args[0];
		if (parametro == null)   {
			ejecutar.pintaOpciones();
			return; 
		}

		if (!parametro.equals("--exec") && !parametro.equals("--boot") && !parametro.equals("--reset") && !parametro.equals("--conf"))   {
			ejecutar.pintaOpciones();
			return; 
		}
		
		switch (parametro)    {
			case "--exec": 
				ejecutar.ejecutarProceso();
				break; 
			case "--boot": 
				ejecutar.inicializarBootstrapping();
				break; 
			case "--reset": 
				ejecutar.resetearConfiguracion();
				break; 
			case "--conf": 
				ejecutar.validarConfiguracion();
				break; 
		}
		System.exit(1);
	}

	private void pintaOpciones()    {
		System.out.println("Error al lanzar el ejecutable. Parametros incorrectos: ");
		System.out.println("\n");
		System.out.println("   --exec   Ejecuta el proceso completo de envio continuo de temperatura");
		System.out.println("   --boot   Ejecuta el proceso de bootstrapping, para dejar el dispositivo preparado para la comunicaci�n");
		System.out.println("   --reset   Resetea la configuraci�n para dejar el dispositivo con la configuraci�n inicial");
		System.out.println("   --conf   Valida la configuraci�n para comprobar si est� preparado para iniciar la comunicaci�n de la temperatura");
		System.out.println("\n");
	}
}
