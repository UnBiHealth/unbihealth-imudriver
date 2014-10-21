package org.unbiquitous.unbihealth.imudriver;

import org.unbiquitous.uos.core.InitialProperties;
import org.unbiquitous.uos.core.UOS;

public class Starter {
	public static void main(String[] args) {
		UOS uos = new UOS();
		InitialProperties initProps = new InitialProperties();
		initProps.addDriver(IMUDriver.class);
		initProps.put(IMUDriver.SERIAL_PORT_PROP_KEY, "/dev/tty.usbmodemfa131");
		uos.start(initProps);
	}
}
