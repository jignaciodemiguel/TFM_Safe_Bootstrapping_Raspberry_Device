package es.naxo.tfm.aws;

import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTopic;

import es.naxo.tfm.EjecutarProcesoDevice;

/**
 * This class extends {@link AWSIotTopic} to receive messages from a subscribed
 * topic.
 */
public class SuscriptorListener extends AWSIotTopic {

    public SuscriptorListener(String topic, AWSIotQos qos) {
        super(topic, qos);
    }

    @Override
    public void onMessage(AWSIotMessage message) {
    	
    	// Valido si llega un mensaje de Reset que lo que significa es que hay que reiniciar la conexión y repetir el bootstrapping. 
    	if ("RESET_DEVICE".equals (message.getStringPayload()))    {
    		System.err.println ("\nRecibido mensaje de RESET_DEVICE\n");
    		
    		EjecutarProcesoDevice ejecutar = new EjecutarProcesoDevice();
    		ejecutar.resetearConfiguracion();
    		ejecutar.ejecutarProceso();
    	}
    	else   {
    		System.out.println("Recepcion - " + message.getStringPayload());
    	}
    }
}
