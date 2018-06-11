package es.naxo.tfm.aws;

import java.util.Random;

import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMqttClient;

public class PublicadorMensaje  implements Runnable {

	private final AWSIotMqttClient awsIotClient;
	private String topic = null; 

    public PublicadorMensaje (AWSIotMqttClient awsIotClient, String vTopic) {
    	this.awsIotClient = awsIotClient;
    	this.topic = vTopic; 
    }

    @Override
    public void run() {
    	
        Random random = new Random();
    	long contador = 1;

        while (true) {
            String payload = "Envio Numero " + contador++ + " - Temperatura del sensor: " + random.nextInt(70);
            System.out.println(payload);
            try {
                awsIotClient.publish (topic, payload);
            } 
            catch (AWSIotException e) {
                System.err.println("Excepcion en la publicacion: " + payload);
                e.printStackTrace();
            }

            try {
                Thread.sleep(5000);
            } 
            catch (InterruptedException e) {
                System.err.println("BlockingPublisher was interrupted");
                return;
            }
        }
    }
}
