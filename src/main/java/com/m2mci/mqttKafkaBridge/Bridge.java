package com.m2mci.mqttKafkaBridge;

import java.util.Properties;

import kafka.javaapi.producer.Producer;

import kafka.message.Message;

import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.kohsuke.args4j.CmdLineException;

public class Bridge implements MqttCallback {
	
	private Logger logger = Logger.getLogger(this.getClass().getName());
	
	private MqttAsyncClient mqtt;
	
	private Producer<String, String> kafkaProducer;
	
	private void connect(String serverURI, String clientId, String zkConnect) throws MqttException {
		mqtt = new MqttAsyncClient(serverURI, clientId);
		mqtt.setCallback(this);
		IMqttToken token = mqtt.connect();
		Properties props = new Properties();
		
		props.put("zk.connect", zkConnect);
		
		// FIX one
		props.put("metadata.broker.list", "quickstart.cloudera:9092");
		
		// Fix two
		props.put("serializer.class", "kafka.serializer.StringEncoder"); 
//		props.put("serializer.class", "kafka.serializer.DefaultEncoder"); 
		
		ProducerConfig config = new ProducerConfig(props);
		
		// changed type of message 
		kafkaProducer = new Producer<String, String>(config);
		token.waitForCompletion();
		logger.info("Connected to MQTT and Kafka");
	}

	private void reconnect() throws MqttException {
		IMqttToken token = mqtt.connect();
		token.waitForCompletion();
	}
	
	private void subscribe(String[] mqttTopicFilters) throws MqttException {
		int[] qos = new int[mqttTopicFilters.length];
		for (int i = 0; i < qos.length; ++i) {
			qos[i] = 0;
		}
		mqtt.subscribe(mqttTopicFilters, qos);
	}

	@Override
	public void connectionLost(Throwable cause) {
		logger.warn("Lost connection to MQTT server", cause);
		while (true) {
			try {
				logger.info("Attempting to reconnect to MQTT server");
				reconnect();
				logger.info("Reconnected to MQTT server, resuming");
				return;
			} catch (MqttException e) {
				logger.warn("Reconnect failed, retrying in 10 seconds", e);
			}
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
			}
		}
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		// TODO Auto-generated method stub
		
	}



	/**
	 * @param args
	 */
	public static void main(String[] args) {
		CommandLineParser parser = null;
		try {
			parser = new CommandLineParser();
			parser.parse(args);
			Bridge bridge = new Bridge();
			bridge.connect(parser.getServerURI(), parser.getClientId(), parser.getZkConnect());
			bridge.subscribe(parser.getMqttTopicFilters());
		} catch (MqttException e) {
			e.printStackTrace(System.err);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			parser.printUsage(System.err);
		}
	}
	
	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		byte[] payload = message.getPayload();
		
		System.out.println("        Message: " + new String(payload) );
		System.out.println("          Topic: " + topic );
			
		KeyedMessage<String, String> data = new KeyedMessage<String, String>(topic, new String(payload));
		kafkaProducer.send(data);
	}
	
}
