package kr.re.kw.ncube.mqttclient;

import kr.re.kw.ncube.Registration;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.kw.tas.main.MainProcesser;

public class MqttClientSensorSub extends MqttClientKetiSub{
	
	public MqttClientSensorSub(String serverUrl){
		super(serverUrl);
	}
	
	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		
		String payload = byteArrayToString(message.getPayload());
		Translation.containerParse(payload);
		MainProcesser.publishClient.publishFullPayload("/oneM2M/req/" + Registration.ae.aeId + "/Mobius/xml", payload);
	}
	
}
