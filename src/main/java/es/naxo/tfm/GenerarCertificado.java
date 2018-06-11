package es.naxo.tfm;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.Base64;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import es.naxo.tfm.utils.Constantes;
import es.naxo.tfm.utils.Log;

/**
 * Clase que concentra los metodos para crear el certificado que usaremos para conectar con AWS una vez establecida la primera conexión. 
 * @author Nacho
 *
 */
public class GenerarCertificado    {

	// Variable que almacenará el par de claves privada y pública iniciales. 
	public KeyPair parClaves = null; 
	
	// Variable que almacenará el CSR generado. 
	private PKCS10CertificationRequest csr = null; 
	private Certificate certificadoFirmado = null;
	
	public GenerarCertificado() {}
	
	/**
	 * Genera un nuevo par de claves privada y pública, que usaremospara generar el CSR posteriormente
	 * Almacena también la clave privada en un fichero en el dispositivo, para usar para la conexión contra AWS.
	 */
	public void generarParClaves()     {
		
		try    {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(2048);
			parClaves = keyPairGenerator.generateKeyPair();
		}
		
		catch (NoSuchAlgorithmException ex)    {
			System.err.println("Excepcion al generar la clave privada");
			ex.printStackTrace();
		}

		// Una vez generados el par de claves, almacenamos en fichero la clave privada, pues la necesitaremos posteriormente para acceder a AWS cuando tengamos ya el certificado firmado.
		try    {

			FileOutputStream out = new FileOutputStream(Constantes.privateKeyCertificadoDevice);
			byte[] clavePrivada = parClaves.getPrivate().getEncoded();
			out.write(clavePrivada);
			out.close();
		}
		
		catch (Exception ex)    {
			System.err.println("Excepcion al generar la clave privada");
			ex.printStackTrace();
		}
	}
	
	/*
	 * Genera, a partir de una clave privada y publica, una petición CSR. 
	 * Incluye como CN en dicha petición, el identificador del dispositivo, que será "Rasperry_{serialNumber}".
	 */
	public byte[] generarCSR (String idDevice)    {
		
		Log.escribirLogPuntos("  - Generando clave primaria");
		generarParClaves();		

		Log.escribirLogPuntos("  - Generando CSR de firma");
		
		try    {
			
			// Generamos el CN con el IdDevice, porque AWS valida que el IdDevice coincida con el CN del certificado del dispositivo. 
			X500Principal x500Principal = new X500Principal("CN=Rasperry_" + idDevice);
			PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder	(x500Principal, parClaves.getPublic());
	
			JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withRSA");
			ContentSigner signer = csBuilder.build(parClaves.getPrivate());
			csr = p10Builder.build(signer);
			
			if (csr != null)    {
				return csr.getEncoded();
			}
		}
		
		catch (Exception ex)    {
			System.err.println("Excepcion al generar el CSR");
			ex.printStackTrace();
		}
		
		return null;
	}
	
	/*
	 * Una vez recibido el certificado firmado, lo grabará a disco en el dispositivo, para poder usarlo posteriormente en la comunicación
	 * con AWS. 
	 * Además del certificado, le concatena después la clave publica de nuestra CA, para que después funcione el autoregistro y activación 
	 * de certificados en AWS. 
	 */
	public boolean grabarCertificadoFirmado (String certificadoFirmado)    {
		
	    // Cargo el contenido de la clave publica de la CA, que lo concatenaré junto con el certificado firmado.
		String clavePublicaCAEnTexto = null;
		FileInputStream fileInput = null;
	    
	    try		{
			
			fileInput = new FileInputStream(Constantes.certificateCAFile);
			
			byte [] array = new byte[10000];
			int leidos = fileInput.read(array);

			if (leidos >=10000 || leidos <= 0)    {
				System.err.println("Error al leer el fichero de la CA. Leidos: " + leidos);
			}

			clavePublicaCAEnTexto = new String (array, 0, leidos, StandardCharsets.UTF_8);
	    	fileInput.close();	
		}

	    catch (Exception e)		{
			System.err.println("Excepcion al cargar la clave publica de la CA de su fichero");
			e.printStackTrace();
			return false; 
		}
		
	    // Ahora grabamos en el fichero tanto el certificado firmado que nos han devuelto, como la clave publica de la CA, para que luego sea compatible con 
	    // el autoregistro de certificados en AWS. 
	    try    {

	    	byte[] cert = certificadoFirmado.getBytes();
	    	FileOutputStream fos = new FileOutputStream(Constantes.certificadoFirmadoDevice);

	    	fos.write("-----BEGIN CERTIFICATE-----\n".getBytes("UTF-8"));

	    	int lineas = cert.length / 64;
		    for (int i = 0; i < lineas; i++)    {
			    fos.write(cert, i*64, 64);
	    		fos.write ("\n".getBytes());
		    }

	    	if (cert.length % 64 > 0)    {
	    		fos.write(cert, lineas*64, cert.length % 64);
	    	}
		    
		    fos.write("\n-----END CERTIFICATE-----\n".getBytes("UTF-8"));
		    
		    // Después le concateno la clave publica de la CA, ya que será necesaria para que en AWS podamos autoregistrar el certificado. 
		    fos.write(clavePublicaCAEnTexto.getBytes());
		    
		    fos.close();
	    }
		catch (Exception e)		{
			System.err.println("Excepcion al grabar el certificado firmado en el fichero de salida");
		    e.printStackTrace();
		    return false;
		}
	    
	    return true; 
	}

