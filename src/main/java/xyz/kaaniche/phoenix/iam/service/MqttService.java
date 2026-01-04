package xyz.kaaniche.phoenix.iam.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

import java.util.logging.Logger;

@ApplicationScoped
public class MqttService {
    
    private static final Logger LOGGER = Logger.getLogger(MqttService.class.getName());
    
    @ConfigProperty(name = "mqtt.broker.url", defaultValue = "tcp://localhost:1883")
    String brokerUrl;
    
    @ConfigProperty(name = "mqtt.client.id", defaultValue = "phoenix-iam")
    String clientId;
    
    @ConfigProperty(name = "mqtt.topic.prefix", defaultValue = "phoenix/iam/")
    String topicPrefix;
    
    private MqttClient mqttClient;

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(brokerUrl, clientId);
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setAutomaticReconnect(true);
            options.setCleanStart(true);
            mqttClient.connect(options);
            LOGGER.info("MQTT client connected to " + brokerUrl);
        } catch (MqttException e) {
            LOGGER.warning("Failed to connect to MQTT broker: " + e.getMessage());
        }
    }

    public void publishUserEvent(String event, String username, String details) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            LOGGER.warning("MQTT client not connected, skipping event publication");
            return;
        }
        
        String topic = topicPrefix + event;
        String payload = String.format("{\"username\":\"%s\",\"event\":\"%s\",\"details\":\"%s\",\"timestamp\":%d}",
                username, event, details, System.currentTimeMillis());
        
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            mqttClient.publish(topic, message);
            LOGGER.info("Published event to " + topic);
        } catch (MqttException e) {
            LOGGER.warning("Failed to publish MQTT message: " + e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                LOGGER.info("MQTT client disconnected");
            } catch (MqttException e) {
                LOGGER.warning("Error disconnecting MQTT client: " + e.getMessage());
            }
        }
    }
}
