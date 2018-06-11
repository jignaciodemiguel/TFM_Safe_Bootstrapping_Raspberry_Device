package es.naxo.tfm.utils;

public class Constantes {

	public static final String rutaKeyStore = "C:\\Users\\Nacho\\OneDrive\\Java\\Proyectos\\UCAM_TFM_Ciberseguridad_Device\\ks\\";
	
	public static final String certificateCAFile = rutaKeyStore + "rootCA_TFM_Ciberseguridad.pem";
	
	public static final String certificadoFirmadoDevice = rutaKeyStore + "certificadoFirmadoDevice_CA.crt"; 
	public static final String certificadoCSRDevice = rutaKeyStore + "certificadoFirmadoDevice_CA.csr"; 
	public static final String privateKeyCertificadoDevice = rutaKeyStore + "certificadoFirmadoDevice_CA.key"; 
	public static final String privateKeyCertificadoBase64Device = rutaKeyStore + "certificadoFirmadoDevice_CA_Base64.key"; 
}