	/**
	 * Graba en fichero el contenido de la Primary Key que hemos generado en formato base64. Realmente no es necesario para conectar con 
	 * AWS, la usaremos si queremos conectar a través de cliente Mosquitto. 
	 */
	private void grabarPrimaryKey (byte[] clavePrivada)    {
		
	    try    {

			byte[] clavePrivadaBase64 = Base64.getEncoder().encode(clavePrivada);

	    	FileOutputStream fos = new FileOutputStream(Constantes.privateKeyCertificadoBase64Device);

	    	fos.write("-----BEGIN RSA PRIVATE KEY-----\n".getBytes("UTF-8"));
	    		    	
	    	int lineas = clavePrivadaBase64.length / 64;
		    for (int i = 0; i < lineas; i++)    {
			    fos.write(clavePrivadaBase64, i*64, 64);
	    		fos.write ("\n".getBytes());
		    }

	    	if (clavePrivadaBase64.length % 64 > 0)    {
	    		fos.write(clavePrivadaBase64, lineas*64, clavePrivadaBase64.length % 64);
	    	}
		    
	    	fos.write("\n-----END RSA PRIVATE KEY-----\n".getBytes("UTF-8"));
		    fos.close();
	    }
		catch (Exception e)		{
			System.err.println("Excepcion al grabar la clave primaria en el fichero");
		    e.printStackTrace();
		}
	}

	/**
	 * Graba en fichero el contenido del CSR generado. Esto realmente no sería necesario, pero nos sirve para validar con un cliente 
	 * mosquitto si el CSR se generó bien. 
	 */
	private void grabarCsr (byte[] csr)    {
		
	    try    {

		    // Lo grabo en el fichero, esto en el servidor no será necesario. 
	    	FileOutputStream fos = new FileOutputStream(Constantes.certificadoCSRDevice);

	    	fos.write("-----BEGIN CERTIFICATE REQUEST-----\n".getBytes("UTF-8"));
	    		    	
	    	int lineas = csr.length / 64;
		    for (int i = 0; i < lineas; i++)    {
			    fos.write(csr, i*64, 64);
	    		fos.write ("\n".getBytes());
		    }

	    	if (csr.length % 64 > 0)    {
	    		fos.write(csr, lineas*64, csr.length % 64);
	    	}
		    
	    	fos.write("\n-----END CERTIFICATE REQUEST-----\n".getBytes("UTF-8"));
		    fos.close();
	    }
		catch (Exception e)		{
			System.err.println("Excepcion al grabar el CSR en el fichero");
		    e.printStackTrace();
		}
	}

	public byte[] getCSR()    {
		
		if (parClaves == null)    {
			return null;
		}
		
		return parClaves.getPublic().getEncoded();
	}

	public KeyPair getParClaves() {
		return parClaves;
	}

	public void setParClaves(KeyPair vParClaves) {
		parClaves = vParClaves;
	}

	public Certificate getCertificadoFirmado() {
		return certificadoFirmado;
	}

	public void setCertificadoFirmado(Certificate vCertificadoFirmado) {
		certificadoFirmado = vCertificadoFirmado;
	}

	public PKCS10CertificationRequest getCsr() {
		return csr;
	}

	public void setCsr(PKCS10CertificationRequest vCsr) {
		csr = vCsr;
	}
}
