package es.naxo.tfm.aws;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTopic;

import es.naxo.tfm.utils.Constantes;
import es.naxo.tfm.utils.CryptoUtils;
import es.naxo.tfm.utils.CryptoUtils.KeyStorePasswordPair;

public class Comunicacion_IoT_AWS {

    private static String nombreTopic = null;
    private final static AWSIotQos TestTopicQos = AWSIotQos.QOS0;
    private static AWSIotMqttClient awsIotClient;

    public static void setClient(AWSIotMqttClient client) {
        awsIotClient = client;
    }

    /*  
     * Metodo de inicialización de la conexión con AWS IoT.
     */
    private static void initClient(String idDevice) {
    	
        String clientEndpoint = "a17jj5x2zjeivl.iot.eu-central-1.amazonaws.com";
        String clientId = "Rasperry_" + idDevice;
        
        nombreTopic = "$aws/things/" + clientId + "/shadow/update";

    	if (awsIotClient == null)  {

    		try    {
        		KeyStorePasswordPair pair = CryptoUtils.getKeyStorePasswordPair(Constantes.certificadoFirmadoDevice, Constantes.privateKeyCertificadoDevice, "RSA");
        		awsIotClient = new AWSIotMqttClient(clientEndpoint, clientId, pair.keyStore, pair.keyPassword);
        	}
    		catch (Exception ex)    {
    			System.err.println(" KO: Error en la carga del certificado cliente del dispositivo");
    		}
    	}
    }

    /*
     * Una vez validado que hay conexión con AWS y el certificado es correcto, lanza el proceso en sí de la Rasperry, que es enviar
     * de manera continua la temperatura, y validar mediante suscripción que el mensaje está llegando al Topic. 
     */
    public static void enviarTemperaturaContinuo (String idDevice)    {
        
        initClient(idDevice);

        try   {
        	
	        awsIotClient.connect();
	
	        AWSIotTopic topic = new SuscriptorListener(nombreTopic, TestTopicQos);
	        awsIotClient.subscribe(topic, true);
	
	        Thread blockingPublishThread = new Thread(new PublicadorMensaje (awsIotClient, nombreTopic));
	
	        blockingPublishThread.start();
	        blockingPublishThread.join();
        }

       catch (Exception ex)    {
    	   System.err.println ("Excepcion al lanzar las publicaciones de temperatura");
    	   ex.printStackTrace();
       }
       
       finally   {
    	   try    {
    		   if (awsIotClient != null)    {
    			   awsIotClient.disconnect();
    		   }
    	   }
    	   catch (Exception ex)    {
	    	   System.err.println ("Excepcion al desconectar el cliente");
	    	   ex.printStackTrace();
	       }
       }
    }
    
    /*
     * Realiza una prueba de conexión y publicacion de un mensaje en el Topic de AWS. Para validar inicialmente si hay conexión, 
     * y si el certificado es valido. En caso contrario, lanzará el proceso de Bootstrapping. 
     */
    public static boolean pruebaEnviarTemperatura (String idDevice)   {
            
        initClient(idDevice);

        if (awsIotClient == null)    {
        	return false; 
        }
        
        try   {
        	
        	awsIotClient.connect();

        	String payload = "Prueba de envio de mensaje...";
            awsIotClient.publish(nombreTopic, payload);
        } 
            
        catch (AWSIotException exception) {
            System.err.println("Error al suscribirse / publicar en el Topic de AWS");
            return false; 
        }
        
        return true; 
    }
}
