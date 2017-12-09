package com.kw.tas.main;

import kr.re.kw.ncube.mqttclient.ResourceStructure;
import kr.re.kw.ncube.mqttclient.MqttClientKetiPub;
import com.kw.tas.main.Sensors;

public class SensorHealthCheck extends Thread{
	
	@Override
	public void run() {
		while(true){
			try {
//				Sensors.printSensor();
				for(int i =0;i<Sensors.mSensorList.size();i++){
					if(Sensors.mSensorList.get(i).isAlive.equals("?"))
						Sensors.delSensor(Sensors.mSensorList.get(i).name);
				}
				
				for(int i =0;i<Sensors.mSensorList.size();i++)
					Sensors.mSensorList.get(i).isAlive = "?";
				
				MainProcesser.publishClient.publishFullPayload("sensor/req", "isAlive?");
				Thread.sleep(15000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
