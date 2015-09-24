package org.unbiquitous.unbihealth.imudriver;

import org.unbiquitous.uos.core.InitialProperties;
import org.unbiquitous.uos.core.UOS;
import org.unbiquitous.uos.core.adaptabitilyEngine.Gateway;
import org.unbiquitous.uos.core.adaptabitilyEngine.ServiceCallException;
import org.unbiquitous.uos.core.messageEngine.messages.Call;
import org.unbiquitous.uos.core.messageEngine.messages.Response;
import org.unbiquitous.uos.network.socket.connectionManager.TCPConnectionManager;



public class Starter {
	public static void main(String[] args) {
		//UOSLogging.setLevel(Level.ALL);
		UOS uos = new UOS();
		InitialProperties initProps = new InitialProperties();
		initProps.addConnectionManager(TCPConnectionManager.class);
		initProps.addDriver(IMUDriver.class);
//		initProps.put(IMUDriver.SERIAL_PORT_PROP_KEY, "/dev/tty.usbmodemfa131");
		initProps.put(IMUDriver.SERIAL_PORT_PROP_KEY, "COM6");
		uos.start(initProps);
		Gateway g = uos.getGateway();
		Call call = new Call();
		call.setService("tare");
		call.setService("startStreaming");
		call.setDriver(IMUDriver.DRIVER_NAME);
		try {
			Response r = g.callService(g.getCurrentDevice(), call);
			System.out.println(r);
//			call.setService("startStreaming");
//			r = g.callService(g.getCurrentDevice(), call);
			/*call.setService("calibrate");
			r = g.callService(g.getCurrentDevice(), call);
			System.out.println(r);
			call.setService("tare");
			r = g.callService(g.getCurrentDevice(), call);
			System.out.println(r);*/
		} catch (ServiceCallException e) {
			e.printStackTrace();
		}
		uos.stop();
	}
}
