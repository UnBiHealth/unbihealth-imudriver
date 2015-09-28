package org.unbiquitous.unbihealth.imudriver;

import java.util.logging.Level;

import org.unbiquitous.uos.core.UOS;
import org.unbiquitous.uos.core.UOSLogging;
import org.unbiquitous.uos.core.adaptabitilyEngine.Gateway;
import org.unbiquitous.uos.core.adaptabitilyEngine.ServiceCallException;
import org.unbiquitous.uos.core.adaptabitilyEngine.UosEventListener;
import org.unbiquitous.uos.core.messageEngine.dataType.UpDevice;
import org.unbiquitous.uos.core.messageEngine.messages.Call;
import org.unbiquitous.uos.core.messageEngine.messages.Notify;
import org.unbiquitous.uos.core.messageEngine.messages.Response;
import org.unbiquitous.uos.network.socket.TCPProperties;
import org.unbiquitous.uos.network.socket.connectionManager.TCPConnectionManager;

public class Starter {
	public static void main(String[] args) throws Exception {
		UOSLogging.setLevel(Level.ALL);
		UOS uos = new UOS();
		TCPProperties initProps = new TCPProperties();
		initProps.setPassivePortRange(4242, 4244);
		initProps.setPort(4242);
		uos.start(initProps);

		Gateway g = uos.getGateway();
		
		// registers to receive the notify events
		g.register(new UosEventListener() {
			@Override
			public void handleEvent(Notify event) {
				System.out.println("handle notify: " + event);
			}
		}, null, IMUDriver.DRIVER_NAME, "update");
		
		// makes a test call
		Call call = new Call(IMUDriver.DRIVER_NAME, "getEulerAngles");
		try {
			UpDevice device = new UpDevice("local-python").addNetworkInterface("127.0.0.1:4243", "Ethernet:TCP");
			Response r = g.callService(device, call);
			System.out.println(r);
		} catch (ServiceCallException e) {
			e.printStackTrace();
		}
		if (true)
			return;

		UOSLogging.setLevel(Level.OFF);
		uos = new UOS();
		initProps = new TCPProperties();
		initProps.addConnectionManager(TCPConnectionManager.class);
		initProps.addDriver(IMUDriver.class);
		initProps.put(IMUDriver.SERIAL_PORT_PROP_KEY, "/dev/tty.usbmodemfa131");
		uos.start(initProps);
		g = uos.getGateway();
		call = new Call();
		//		call.setService("getEulerAngles");
		call.setService("tare");
		call.setDriver(IMUDriver.DRIVER_NAME);
		try {
			Response r = g.callService(g.getCurrentDevice(), call);
			System.out.println(r);
			call.setService("startStreaming");
			r = g.callService(g.getCurrentDevice(), call);
			/*
			 * call.setService("calibrate"); r =
			 * g.callService(g.getCurrentDevice(), call); System.out.println(r);
			 * call.setService("tare"); r = g.callService(g.getCurrentDevice(),
			 * call); System.out.println(r);
			 */
		} catch (ServiceCallException e) {
			e.printStackTrace();
		}
		uos.stop();
	}
}
