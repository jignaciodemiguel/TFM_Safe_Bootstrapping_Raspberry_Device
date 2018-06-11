package es.naxo.tfm.aws;

import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTopic;

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
        System.out.println("Recepcion - " + message.getStringPayload());
    }
}
