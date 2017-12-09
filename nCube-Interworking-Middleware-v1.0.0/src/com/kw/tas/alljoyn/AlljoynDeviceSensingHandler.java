package com.kw.tas.alljoyn;

public interface AlljoynDeviceSensingHandler {
	
	void SensingDataReceived(AlljoynDevice device, String msg);
}
