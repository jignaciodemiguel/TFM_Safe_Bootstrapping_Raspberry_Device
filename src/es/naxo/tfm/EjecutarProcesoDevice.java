package es.naxo.tfm;

import java.util.Base64;

import es.naxo.tfm.aws.Publicar_IoT_AWS;
import es.naxo.tfm.utils.Log;

/**
 * Clase que ejecuta el proceso para el que fue creado el Device. 
 * En la primera llamada, comprobará las credenciales y si no existen llamará al procedimiento de inicialización de las mismas. 
 * @author Nacho
 *
 */
public class EjecutarProcesoDevice {
	
	GenerarCertificado generarCertificado = null;
	private final static String KO_EXCEPTION = "KO";
	private boolean esPruebas = true; 
	
	public EjecutarProcesoDevice()     {

		Log.escribirLog("Inicializando dispositivo");
		
		// Obtengo el serial number, que será el identificador del dispositivo de cara a la validación contra AWS. 
		String idDevice = GenerarIdentificadorUnico.getSerialNumber();

		// Hago una prueba de publicación en AWS, para validar que el certificado es correcto. 
		Log.escribirLogPuntosSinLinea("Estableciendo conexión con Plataforma AWS IoT");
		boolean exito = Publicar_IoT_AWS.pruebaEnviarTemperatura(idDevice);
		
		if (exito == false)   {
			exito = inicializarBootstrapping();

			if (exito == true)    {
				Log.escribirLog("Proceso Bootstrapping concluido con exito");
			}
			else    {
				Log.escribirLog("Error en proceso Bootstrapping. Imposible iniciar comunicación con plataforma AWS IoT");
				return; 
			}
		}
		else   {
			Log.escribirLog(" OK");
		}

		Log.escribirLogPuntos("Iniciando proceso de envío continuo de temperaturaT");

		Publicar_IoT_AWS.enviarTemperaturaContinuo (idDevice);
	}
	
	private boolean inicializarBootstrapping()     {
		
		// Inicializo la clase de generación de certificado. 
		generarCertificado = new GenerarCertificado();

		Log.escribirLog("Iniciando proceso seguro de Bootstrapping contra AWS IoT");

		// Obtengo el serial number, que será el identificador del dispositivo de cara a la validación contra AWS. 
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
			System.err.println("Error en la generación del csr");
			return false; 
		}

		Log.escribirLogPuntos("  - Enviando CSR para firma remota");
		
		try   {
			
			String csrString = Base64.getEncoder().encodeToString(csr);
			String certificadoFirmadoString = null;
			
			if (esPruebas == true)    {
				certificadoFirmadoString = FirmarCertificado.firmarCertificadoConCA(csrString, identificadorUnico);
			}
			
			if (certificadoFirmadoString == null || "".equals (certificadoFirmadoString))    {
				Log.escribirLog ("  - Error en el proceso de firma del certificado");
				return false; 
			}
			
			Log.escribirLogPuntos("  - Certificado firmado recibido. Almacenandolo en el dispositivo");

			return generarCertificado.grabarCertificadoFirmado(certificadoFirmadoString);
		}
		
		catch (Exception ex)    {
			System.err.println("Excepcion al firmar el CSR");
			ex.printStackTrace();
			return false; 
		}
			
        // Genero un nuevo HashMap de parametros y le agrego los dos. 
        //HashMap<String, String> parametros = new HashMap<String, String>();
        //parametros.put ("identificador", identificadorUnico);
        //parametros.put ("csr", new String (csr));

        //String respuesta = ConexionHTTP.peticion("FirmaCSRServlet", ConexionHTTP.POST, parametros);

        //if (respuesta != null && !respuesta.equals (KO_EXCEPTION))   {

        	// Si entro es porque el resultado fue correcto. Obtenemos el certificado firmado. 
        //}
	}
	
	public static void main(String[] args) {

		new EjecutarProcesoDevice();
	}
}
