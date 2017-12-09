package kr.re.kw.ncube.mqttclient;

import java.io.StringReader;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import kr.re.kw.ncube.httpclient.HttpClientRequest;
import kr.re.kw.ncube.resource.Container;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.kw.tas.main.DeviceFunction;
import com.kw.tas.main.Devices;
import com.kw.tas.main.MainProcesser;

public class Translation { // Translator
	
	public static ArrayList<String> contentInstanceParse(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputSource xmlSource = new InputSource();
		xmlSource.setCharacterStream(new StringReader(xml));
		Document document = builder.parse(xmlSource);
		
		String operator = "";
		String targetDevice = "";
		String targetFunction = "";
		String content = "";
		
		Node opNode = document.getElementsByTagName("op").item(0).getChildNodes().item(0);
		operator = opNode.getNodeValue();
		
		if(operator.equals("1")){
			Node targetNode = document.getElementsByTagName("to").item(0).getChildNodes().item(0);
			String[] DevicePath = targetNode.getNodeValue().split("/");
			
			if(DevicePath.length == 5){
				targetDevice = DevicePath[DevicePath.length-2];
				targetFunction = DevicePath[DevicePath.length-1];
			}
			else
				targetDevice = DevicePath[DevicePath.length-1];
			
			NodeList conNodeList = document.getElementsByTagName("con");
			if (conNodeList.getLength() > 0 && conNodeList.item(0).getChildNodes().getLength() > 0) {
				Node conNode = conNodeList.item(0).getChildNodes().item(0);
				content = conNode.getNodeValue();
			}
		}
		ArrayList<String> returnArray = new ArrayList<String>();
		
		returnArray.add(operator);
		returnArray.add(targetDevice);
		returnArray.add(targetFunction);
		returnArray.add(content);
		
		return returnArray;
	}
	
	public static void containerParse(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		InputSource xmlSource = new InputSource();
		xmlSource.setCharacterStream(new StringReader(xml.trim()));
		Document document = builder.parse(xmlSource);
		
		String operator = "";
		String label = "";
		String container = "";
		
		Node opNode = document.getElementsByTagName("op").item(0).getChildNodes().item(0);
		operator = opNode.getNodeValue();
		
		if(operator.equals("1")){
			NodeList conNodeList = document.getElementsByTagName("lbl");
			// make container resource request 
			if (conNodeList.getLength() > 0 && conNodeList.item(0).getChildNodes().getLength() > 0) {
				Node targetNode = conNodeList.item(0).getChildNodes().item(0);
				label = targetNode.getNodeValue();
				
				String[] tmp = xml.replaceAll(" ","").split("rn=\"");
				container = tmp[1].split("\">")[0];
				
				Devices.setDevice(container, label, DeviceFunction.Sensor);
				System.out.println("[Make container resource]: " + container);	
			}
		}
	}
	
	public static void makeResourceOrientedArchitecture(){
		for (int i = 0; i < Devices.mDeviceList.size(); i++) {
			/* Resource structure */
			Devices.mDeviceList.get(i).parentPath = "/" + MainProcesser.hostingAE.appName;
			Devices.mDeviceList.get(i).cntName = Devices.mDeviceList.get(i).deviceName.replaceAll(" ","-");
			
		}
	}
	
}
