package com.kw.tas.main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import kr.re.kw.ncube.Registration;
import kr.re.kw.ncube.mqttclient.MqttClientKetiPub;
import kr.re.kw.ncube.mqttclient.MqttClientKetiSub;
import kr.re.kw.ncube.mqttclient.MqttClientPingSub;
import kr.re.kw.ncube.mqttclient.MqttClientSensorSub;
import kr.re.kw.ncube.mqttclient.MqttClientSensorCheck;
import kr.re.kw.ncube.resource.AE;
import kr.re.kw.ncube.resource.CSEBase;
import kr.re.kw.ncube.resource.ResourceRepository;

import org.json.JSONObject;

import com.kw.tas.alljoyn.Alljoyn;
import com.kw.tas.alljoyn.AlljoynDevice;
import com.kw.tas.alljoyn.AlljoynDeviceAnounceHandler;
import com.kw.tas.alljoyn.AlljoynDeviceSensingHandler;
import com.kw.tas.alljoyn.AlljoynProcesser;
import com.kw.tas.alljoyn.JoinSessionFailedHandler;
import com.philips.lighting.Controller;
import com.philips.lighting.data.HueProperties;
import com.philips.lighting.hue.sdk.PHHueSDK;

public class MainProcesser{

	public static MqttClientKetiSub requestClient;
	public static MqttClientKetiSub responseClient;
	public static MqttClientKetiPub publishClient;
	public static MqttClientSensorSub tasClient;
	public static MqttClientSensorCheck tasCheckClient;
	public static MqttClientPingSub pingClient;

	public static AlljoynProcesser alljoyn;
	
	public static HashMap<String, AlljoynDevice> devices = new HashMap<>();
	private static ArrayList<AlljoynDevice> list = new ArrayList<>();
	
    static String bridge = "192.168.86.61"; // Philips Hue bridge
    
    static MainProcesser m;
    /* Philipse Hue variable */
    public static Controller controller;
    /* Philipse Hue variable end */

    private static CSEBase hostingCSE = new CSEBase();
	public static AE hostingAE = new AE();
	private static InetAddress ip;
	
    Devices dev = new Devices();
    
	private void configurationFileLoader() throws Exception {
		
		System.out.println("[&CubeThyme] Configuration file loading...");
		
		String jsonString = "";
		
		BufferedReader br = new BufferedReader(new FileReader("./conf.json"));
		while(true) {
			String line = br.readLine();
			if (line == null) break;
			jsonString += line;
		}
		br.close();
				
		JSONObject conf = new JSONObject(jsonString);
		
		JSONObject cseObj = conf.getJSONObject("cse");
		hostingCSE.CSEHostAddress = cseObj.getString("cbhost");
		System.out.println("[&CubeThyme] CSE - cbhost : " + hostingCSE.CSEHostAddress);
		hostingCSE.CSEPort = cseObj.getString("cbport");
		System.out.println("[&CubeThyme] CSE - cbport : " + hostingCSE.CSEPort);
		hostingCSE.CSEName = cseObj.getString("cbname");
		System.out.println("[&CubeThyme] CSE - cbname : " + hostingCSE.CSEName);
		hostingCSE.CSEId = cseObj.getString("cbcseid");
		System.out.println("[&CubeThyme] CSE - cbcseid : " + hostingCSE.CSEId);
		hostingCSE.mqttPort = cseObj.getString("mqttport");
		System.out.println("[&CubeThyme] CSE - mqttPort : " + hostingCSE.mqttPort);
		ResourceRepository.setCSEBaseInfo(hostingCSE);

		JSONObject aeObj = conf.getJSONObject("ae");
		hostingAE.aeId = aeObj.getString("aeid");
		System.out.println("[&CubeThyme] AE - aeId : " + hostingAE.aeId);
		hostingAE.appId = aeObj.getString("appid");
		System.out.println("[&CubeThyme] AE - appid : " + hostingAE.appId);
		hostingAE.appName = aeObj.getString("appname");
		System.out.println("[&CubeThyme] AE - appname : " + hostingAE.appName);
		hostingAE.appPort = aeObj.getString("appport");
		System.out.println("[&CubeThyme] AE - appport : " + hostingAE.appPort);
		hostingAE.bodyType = aeObj.getString("bodytype");
		System.out.println("[&CubeThyme] AE - bodytype : " + hostingAE.bodyType);
		hostingAE.tasPort = aeObj.getString("tasport");
		System.out.println("[&CubeThyme] AE - tasport : " + hostingAE.tasPort);
		ResourceRepository.setAEInfo(hostingAE);

	}    
    
