package es.naxo.tfm.utils;

import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Clase que realiza el cifrado o descifrado basado en AES. 
 * @author Nacho
 *
 */
public class Cifrado {
	
	private static final String claveCifrado = "Ciph3r_Comm0n";

	/**
	 * Realiza el cifrado de la cadena recibida. 
	 * @param sinCifrar
	 * @return
	 * @throws Exception
	 */
	public static byte[] cifra(String sinCifrar)   {

		byte[] cifrado = null; 
		
		try   {

			final byte[] bytes = sinCifrar.getBytes("UTF-8");
			final Cipher aes = obtieneCipher(true);
			cifrado = aes.doFinal(bytes);
		}
		
		catch (Exception ex)    {
			System.err.println("Excepcion al cifrar el identificador Unico: " + sinCifrar);
			ex.printStackTrace();
		}
		
		return cifrado;
	}

	/**
	 * Realiza el descifrado de la cadena recibida. 
	 * @param cifrado
	 * @return
	 * @throws Exception
	 */
	public static String descifra(byte[] cifrado)   {

		String sinCifrar = null; 
		
		try   {

			final Cipher aes = obtieneCipher(false);
			final byte[] bytes = aes.doFinal(cifrado);
			sinCifrar = new String(bytes, "UTF-8");
		}
		
		catch (Exception ex)    {
			System.err.println("Excepcion al descifrar el identificador Unico: " + cifrado);
			ex.printStackTrace();
		}
		
		return sinCifrar;
	}

	private static Cipher obtieneCipher(boolean paraCifrar) throws Exception   {
		
		final MessageDigest digest = MessageDigest.getInstance("SHA");
		digest.update(claveCifrado.getBytes("UTF-8"));
		final SecretKeySpec key = new SecretKeySpec(digest.digest(), 0, 16, "AES");

		final Cipher aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
		if (paraCifrar) {
			aes.init(Cipher.ENCRYPT_MODE, key);
		} else {
			aes.init(Cipher.DECRYPT_MODE, key);
		}

		return aes;
	}
}
