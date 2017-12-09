package com.kw.tas.main;

import kr.re.kw.ncube.mqttclient.ResourceStructure;

import com.kw.tas.alljoyn.AlljoynDevice;
import com.kw.tas.alljoyn.AlljoynProcesser;
import com.kw.tas.alljoyn.PowertechAlljoynPlug;
import com.kw.tas.main.MainProcesser.AlljoynSensingReceiver;

public class DeviceMonitoring extends Thread{
	
	@Override
	public void run() {
		while(true){
			try {
				// Every 1 minute, updates target device information
				for(int i =0;i<Devices.mDeviceList.size();i++){
		    		if(Devices.mDeviceList.get(i).deviceModel.equals("Smart Plug")){
		    			for(AlljoynDevice dev: MainProcesser.alljoyn.alldev){ 
							String Property = ((PowertechAlljoynPlug)dev).GetProperties();
							ResourceStructure.updateContentInstance(dev.GetDeviceName(), "Status" ,Property);
		    			}
		    		}
		    	}
				Thread.sleep(60000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
