package es.naxo.tfm.aws;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTopic;

import es.naxo.tfm.utils.Constantes;
import es.naxo.tfm.utils.CryptoUtils;
import es.naxo.tfm.utils.CryptoUtils.KeyStorePasswordPair;
import es.naxo.tfm.utils.Log;

public class Comunicacion_IoT_AWS {

    private String nombreTopic = null;
    private String nombreTopicAlertas = null;
    
    private final static AWSIotQos TestTopicQos = AWSIotQos.QOS0;
    private AWSIotMqttClient awsIotClient;

    public void setClient(AWSIotMqttClient client) {
        awsIotClient = client;
    }

    /*  
     * Metodo de inicializaci�n de la conexi�n con AWS IoT.
     */
    private void initClient(String idDevice) {
    	
        String clientEndpoint = "a17jj5x2zjeivl.iot.eu-central-1.amazonaws.com";
        String clientId = "Rasperry_" + idDevice;
        
        nombreTopic = "$aws/things/" + clientId + "/comunicarTemperatura";
        nombreTopicAlertas = "$aws/things/" + clientId + "/alertas";

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
     * Una vez validado que hay conexi�n con AWS y el certificado es correcto, lanza el proceso en s� de la Rasperry, que es enviar
     * de manera continua la temperatura, y validar mediante suscripci�n que el mensaje est� llegando al Topic. 
     */
    public void enviarTemperaturaContinuo (String idDevice)    {
        
        initClient(idDevice);

        try   {
        	
	        awsIotClient.connect();
	
	        //AWSIotTopic topic = new SuscriptorListener(nombreTopic, TestTopicQos);
	        Log.escribirLogPuntosSinLinea("Suscribiendo dispositivo a Topic de alertas: '" + nombreTopicAlertas + "'");

	        AWSIotTopic topicAlertas = new SuscriptorListener(nombreTopicAlertas, TestTopicQos);
	        awsIotClient.subscribe(topicAlertas, true);
	        
	        Log.escribirLog("OK");
	        
	        Log.escribirLogPuntosSinLinea("Iniciando env�os de temperatura a Topic: '" + nombreTopic + "'");

	        Thread blockingPublishThread = new Thread(new PublicadorMensaje (awsIotClient, nombreTopic));

	        Log.escribirLog("OK");

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
     * Realiza una prueba de conexi�n y publicacion de un mensaje en el Topic de AWS. Para validar inicialmente si hay conexi�n, 
     * y si el certificado es valido. En caso contrario, lanzar� el proceso de Bootstrapping. 
     */
    public boolean pruebaEnviarTemperatura (String idDevice)   {
            
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

        return true; 
    }
}
