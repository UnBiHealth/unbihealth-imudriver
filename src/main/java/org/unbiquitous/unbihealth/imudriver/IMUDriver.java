package org.unbiquitous.unbihealth.imudriver;

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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IMUDriver implements UosEventDriver {
	public static final String DRIVER_NAME = "org.unbiquitous.ubihealth.IMODriver";
	public static final String MOVE_EVENT_KEY = "move";
	public static final String SERIAL_PORT_PROP_KEY = "ubihealth.imodriver.serialport";
	public static final String ARM_IMU_ADDRESS = "3";
	public static final String FOREARM_IMU_ADDRESS = "2";
	public static final String TRUNK_ADDRESS = "1";


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
								port.writeBytes(">".concat(FOREARM_IMU_ADDRESS).concat(",1\n").getBytes());
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
								port.writeBytes(">".concat(FOREARM_IMU_ADDRESS).concat(",165\n").getBytes());
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
								port.writeBytes(">".concat(FOREARM_IMU_ADDRESS).concat(",96\n").getBytes());
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
								port.writeBytes(">".concat(ARM_IMU_ADDRESS).concat(",96\n").getBytes());
								data = null;
								isNull = true;
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
								String s;
								Vector3 dataf = null;
								Vector3 dataa = null;
								Vector3 datat = null;
								boolean isNull = true;

								for (int i = 0; i < 1000; i++) {
//									System.out.println("Forearm...");
									port.writeBytes(">".concat(FOREARM_IMU_ADDRESS).concat(",1\n").getBytes());
									isNull = true;
									while (isNull) {
										s = port.readString();

										try {
											dataf = extract(s);
										} catch (Exception e) {
											continue;
										}
										isNull = false;
//										System.out.println(s);
									}

									isNull = true;
//									System.out.println("Arm...");
									port.writeBytes(">".concat(ARM_IMU_ADDRESS).concat(",1\n").getBytes());
									while (isNull) {
										s = port.readString();

										try {
											dataa = extract(s);
										} catch (Exception e) {
											continue;
										}
										isNull = false;
//										System.out.println(s);
									}

									isNull = true;
//									System.out.println("Arm...");
									port.writeBytes(">".concat(TRUNK_ADDRESS).concat(",1\n").getBytes());
									while (isNull) {
										s = port.readString();

										try {
											datat = extract(s);
										} catch (Exception e) {
											continue;
										}
										isNull = false;
//										System.out.println(s);
									}

									double angXt = dataa.x - datat.x;
									double angYt = dataa.y - datat.y;
									double angZt = dataa.z - datat.z;

									double angXa = dataf.x - dataa.x;
									double angYa = dataf.y - dataa.y;
									double angZa = dataf.z - dataa.z;

									System.out.println(Double.toString(angXt).concat(",").concat(Double.toString(angYt)).concat(",").concat(Double.toString(angZt)).concat(",").concat(Double.toString(angXa)).concat(",").concat(Double.toString(angYa)).concat(",").concat(Double.toString(angZa)).concat(";"));
//									System.out.println(angY);
//									System.out.println(angZ);
								}


								/*System.out.println("Start streaming");
								port.writeBytes(">".concat(FOREARM_IMU_ADDRESS).concat(",85\n").getBytes());
								String s;
								Vector3 data = null;
								*//*for (int i = 0; i < 100; i++) {
									System.out.println("Respostas...");
									s = port.readString();

									try {
										data = extract(s);
									} catch (Exception e) {
										continue;
									}
									//isNull = false;
									System.out.println(Integer.toString(i).concat(": ").concat(s));
								}*//*
								int i = 0;
								while (i < 10) {
									s = port.readString();

									try {
										data = extract(s);
									} catch (Exception e) {
										continue;
									}
									System.out.println("x: ".concat(Double.toString(data.x)));
									System.out.println("y: ".concat(Double.toString(data.y)));
									System.out.println("z: ".concat(Double.toString(data.z)));
									System.out.println("");
									i++;
								}*/

//								port.writeBytes(">".concat(FOREARM_IMU_ADDRESS).concat(",86\n").getBytes());
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
			if(data.length > 3) {
				v.x = Double.parseDouble(data[3]) / Math.PI;
				v.y = Double.parseDouble(data[4]) / Math.PI;
				v.z = Double.parseDouble(data[5]) / Math.PI;
			}
			return v;
		}
	}
}