	private void init(String mobiusIP){
    	try{
    		FileWriter fw = new FileWriter("./conf.json");
    		String initSet = 
    			"{\n" +
    			    "\t\"useprotocol\": \"http\",\n" +
    			    "\t\"cse\": {\n" + 
    			        "\t\t\"cbhost\": \"" + mobiusIP + "\",\n" +
    			        "\t\t\"cbport\": \"7579\",\n" +
    			        "\t\t\"cbname\": \"Mobius\",\n" +
    			        "\t\t\"cbcseid\": \"/Mobius\",\n" +
    			        "\t\t\"mqttport\": \"1883\"\n" +
    			    "\t},\n" + 
    			    "\t\"ae\": {\n" + 
    			        "\t\t\"aeid\": \"S\",\n" + 
    			        "\t\t\"appid\": \"0.2.481.1.1\",\n" + 
    			        "\t\t\"appname\": \"kwu-hub\",\n" + 
    			        "\t\t\"appport\": \"9727\",\n" + 
    			        "\t\t\"bodytype\": \"xml\",\n" + 
    			        "\t\t\"tasport\": \"3105\"\n" + 
    			    "\t},\n" +
    			    "\t\"cnt\": [\n" +
    			    "\t],\n" +
    			    "\t\"sub\": [\n" +
    			    "\t]\n" +
    			"}\n";
    		fw.write(initSet);
    		fw.close();
    	}catch(Exception e){
    		
    	}
    	
    }
    
	public static void main(String[] args) throws Exception {
		 m = new MainProcesser();
		
		 Scanner scan = new Scanner(System.in);
		 System.out.print("Please enter the mobius ip: ");
		 String mobiusIP = scan.next();
		 System.out.println("mobius ip: [" + mobiusIP + "]");
		 
		 m.init(mobiusIP);
		 
		/* Philips Hue */
		 PHHueSDK phHueSDK = PHHueSDK.create();
	     HueProperties.loadProperties();  // Load in HueProperties, if first time use a properties file is created.
	     controller = new Controller();

	     phHueSDK.getNotificationManager().registerSDKListener(controller.getListener());
	     if (controller.connectIP(bridge)) {
	    	 System.out.println("Bridge connect success");
	    	 // Philips Hue bridge successfully connected
         }
	     else
	    	 System.out.println("Bridge connect fail");
	     
	     try {
	    	 Thread.sleep(1000); // Wait for bridge setting
		 }catch (Exception e) {
			 e.printStackTrace();
		 }
		/* Philips Hue end */
	     
		Alljoyn alljoynManagement = new Alljoyn();
		alljoynManagement.start();
	     
	     
		 try {
	    	 Thread.sleep(3000); // Wait for bridge setting & Alljoyn init
		 }catch (Exception e) {
			 e.printStackTrace();
		 }
		
		
	    
	    System.out.println("[&CubeThyme] &CubeThyme SW start.......\n");
		
		// Load the &CubeThyme configuration file
		m.configurationFileLoader();

		// Initialize the HTTP server for receiving the notification messages
		System.out.println("[&CubeThyme] &CubeThyme initialize.......\n");
		
		// Registration sequence
		Registration regi = new Registration();
		regi.registrationStart();
		
		System.out.println("[MAIN END]");
		
		 try {
	    	 Thread.sleep(3000); // Wait a moment
		 }catch (Exception e) {
			 e.printStackTrace();
		 }
		 
		DeviceMonitoring devMonitor = new DeviceMonitoring();
		devMonitor.start();
	}

	
	static class AlljoynAnounceRevceiver implements AlljoynDeviceAnounceHandler, JoinSessionFailedHandler{
		
		private final static Logger LOG = Logger.getLogger(AlljoynAnounceRevceiver.class.getName());
		
		@Override
		public void AlljoynDeviceConnected(AlljoynDevice device) {
			// TODO Auto-generated method stub
		}

		@Override
		public void joinSessionFailed() {
			// TODO Auto-generated method stub
			System.out.println("Main-joinSessionFaild() call");
			for(String key : devices.keySet()){
				//devices.get(key).setOnJoinSessionFailedHandler(null);
				devices.get(key).MonitoringStop();
			}
			
			AlljoynProcesser.alljoynDevices.clear();
			
			devices.clear();
			
			alljoyn.StopProcesser();
			alljoyn = new AlljoynProcesser();
			alljoyn.setDeviceSensingListener(new AlljoynSensingReceiver());
			alljoyn.start();
		}
	}
	
	public static class AlljoynSensingReceiver implements AlljoynDeviceSensingHandler{

		private final static Logger LOG = Logger.getLogger(AlljoynSensingReceiver.class.getName());
		
		@Override
		public void SensingDataReceived(AlljoynDevice device, String msg) {
			if(msg != null && msg.length() > 0)
				LOG.log(Level.INFO, "Recevie a sensing data [" + msg + "]");
		}
	}
}
