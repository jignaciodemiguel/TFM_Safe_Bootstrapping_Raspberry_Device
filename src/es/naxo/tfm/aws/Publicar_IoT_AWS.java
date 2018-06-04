package es.naxo.tfm.aws;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTopic;

import es.naxo.tfm.utils.Constantes;
import es.naxo.tfm.utils.CryptoUtils;
import es.naxo.tfm.utils.CryptoUtils.KeyStorePasswordPair;

public class Publicar_IoT_AWS {

    private static String testTopic = null;
    private final static AWSIotQos TestTopicQos = AWSIotQos.QOS0;
    private static AWSIotMqttClient awsIotClient;

    public static void setClient(AWSIotMqttClient client) {
        awsIotClient = client;
    }

    public static class BlockingPublisher implements Runnable {
        private final AWSIotMqttClient awsIotClient;

        public BlockingPublisher(AWSIotMqttClient awsIotClient) {
            this.awsIotClient = awsIotClient;
        }

        @Override
        public void run() {
            long counter = 1;

            while (true) {
                String payload = "hello from blocking publisher - " + (counter++);
                System.out.println(System.currentTimeMillis() + ": >>> " + payload);
                try {
                    awsIotClient.publish(testTopic, payload);
                } catch (AWSIotException e) {
                    System.out.println(System.currentTimeMillis() + ": publish failed for " + payload);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    System.out.println(System.currentTimeMillis() + ": BlockingPublisher was interrupted");
                    return;
                }
            }
        }
    }

    private static void initClient(String idDevice) {
    	
        String clientEndpoint = "a17jj5x2zjeivl.iot.eu-central-1.amazonaws.com";
        String clientId = "Rasperry_" + idDevice;
        
        testTopic = "$aws/things/" + clientId + "/shadow/update";

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

    public static void enviarTemperaturaContinuo (String idDevice)    {
        
        initClient(idDevice);

        try   {
        	
	        awsIotClient.connect();
	
	        AWSIotTopic topic = new TestTopicListener(testTopic, TestTopicQos);
	        awsIotClient.subscribe(topic, true);
	
	        Thread blockingPublishThread = new Thread(new BlockingPublisher(awsIotClient));
	
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
    
    public static boolean pruebaEnviarTemperatura (String idDevice)   {
            
        initClient(idDevice);

        if (awsIotClient == null)    {
        	return false; 
        }
        
        try   {
        	
        	awsIotClient.connect();

        	AWSIotTopic topic = new TestTopicListener(testTopic, TestTopicQos);
        	awsIotClient.subscribe(topic, true);

        	String payload = "Prueba de envio de mensaje...";
            awsIotClient.publish(testTopic, payload);
        } 
            
        catch (AWSIotException exception) {
            System.err.println("Error al suscribirse / publicar en el Topic de AWS");
            return false; 
        }
        
        return true; 
    }
}
