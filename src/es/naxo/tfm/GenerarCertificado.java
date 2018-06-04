package es.naxo.tfm;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import es.naxo.tfm.aws.PrivateKeyReader;
import es.naxo.tfm.utils.Constantes;
import es.naxo.tfm.utils.Log;

import javax.security.auth.x500.X500Principal;

/**
 * Clase que concentra los metodos para crear el certificado que usaremos para conectar con AWS una vez establecida la primera conexión. 
 * @author Nacho
 *
 */
public class GenerarCertificado    {


	// Variable que almacenará el par de claves privada y pública (CSR) iniciales. 
	public KeyPair parClaves = null; 
	private PKCS10CertificationRequest csr = null; 
	private Certificate certificadoFirmado = null;
	
	public GenerarCertificado() {}
	
	/**
	 * Genera un nuevo par de claves privada y pública, que usaremos como CSR para firmar
	 * Almacena también la clave privada en un fichero en el device, para usar para la conexión contra AWS.
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

		try    {

			// Una vez generados el par de claves, almacenamos en fichero la clave privada, pues la necesitaremos posteriormente para acceder a AWS cuando tengamos ya el certificado firmado.
			FileOutputStream out = new FileOutputStream(Constantes.privateKeyCertificadoDevice);
			out.write(parClaves.getPrivate().getEncoded());
			out.close();

			byte[] clavePrivada = parClaves.getPrivate().getEncoded();
			byte[] clavePrivadaBase64 = Base64.getEncoder().encode(clavePrivada);
			
			grabarPrimaryKey(clavePrivadaBase64);
		}
		
		catch (Exception ex)    {
			System.err.println("Excepcion al generar la clave privada");
			ex.printStackTrace();
		}
	
	}
	
	public byte[] generarCSR (String idDevice)    {
		
		Log.escribirLogPuntos("  - Generando clave primaria");
		generarParClaves();		

		Log.escribirLogPuntos("  - Generando CSR de firma");
		
		try    {
			
			// Generamos el CN con el IdDevice, porque luego AWS valida que el IdDevice coincida con el CN del certificado del dispositivo. 
			X500Principal x500Principal = new X500Principal("CN=Rasperry_" + idDevice);
			PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(x500Principal, parClaves.getPublic());
	
			JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withRSA");
			ContentSigner signer = csBuilder.build(parClaves.getPrivate());
			csr = p10Builder.build(signer);
			
			grabarCsr (Base64.getEncoder().encode(csr.getEncoded()));
			
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
	
	public boolean grabarCertificadoFirmado (String certificadoFirmado)    {
		
	    // Cargo el contenido de la clave publica, que lo concatenaré junto con el certificado firmado.
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

		    // Lo grabo en el fichero, esto en el servidor no será necesario. 
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
	 * Graba en fichero el contenido de la Primary Key que hemos generado. La usaremos para conectar con AWS.
	 * @param primaryKey
	 */
	private void grabarPrimaryKey (byte[] primaryKey)    {
		
	    try    {

		    // Lo grabo en el fichero, esto en el servidor no será necesario. 
	    	FileOutputStream fos = new FileOutputStream(Constantes.privateKeyCertificadoBase64Device);

	    	fos.write("-----BEGIN RSA PRIVATE KEY-----\n".getBytes("UTF-8"));
	    	//fos.write(primaryKey, 0, primaryKey.length);
	    		    	
	    	int lineas = primaryKey.length / 64;
		    for (int i = 0; i < lineas; i++)    {
			    fos.write(primaryKey, i*64, 64);
	    		fos.write ("\n".getBytes());
		    }

	    	if (primaryKey.length % 64 > 0)    {
	    		fos.write(primaryKey, lineas*64, primaryKey.length % 64);
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
	 * Graba en fichero el contenido del CSR generado. 
	 * @param csr
	 */
	private void grabarCsr (byte[] csr)    {
		
	    try    {

		    // Lo grabo en el fichero, esto en el servidor no será necesario. 
	    	FileOutputStream fos = new FileOutputStream(Constantes.certificadoCSRDevice);

	    	fos.write("-----BEGIN CERTIFICATE REQUEST-----\n".getBytes("UTF-8"));
	    	//fos.write(primaryKey, 0, primaryKey.length);
	    		    	
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

	
	/** 
	 * Consulta si ya existe KeyStore creado en el dispositivo, y si están ya las credenciales dadas de alta (certificado firmado). 
	 */
	public boolean validarExisteKeyStore()    {
	
		//FileInputStream fileInputStream = new FileInputStream (rutaKeyStore);
		
		return false;
	}
	
	public void almacenarCertificadoFirmado()     {
		
		FileOutputStream fos = null;
		if (certificadoFirmado == null)    {
			System.err.println("El certificado no es valido. No se pueden registrar las credenciales");
			return; 
		}
		
		try   {
        
			fos = new FileOutputStream(Constantes.rutaKeyStore);
			Certificate[] chain = {
					certificadoFirmado
			};

			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, null);
			keyStore.setKeyEntry("main", parClaves.getPrivate(), Constantes.claveKeyStore.toCharArray(), chain);
			keyStore.store(fos, Constantes.claveKeyStore.toCharArray());
        } 
		
		catch (IOException | GeneralSecurityException e) {
			System.err.println("Excepcion al registrar el certificado firmado.");
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
