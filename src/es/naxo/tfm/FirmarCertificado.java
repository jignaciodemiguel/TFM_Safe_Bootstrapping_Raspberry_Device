package es.naxo.tfm;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import es.naxo.tfm.utils.Cifrado;
import es.naxo.tfm.utils.Constantes;
import es.naxo.tfm.utils.CryptoUtils;

public class FirmarCertificado {
	
	// Clave usada compartida para generar el certificado unico. 
	private static final String claveSecretoCompartido1 = "6849576002387456";
	private static final String claveSecretoCompartido2 = "9472652849608709";

	public static String firmarCertificadoConCA (String csr, String identificadorUnico) 
		throws Exception {
		
		// Desciframos el identificadorUnico
		byte[] descifrado = Base64.getDecoder().decode(identificadorUnico);
		
		//Lo desciframos desde AES, con la clave secreta también compartida. 
		String cadena = Cifrado.descifra(descifrado);
		
		// Eliminamos los secretos compartidos de inicio y final. Lo que nos queda al final es el Serial Number. 
		cadena = cadena.replaceAll(claveSecretoCompartido1, "");
		String serialNumber = cadena.replaceAll(claveSecretoCompartido2, "");
		
		if (validarSerialNumber(serialNumber) == false)    {
			return null; 
		}

		String certificado = firmarCSR(csr);

		return certificado;
	}
	
