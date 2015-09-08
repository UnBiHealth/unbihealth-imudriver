package org.unbiquitous.unbihealth.imudriver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

import jssc.SerialPort;
import jssc.SerialPortException;

import org.unbiquitous.unbihealth.core.types.Vector3;
import org.unbiquitous.uos.core.InitialProperties;
import org.unbiquitous.uos.core.UOSLogging;
import org.unbiquitous.uos.core.adaptabitilyEngine.Gateway;
import org.unbiquitous.uos.core.adaptabitilyEngine.NotifyException;
import org.unbiquitous.uos.core.applicationManager.CallContext;
import org.unbiquitous.uos.core.driverManager.UosEventDriver;
import org.unbiquitous.uos.core.messageEngine.dataType.UpDevice;
import org.unbiquitous.uos.core.messageEngine.dataType.UpDriver;
import org.unbiquitous.uos.core.messageEngine.dataType.UpNetworkInterface;
import org.unbiquitous.uos.core.messageEngine.messages.Call;
import org.unbiquitous.uos.core.messageEngine.messages.Notify;
import org.unbiquitous.uos.core.messageEngine.messages.Response;
import org.unbiquitous.uos.core.network.model.NetworkDevice;

public class IMUDriver implements UosEventDriver {
	public static final String DRIVER_NAME = "org.unbiquitous.ubihealth.IMODriver";
	public static final String MOVE_EVENT_KEY = "move";
	public static final String SERIAL_PORT_PROP_KEY = "ubihealth.imodriver.serialport";
	public static final String ARM_IMU_ADDRESS = "0";
	public static final String FOREARM_IMU_ADDRESS = "1";


	private static final UpDriver _driver = new UpDriver(DRIVER_NAME);
	{
		_driver.addEvent(MOVE_EVENT_KEY);
	}
	private static Logger logger = UOSLogging.getLogger();

	private class RequestData {
		public UUID id;
		public Call call;
		public Response response;
		public CallContext context;

		public RequestData(UUID id, Call call, Response response, CallContext context) {
			this.id = id;
			this.call = call;
			this.response = response;
			this.context = context;
		}
	}

	private Gateway gateway;
	private InitialProperties initProps;
	private Queue<RequestData> requestQueue = new ConcurrentLinkedDeque<>();
	private Map<UUID, RequestData> responses = new ConcurrentHashMap<>();
	private HashMap<UpNetworkInterface, UpDevice> listeners = new HashMap<UpNetworkInterface, UpDevice>();
	private boolean running = false;
	private SerialPort port;

	public UpDriver getDriver() {
		return _driver;
	}

	public List<UpDriver> getParent() {
		return null;
	}

	public void init(Gateway _gateway, InitialProperties _initProps, String id) {
		this.gateway = _gateway;
		this.initProps = _initProps;

		new SerialPortThread().start();
	}

	public void destroy() {
		running = false;
	}

	@Override
	public void registerListener(Call call, Response response, CallContext context) {
		logger.info(DRIVER_NAME + ": registerListener");

		UpNetworkInterface uni = getNetworkInterface(context);

		if (!listeners.containsKey(uni))
			listeners.put(uni, context.getCallerDevice());
	}

	@Override
	public void unregisterListener(Call call, Response response, CallContext context) {
		logger.info(DRIVER_NAME + ": unregisterListener");

		listeners.remove(getNetworkInterface(context));
	}

	public void calibrate(Call call, Response response, CallContext context) {
		RequestData request = new RequestData(UUID.randomUUID(), call, response, context);
		requestQueue.add(request);
		while (!responses.containsKey(request.id))
			Thread.yield();
		responses.remove(request.id);
	}

	public void getEulerAngles(Call call, Response response, CallContext context) {
		RequestData request = new RequestData(UUID.randomUUID(), call, response, context);
		requestQueue.add(request);
		while (!responses.containsKey(request.id))
			Thread.yield();
		responses.remove(request.id);
	}

	public void startStreaming(Call call, Response response, CallContext context) {
		RequestData request = new RequestData(UUID.randomUUID(), call, response, context);
		requestQueue.add(request);
		while (!responses.containsKey(request.id))
			Thread.yield();
		responses.remove(request.id);
	}

	public void tare(Call call, Response response, CallContext context) {
		RequestData request = new RequestData(UUID.randomUUID(), call, response, context);
		requestQueue.add(request);
		while (!responses.containsKey(request.id))
			Thread.yield();
		responses.remove(request.id);
	}

