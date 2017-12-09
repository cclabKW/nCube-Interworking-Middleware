package com.kw.tas.alljoyn;


import com.kw.tas.main.MainProcesser;
import com.kw.tas.main.MainProcesser.AlljoynSensingReceiver;

public class Alljoyn extends Thread{
	

	@Override
	public void run() {
		MainProcesser.alljoyn = new AlljoynProcesser();
		MainProcesser.alljoyn.setDeviceSensingListener(new AlljoynSensingReceiver());
		
		MainProcesser.alljoyn.start();
	}
}