	/**
	 * A partir de un CSR generado, y de la clave privada de nuestra CA, realiza la firma del certificado. 
	 * @param csrString El CSR, codificado en base64, para poder transmitirlo por HTTP. 
	 * Ayuda para generar el codigo de la firma: http://blog.pulasthi.org/2014/08/signing-certificate-signing-request-csr.html
	 *
	 * @return Devuelve a su vez un String en base64, para poder devolverlo también por HTTP. 
	 */
	public static String firmarCSR (String csrString)   {
	    
		// Cargo clave publica y privada de la CA. Y la variable que almacenará finalmente el certificado firmado.  
    	PrivateKey cakey = null;
    	X509Certificate cacert = null;
    	X509Certificate certificadoFirmado = null;
    	String certificadoFirmadoString = null;

		try		{
	    	cakey = CryptoUtils.loadPrivateKeyFromFile (Constantes.privateKeyCAFile, "RSA");
	    	cacert = (X509Certificate)CryptoUtils.loadCertificateFromFile(Constantes.certificateCAFile);
		}
		catch (Exception e)		{
			System.err.println("Excepcion al cargar la clave privada de la CA o el CSR");
			e.printStackTrace();
			return null; 
		}

	    // Ahora realizamos en si mismo la firma de certificado. 
	    try    {

		    // Cargo el CSR y lo decodifico, que viene en base64 para poder transportarlo. 
		    byte[] decodeado = Base64.getDecoder().decode(csrString); 
		    PKCS10CertificationRequest request = new PKCS10CertificationRequest(decodeado); 

		    //PEMParser pemParser = new PEMParser(new InputStreamReader(new ByteArrayInputStream(decodeado)));
	    	//PKCS10CertificationRequest request = (PKCS10CertificationRequest) pemParser.readObject();
	    	//pemParser.close();

		    // Fecha de inicio y fin de caducidad del certificado. 
		    Date issuedDate = new Date();
		    Date expiryDate = new Date(System.currentTimeMillis() + (730L * 86400000L));   // 2 años desde hoy. 
	
		    // Serial Number aleatorio. 
		    BigInteger serial = new BigInteger(32, new SecureRandom());
	    	
            JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(request);
            
            X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(cacert, serial, issuedDate, expiryDate, jcaRequest.getSubject(), jcaRequest.getPublicKey());
            
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            certificateBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                    extUtils.createAuthorityKeyIdentifier(cacert))
                    .addExtension(Extension.subjectKeyIdentifier, false, extUtils.createSubjectKeyIdentifier(jcaRequest
                            .getPublicKey()))
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(0))
                    .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment))
                    .addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
            
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(cakey);
            certificadoFirmado = new JcaX509CertificateConverter().getCertificate (certificateBuilder.build(signer));
	    }
	    
	    catch (Exception e)		{
	    	System.err.println("Excepcion al firmar el CSR");
	    	e.printStackTrace();
	    	return null; 
	    }
	    
	    // Devolvemos el certificado en base64, para que se pueda transmitir. 
	    try    {
		    byte[] certBase64 = certificadoFirmado.getEncoded();
		    certificadoFirmadoString = Base64.getEncoder().encodeToString(certBase64);
	    }

	    catch (Exception e)		{
			System.err.println("Excepcion al grabar el certificado firmado en el fichero de salida");
		    e.printStackTrace();
		    return null; 
		}
	    
	    return certificadoFirmadoString;
	}
	
	/**
	 * A partir de un CSR generado, y de la clave privada de nuestra CA, realiza la firma del certificado. 
	 * @param csrString El CSR, codificado en base64, para poder transmitirlo por HTTP. 
	 * Ayuda para generar el codigo de la firma: https://stackoverflow.com/questions/7230330/sign-csr-using-bouncy-castle
	 *
	 * @return Devuelve a su vez un String en base64, para poder devolverlo también por HTTP. 
	 */
	public static String firmarCSR_NOVA (String csrString)   {
	    
		// Cargo clave publica y privada de la CA. Y la variable que almacenará finalmente el certificado firmado.  
    	PrivateKey cakey = null;
    	X509Certificate cacert = null;
    	X509Certificate certificadoFirmado = null;
    	String certificadoFirmadoString = null;

		try		{
	    	cakey = CryptoUtils.loadPrivateKeyFromFile (Constantes.privateKeyCAFile, "RSA");
	    	cacert = (X509Certificate)CryptoUtils.loadCertificateFromFile(Constantes.certificateCAFile);
		}
		catch (Exception e)		{
			System.err.println("Excepcion al cargar la clave privada de la CA o el CSR");
			e.printStackTrace();
			return null; 
		}

	    // Ahora realizamos en si mismo la firma de certificado. 
	    try    {
	    	
		    // Cargo el CSR y lo decodifico, que viene en base64 para poder transportarlo. 
		    byte[] decodeado = Base64.getDecoder().decode(csrString); 
		    PKCS10CertificationRequest csr = new PKCS10CertificationRequest(decodeado); 
	
		    AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
		    AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
	
		    AsymmetricKeyParameter foo = PrivateKeyFactory.createKey(cakey.getEncoded());
		    SubjectPublicKeyInfo keyInfo = csr.getSubjectPublicKeyInfo();   
	
		    // Fecha de inicio y fin de caducidad del certificado. 
		    Date from = new Date();
		    Date to = new Date(System.currentTimeMillis() + (730L * 86400000L));   // 2 años desde hoy. 
	
		    // Principal y Serial Number. 
		    X500Name issuer = new X500Name(cacert.getSubjectX500Principal().getName());
		    BigInteger serial = new BigInteger(32, new SecureRandom());
	
		    X509v3CertificateBuilder myCertificateGenerator = new X509v3CertificateBuilder(issuer, serial, from, to, csr.getSubject(), keyInfo);
		    ContentSigner sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(foo);  
		    
		    myCertificateGenerator.addExtension(X509Extension.basicConstraints, false, new BasicConstraints(false));
		    //myCertificateGenerator.addExtension(X509Extension.subjectKeyIdentifier, false, new SubjectKeyIdentifier(cakey.get. .getSubjectPublicKeyInfo()));
		    myCertificateGenerator.addExtension(X509Extension.authorityKeyIdentifier, false, new AuthorityKeyIdentifier(new GeneralNames(new GeneralName(new X509Name(cacert.getSubjectX500Principal().getName()))), cacert.getSerialNumber()));
	
		    X509CertificateHolder holder = myCertificateGenerator.build(sigGen);
		    Certificate eeX509CertificateStructure = holder.toASN1Structure(); 
		    CertificateFactory cf = CertificateFactory.getInstance("X.509");  
	
		    // Read Certificate
		    InputStream is1 = new ByteArrayInputStream(eeX509CertificateStructure.getEncoded());
		    certificadoFirmado = (X509Certificate) cf.generateCertificate(is1);
		    is1.close();
	    }
	    
	    catch (Exception e)		{
	    	System.err.println("Excepcion al firmar el CSR");
	    	e.printStackTrace();
	    	return null; 
	    }
	    
	    // Devolvemos el certificado en base64, para que se pueda transmitir. 
	    try    {
		    byte[] certBase64 = certificadoFirmado.getEncoded();
		    certificadoFirmadoString = Base64.getEncoder().encodeToString(certBase64);
	    }

	    catch (Exception e)		{
			System.err.println("Excepcion al grabar el certificado firmado en el fichero de salida");
		    e.printStackTrace();
		    return null; 
		}
	    
	    return certificadoFirmadoString;
	}

	
	/* Ejecuto la validación sintactica de un campo SerialNumber de Rasperry. 
	 *  - Tiene que existir. 
	 *  - Tener 16 caracteres exactos de tamaño.
	 *  - Cumplir la expresion regular (numeros)
	 */
	public static boolean validarSerialNumber (String serialNumber)    {
		
		if (serialNumber == null || serialNumber.equals(""))   {
			System.err.println ("Error en la validación del SerialNumber. Está vacio: " + serialNumber);
			return false;
		}

	    if (serialNumber.length() != 16)   {
			System.err.println ("Error en la longitud (" + serialNumber.length() + ") del SerialNumber: " + serialNumber);
			return false;	
		}
	    
		Pattern patron = Pattern.compile("^[A-Za-z\\d]{16}$");
	    Matcher busqueda = patron.matcher(serialNumber);
		
	    if (busqueda.matches() == false)   {
			System.err.println("Error en la validación de la expresion regular del SerialNumber: " + serialNumber);
			return false;	
		}
	    
	    // Si he llegado hasta aquí, es que todo fue bien.
	    return true;
	}
}