	private static UpNetworkInterface getNetworkInterface(CallContext context) {
		NetworkDevice networkDevice = context.getCallerNetworkDevice();
		String host = networkDevice.getNetworkDeviceName().split(":")[1];
		return new UpNetworkInterface(networkDevice.getNetworkDeviceType(), host);
	}

	private void doNotify(Notify n) throws NotifyException {
		System.out.println(n.toString());
		for (UpDevice device : listeners.values()) {
			gateway.notify(n, device);
		}
	}

	private class SerialPortThread extends Thread {
		private Vector3 lastData = new Vector3(0, 0, 0);
		private long lastT = System.currentTimeMillis();

		@Override
		public void run() {
			try {
				port = new SerialPort(initProps.getString(SERIAL_PORT_PROP_KEY));
				if (port.openPort()) {
					port.setParams(115200, 8, 1, 0);
					running = true;

					while (running) {
						if (!requestQueue.isEmpty()) {
							RequestData nextRequest = requestQueue.poll();
							if (nextRequest.call.getService().equals("getEulerAngles")) {
								port.writeBytes(">".concat(ARM_IMU_ADDRESS).concat(",1\n").getBytes());
								String s;
								Vector3 data = null;
								boolean isNull = true;
								while (isNull) {
									s = port.readString();

									try {
										data = extract(s);
									} catch (Exception e) {
										continue;
									}
									isNull = false;
									System.out.println(s);
								}
								Vector3 ds = data.subtract(lastData);
								nextRequest.response.addParameter("ANGLE_X",ds.x);
								nextRequest.response.addParameter("ANGLE_Y",ds.y);
								nextRequest.response.addParameter("ANGLE_Z", ds.z);
								nextRequest.response.setError(null);
								//System.out.println(nextRequest.response);
								responses.put(nextRequest.id, nextRequest);
							}
							else if (nextRequest.call.getService().equals("calibrate")) {
								port.writeBytes(">".concat(ARM_IMU_ADDRESS).concat(",165\n").getBytes());
								String s;
								Vector3 data = null;
								boolean isNull = true;
								while (isNull) {
									s = port.readString();

									try {
										data = extract(s);
									} catch (Exception e) {
										continue;
									}
									isNull = false;
									System.out.println(s);
								}
								responses.put(nextRequest.id, nextRequest);
							}
							else if (nextRequest.call.getService().equals("tare")) {
								port.writeBytes(">".concat(ARM_IMU_ADDRESS).concat(",96\n").getBytes());
								String s;
								Vector3 data = null;
								boolean isNull = true;
								while (isNull) {
									s = port.readString();

									try {
										data = extract(s);
									} catch (Exception e) {
										continue;
									}
									isNull = false;
									System.out.println(s);
								}
								responses.put(nextRequest.id, nextRequest);
							}
							else if (nextRequest.call.getService().equals("startStreaming")) {
								port.writeBytes(">".concat(ARM_IMU_ADDRESS).concat(",85\n").getBytes());
								String s;
								Vector3 data = null;
								boolean isNull = true;
								while (isNull) {
									s = port.readString();

									try {
										data = extract(s);
									} catch (Exception e) {
										continue;
									}
									//isNull = false;
									System.out.println(s);
								}
								responses.put(nextRequest.id, nextRequest);
							}
						}


						/*port.writeBytes(">1,1\n".getBytes());
						Thread.sleep(0);
						String s;
						Vector3 data = null;
						boolean isNull = true;
						while (isNull) {
							s = port.readString();

							try {
								data = extract(s);
							} catch (Exception e) {
								continue;
							}
							isNull = false;
							System.out.println(s);
						}
						Vector3 ds = data.subtract(lastData);

						if (ds.sqrMagnitude() > 0.1) {
							lastData = data;

							Notify n = new Notify("move", DRIVER_NAME, null);
							n.addParameter("dx", ds.x);
							n.addParameter("dy", ds.y);
							n.addParameter("dz", ds.z);
							try {
								doNotify(n);
							} catch (NotifyException e) {
								logger.log(Level.SEVERE, "failed to notify move event", e);
							}
						}
						long t = System.currentTimeMillis();
						System.out.println("Time: " + (t - lastT));
						lastT = t;*/
					}
				} else {
					logger.log(Level.SEVERE, "Port did not open");
				}
			} catch (SerialPortException e) {
				logger.log(Level.SEVERE, "serial port failure", e);
			}
		}

		private Vector3 extract(String response) {
			String[] data = response.split(",");
			Vector3 v = new Vector3();
			v.x = Double.parseDouble(data[0]) / Math.PI;
			v.y = Double.parseDouble(data[1]) / Math.PI;
			v.z = Double.parseDouble(data[2]) / Math.PI;
			return v;
		}
	}
}
