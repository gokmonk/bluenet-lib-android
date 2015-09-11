package nl.dobots.bluenet.extended;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import nl.dobots.bluenet.BleBase;
import nl.dobots.bluenet.BleDeviceConnectionState;
import nl.dobots.bluenet.BleDeviceFilter;
import nl.dobots.bluenet.BleTypes;
import nl.dobots.bluenet.BleUtils;
import nl.dobots.bluenet.callbacks.IBaseCallback;
import nl.dobots.bluenet.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.callbacks.IDataCallback;
import nl.dobots.bluenet.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.callbacks.IExecuteCallback;
import nl.dobots.bluenet.callbacks.IIntegerCallback;
import nl.dobots.bluenet.callbacks.IStatusCallback;
import nl.dobots.bluenet.callbacks.IStringCallback;
import nl.dobots.bluenet.core.BleCoreTypes;
import nl.dobots.bluenet.extended.structs.BleDevice;
import nl.dobots.bluenet.extended.structs.BleDeviceMap;
import nl.dobots.bluenet.structs.BleMeshMessage;
import nl.dobots.bluenet.structs.BleTrackedDevice;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 15-7-15
 *
 * @author Dominik Egger
 */
public class BleExt {

	private static final String TAG = BleExt.class.getCanonicalName();

	private static final int CONNECT_TIMEOUT = 10; // 5 seconds

	private BleBase _bleBase;

	private BleDeviceMap _devices = new BleDeviceMap();

	private String _targetAddress;

	private BleDeviceFilter _scanFilter = BleDeviceFilter.all;
	private BleDeviceConnectionState _connectionState = BleDeviceConnectionState.uninitialized;

	private Context _context;

	private ArrayList<String> _detectedCharacteristics = new ArrayList<>();
	private Handler _handler;

	public BleExt() {
		_bleBase = new BleBase();

		HandlerThread handlerThread = new HandlerThread("BleExtHandler");
		handlerThread.start();
		_handler = new Handler(handlerThread.getLooper());
	}

	protected void LOGd(String message) {
		Log.d(TAG, message);
	}

	protected void LOGd(String fmt, Object ... args) {
		LOGd(String.format(fmt, args));
	}

	protected void LOGe(String message) {
//		Toast.makeText(_context, message, Toast.LENGTH_LONG).show();
		Log.e(TAG, message);
	}

	protected void LOGe(String fmt, Object ... args) {
		LOGe(String.format(fmt, args));
	}

	public void setScanFilter(BleDeviceFilter filter) {
		_scanFilter = filter;
	}

	public BleDeviceFilter getScanFilter() {
		return _scanFilter;
	}

	public void setTargetAddress(String targetAddress) {
		_targetAddress = targetAddress;
	}

	public String getTargetAddress() {
		return _targetAddress;
	}

	public BleDeviceMap getDeviceMap() {
		// make sure it is refreshed
		_devices.refresh();
		return _devices;
	}

	public BleDeviceConnectionState getConnectionState() {
		return _connectionState;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		_bleBase.onActivityResult(requestCode, resultCode, data);
	}

	public void init(Context context, final IStatusCallback callback) {
		_context = context;

		// wrap the callback to update the connection state
		_bleBase.init(context, new IStatusCallback() {
			@Override
			public void onSuccess() {
				_connectionState = BleDeviceConnectionState.initialized;
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				_connectionState = BleDeviceConnectionState.uninitialized;
				callback.onError(error);
			}
		});
	}

	public void finish() {
		_bleBase.finish();
	}

	/**
	 * Returns an object of type BleDevice which is an already parsed json object
	 * clears the device list on start
	 * @param callback
	 * @return
	 */
	public boolean startScan(final IBleDeviceCallback callback) {
		_devices.clear();
		return startEndlessScan(callback);
	}

	/**
	 * Starts a scan without clearing the device list
	 * @param callback
	 * @return
	 */
	public boolean startIntervalScan(final IBleDeviceCallback callback) {
		return startEndlessScan(callback);
	}

	private boolean startEndlessScan(final IBleDeviceCallback callback) {
		if (_connectionState != BleDeviceConnectionState.initialized) {
			LOGe("State is not initialized: %s", _connectionState.toString());
			callback.onError(BleExtTypes.ERROR_WRONG_STATE);
			return false;
		}

		_connectionState = BleDeviceConnectionState.scanning;

		return _bleBase.startEndlessScan(new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				BleDevice device;
				try {
					device = new BleDevice(json);
				} catch (JSONException e) {
					LOGe("Failed to parse json into device! Err: " + e.getMessage());
					LOGd("json: " + json.toString());
//					e.printStackTrace();
					return;
				}

				switch (_scanFilter) {
					case crownstone:
						if (!device.isCrownstone()) return;
						break;
					case doBeacon:
					case iBeacon:
						if (!device.isIBeacon()) return;
						break;
					case all:
						break;
				}

				device = _devices.updateDevice(device);
				callback.onSuccess(device);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public boolean stopScan(final IStatusCallback callback) {
		_connectionState = BleDeviceConnectionState.initialized;
		return _bleBase.stopEndlessScan(callback);
	}

	public void connect(String address, final IStatusCallback callback) {

		if (checkState(BleDeviceConnectionState.initialized, null)) {

			if (address != null) {
				_targetAddress = address;
			}

			if (_targetAddress == null) {
				callback.onError(BleCoreTypes.ERROR_NO_ADDRESS_PROVIDED);
				return;
			}

			_connectionState = BleDeviceConnectionState.connecting;

			IDataCallback dataCallback = new IDataCallback() {
				@Override
				public void onData(JSONObject json) {
					String status = BleUtils.getStatus(json);
					if (status == "connected") {
						onConnect();
						callback.onSuccess();
					} else {
						LOGe("wrong status received: %s", status);
						callback.onError(BleExtTypes.ERROR_CONNECT_FAILED);
					}
				}

				@Override
				public void onError(int error) {
					_connectionState = BleDeviceConnectionState.initialized;
					callback.onError(error);
				}
			};

			if (_bleBase.isClosed(_targetAddress)) {
				_bleBase.connectDevice(_targetAddress, CONNECT_TIMEOUT, dataCallback);
			} else if (_bleBase.isDisconnected(_targetAddress)) {
				_bleBase.reconnectDevice(_targetAddress, 30, dataCallback);
			}
		} else if (checkState(BleDeviceConnectionState.connected, null) && _targetAddress.equals(address)) {
			callback.onSuccess();
		} else {
			callback.onError(BleExtTypes.ERROR_WRONG_STATE);
		}

	}

	private void onConnect() {
		LOGd("successfully connected");
		// todo: timeout?
		_connectionState = BleDeviceConnectionState.connected;
	}

	public boolean disconnect(final IStatusCallback callback) {
		if (!checkState(BleDeviceConnectionState.connected, callback)) return false;

		_connectionState = BleDeviceConnectionState.disconnecting;
		return _bleBase.disconnectDevice(_targetAddress, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				String status = BleUtils.getStatus(json);
				if (status == "disconnected") {
					onDisconnect();
					callback.onSuccess();
				} else {
					LOGe("wrong status received: %s", status);
					callback.onError(BleExtTypes.ERROR_DISCONNECT_FAILED);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	private void onDisconnect() {
		LOGd("successfully disconnected");
		// todo: timeout?
		_connectionState = BleDeviceConnectionState.initialized;
//		_detectedCharacteristics.clear();
	}

	private boolean checkState(BleDeviceConnectionState state, IBaseCallback callback) {
		if (_connectionState != state) {
			if (callback != null) {
				LOGe("wrong state: %s", _connectionState.toString());
				callback.onError(BleExtTypes.ERROR_WRONG_STATE);
			}
			return false;
		}
		return true;
	}

	public void close(boolean clearCache, IStatusCallback callback) {
		LOGd("closing device ...");
		_bleBase.closeDevice(_targetAddress, clearCache, callback);
	}

	public void discoverServices(final IDiscoveryCallback callback) {
		LOGd("discovering services ...");
		_detectedCharacteristics.clear();
		_bleBase.discoverServices(_targetAddress, new IDiscoveryCallback() {
			@Override
			public void onDiscovery(String serviceUuid, String characteristicUuid) {
				onCharacteristicDiscovered(serviceUuid, characteristicUuid);
				callback.onDiscovery(serviceUuid, characteristicUuid);
			}

			@Override
			public void onSuccess() {
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	private void onCharacteristicDiscovered(String serviceUuid, String characteristicUuid) {
		LOGd("discovered characteristic: %s", characteristicUuid);
		_detectedCharacteristics.add(characteristicUuid);
	}

	private boolean hasCharacteristic(String characteristicUuid, IBaseCallback callback) {
		if (_detectedCharacteristics.indexOf(characteristicUuid) == -1) {
			if (callback != null) {
				callback.onError(BleExtTypes.ERROR_CHARACTERISTIC_NOT_FOUND);
				return false;
			}
		}
		return true;
	}

	public void connectAndDiscover(String address, final IDiscoveryCallback callback) {
		connect(address, new IStatusCallback() {
			@Override
			public void onSuccess() {
				discoverServices(callback);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public boolean disconnectAndClose(boolean clearCache, final IStatusCallback callback) {
		if (!checkState(BleDeviceConnectionState.connected, callback)) return false;

		_connectionState = BleDeviceConnectionState.disconnecting;
		return _bleBase.disconnectAndCloseDevice(_targetAddress, clearCache, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				String status = BleUtils.getStatus(json);
				if (status == "closed") {
					onDisconnect();
					callback.onSuccess();
				} else if (status != "disconnected") {
					LOGe("wrong status received: %s", status);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	abstract class CallbackRunnable implements Runnable {
		IStatusCallback _callback;

		public void setCallback(IStatusCallback callback) {
			_callback = callback;
		}
	}

	private DelayedDisconnectRunnable _delayedDisconnect = null;

	class DelayedDisconnectRunnable extends CallbackRunnable {
		@Override
		public void run() {
			disconnectAndClose(false, new IStatusCallback() {
				@Override
				public void onSuccess() {
					if (_callback != null) {
						_callback.onSuccess();
					}
				}

				@Override
				public void onError(int error) {
					if (_callback != null) {
						_callback.onError(error);
					}
				}
			});
			_delayedDisconnect = null;
		}
	};

	private void delayedDisconnect(IStatusCallback callback) {
		// todo: solve timeout issue. removing callback doesn't seem to work quite right,
		//   the callback is still called
		if (_delayedDisconnect != null) {
			_handler.removeCallbacks(_delayedDisconnect);
		}
		_delayedDisconnect = new DelayedDisconnectRunnable();
		_delayedDisconnect.setCallback(callback);
		_handler.postDelayed(_delayedDisconnect, 5000);
	}

	public int counter = 0;
	public void connectAndExecute(String address, final IExecuteCallback function, final IStatusCallback callback) {
		counter++;
		LOGd("counter: %d", counter);
		connectAndDiscover(address, new IDiscoveryCallback() {
			@Override
			public void onDiscovery(String serviceUuid, String characteristicUuid) { /* don't care */ }

			@Override
			public void onSuccess() {

				// call execute function
				function.execute(new IStatusCallback() {
					@Override
					public void onSuccess() {
						delayedDisconnect(callback);
					}

					@Override
					public void onError(int error) {
						delayedDisconnect(new IStatusCallback() {
							@Override
							public void onSuccess() { /* don't care */ }

							@Override
							public void onError(int error) {
								callback.onError(error);
							}
						});
						callback.onError(error);
					}
				});
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
//				disconnectAndClose(new IStatusCallback() {
//					@Override
//					public void onSuccess() {}
//
//					@Override
//					public void onError(int error) {}
//				});
			}
		});
	}

	public boolean isConnected(IBaseCallback callback) {
		if (checkState(BleDeviceConnectionState.connected, callback)) {
			if (_bleBase.isDeviceConnected(_targetAddress)) {
				return true;
			} else {
				if (callback != null) {
					callback.onError(BleExtTypes.ERROR_NOT_CONNECTED);
				}
				return false;
			}
		}
		return false;
	}

	public boolean isStillConnected(IStatusCallback callback) {
		if (isConnected(callback)) {
			if (_delayedDisconnect != null) {
				delayedDisconnect(callback);
			}
			return true;
		}
		return false;
	}

	///////////////////
	// Power service //
	///////////////////

	public void readPwm(IIntegerCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_PWM_UUID, callback)) {
			LOGd("Reading current PWM value ...");
			_bleBase.readPWM(_targetAddress, callback);
		}
	}

	public void readPwm(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			readPwm(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readPwm(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void writePwm(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_PWM_UUID, callback)) {
			LOGd("Set PWM to %d", value);
			_bleBase.writePWM(_targetAddress, value, callback);
		}
	}

	public void writePwm(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			writePwm(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					writePwm(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void togglePower(final IStatusCallback callback) {
		readPwm(new IIntegerCallback() {
			@Override
			public void onSuccess(int result) {
				if (result > 0) {
					writePwm(0, (IStatusCallback)callback);
				} else {
					writePwm(255, (IStatusCallback)callback);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void togglePower(String address, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			togglePower(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					togglePower(new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void powerOn(IStatusCallback callback) {
		writePwm(255, callback);
	}

	public void powerOn(String address, final IStatusCallback callback) {
		writePwm(address, 255, callback);
	}

	public void powerOff(IStatusCallback callback) {
		writePwm(0, callback);
	}

	public void powerOff(String address, final IStatusCallback callback) {
		writePwm(address, 0, callback);
	}

	public void readCurrentConsumption(final IIntegerCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_SAMPLE_CURRENT_UUID, callback) &&
				hasCharacteristic(BleTypes.CHAR_CURRENT_CONSUMPTION_UUID, callback)) {
			// Sample current
			LOGd("Sampling Current ...");
			_bleBase.sampleCurrent(_targetAddress, BleTypes.SAMPLE_CURRENT_CONSUMPTION, new IStatusCallback() {
				@Override
				public void onSuccess() {
					// give some time for the sampling
					_handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							LOGd("Reading CurrentConsumption value ...");
							_bleBase.readCurrentConsumption(_targetAddress, callback);
						}
					}, 100);
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void readCurrentConsumption(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			readCurrentConsumption(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readCurrentConsumption(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void readCurrentCurve(final IByteArrayCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_SAMPLE_CURRENT_UUID, callback) &&
				hasCharacteristic(BleTypes.CHAR_CURRENT_CURVE_UUID, callback)) {
			// Sample current
			LOGd("Sampling Current ...");
			_bleBase.sampleCurrent(_targetAddress, BleTypes.SAMPLE_CURRENT_CURVE, new IStatusCallback() {
				@Override
				public void onSuccess() {
					// give some time for the sampling
					_handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							LOGd("Reading CurrentCurve value ...");
							_bleBase.readCurrentCurve(_targetAddress, callback);
						}
					}, 100);
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void readCurrentCurve(String address, final IByteArrayCallback callback) {
		if (isStillConnected(null)) {
			readCurrentCurve(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readCurrentCurve(new IByteArrayCallback() {
						@Override
						public void onSuccess(byte[] result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void readCurrentLimit(IIntegerCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_CURRENT_LIMIT_UUID, callback)) {
			LOGd("Reading CurrentLimit value ...");
			_bleBase.readCurrentLimit(_targetAddress, callback);
		}
	}

	public void readCurrentLimit(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			readCurrentLimit(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readCurrentLimit(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void writeCurrentLimit(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_CURRENT_LIMIT_UUID, callback)) {
			LOGd("Set CurrentLimit to %d", value);
			_bleBase.writeCurrentLimit(_targetAddress, value, callback);
		}
	}

	public void writeCurrentLimit(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			writeCurrentLimit(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					writeCurrentLimit(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/////////////////////
	// General service //
	/////////////////////

	private void writeReset(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_RESET_UUID, callback)) {
			LOGd("Set Reset to %d", value);
			_bleBase.writeReset(_targetAddress, value, callback);
		}
	}

	private void writeReset(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			writeReset(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					writeReset(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void resetDevice(IStatusCallback callback) {
		writeReset(BleTypes.RESET_DEFAULT, callback);
	}

	public void resetDevice(String address, final IStatusCallback callback) {
		writeReset(address, BleTypes.RESET_DEFAULT, callback);
	}

	public void resetToBootloader(IStatusCallback callback) {
		writeReset(BleTypes.RESET_BOOTLOADER, callback);
	}

	public void resetToBootloader(String address, final IStatusCallback callback) {
		writeReset(address, BleTypes.RESET_BOOTLOADER, callback);
	}

	public void readTemperature(IIntegerCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_TEMPERATURE_UUID, callback)) {
			LOGd("Reading Temperature value ...");
			_bleBase.readTemperature(_targetAddress, callback);
		}
	}

	public void readTemperature(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			readTemperature(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readTemperature(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void writeMeshMessage(BleMeshMessage value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_MESH_UUID, callback)) {
			LOGd("Set MeshMessage to %s", value.toString());
			_bleBase.writeMeshMessage(_targetAddress, value, callback);
		}
	}

	public void writeMeshMessage(String address, final BleMeshMessage value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			writeMeshMessage(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					writeMeshMessage(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public boolean hasConfigurationCharacteristics(IBaseCallback callback) {
		return hasCharacteristic(BleTypes.CHAR_SELECT_CONFIGURATION_UUID, callback) &&
				hasCharacteristic(BleTypes.CHAR_GET_CONFIGURATION_UUID, callback) &&
				hasCharacteristic(BleTypes.CHAR_SET_CONFIGURATION_UUID, callback);
	}

//	public void writeConfiguration(BleConfiguration value, IStatusCallback callback) {
//		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
//			LOGd("Set Configuration to %s", value.toString());
//			_bleBase.writeConfiguration(_targetAddress, value, callback);
//		}
//	}

//	public void writeConfiguration(String address, final BleConfiguration value, final IStatusCallback callback) {
//		if (isStillConnected(null)) {
//			writeConfiguration(value, callback);
//		} else {
//			connectAndExecute(address, new IExecuteCallback() {
//				@Override
//				public void execute(final IStatusCallback execCallback) {
//					writeConfiguration(value, new IStatusCallback() {
//						@Override
//						public void onSuccess() {
//							callback.onSuccess();
//							execCallback.onSuccess();
//						}
//
//						@Override
//						public void onError(int error) {
//							execCallback.onSuccess();
//						}
//					});
//				}
//			}, new IStatusCallback() {
//				@Override
//				public void onSuccess() { /* don't care */ }
//
//				@Override
//				public void onError(int error) {
//					callback.onError(error);
//				}
//			});
//		}
//	}

//	public void readConfiguration(int configurationType, IConfigurationCallback callback) {
//		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
//			LOGd("Reading Configuration value ...");
//			_bleBase.getConfiguration(_targetAddress, configurationType, callback);
//		}
//	}

//	public void readConfiguration(String address, final int configurationType, final IConfigurationCallback callback) {
//		if (isStillConnected(null)) {
//			readConfiguration(configurationType, callback);
//		} else {
//			connectAndExecute(address, new IExecuteCallback() {
//				@Override
//				public void execute(final IStatusCallback execCallback) {
//					readConfiguration(configurationType, new IConfigurationCallback() {
//						@Override
//						public void onSuccess(BleConfiguration result) {
//							callback.onSuccess(result);
//							execCallback.onSuccess();
//						}
//
//						@Override
//						public void onError(int error) {
//							execCallback.onSuccess();
//						}
//					});
//				}
//			}, new IStatusCallback() {
//				@Override
//				public void onSuccess() { /* don't care */ }
//
//				@Override
//				public void onError(int error) {
//					callback.onError(error);
//				}
//			});
//		}
//	}

	public void setDeviceName(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set DeviceName to %s", value.toString());
			_bleBase.setDeviceName(_targetAddress, value, callback);
		}
	}

	public void setDeviceName(String address, final String value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setDeviceName(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setDeviceName(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getDeviceName(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get DeviceName ...");
			_bleBase.getDeviceName(_targetAddress, callback);
		}
	}

	public void getDeviceName(String address, final IStringCallback callback) {
		if (isStillConnected(null)) {
			getDeviceName(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getDeviceName(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getBeaconMajor(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Reading BeaconMajor value ...");
			_bleBase.getBeaconMajor(_targetAddress, callback);
		}
	}

	public void getBeaconMajor(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			getBeaconMajor(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getBeaconMajor(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setBeaconMajor(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set BeaconMajor to %d", value);
			_bleBase.setBeaconMajor(_targetAddress, value, callback);
		}
	}

	public void setBeaconMajor(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setBeaconMajor(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setBeaconMajor(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getBeaconMinor(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get BeaconMinor ...");
			_bleBase.getBeaconMinor(_targetAddress, callback);
		}
	}

	public void getBeaconMinor(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			getBeaconMinor(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getBeaconMinor(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setBeaconMinor(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set BeaconMinor to %d", value);
			_bleBase.setBeaconMinor(_targetAddress, value, callback);
		}
	}

	public void setBeaconMinor(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setBeaconMinor(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setBeaconMinor(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getBeaconProximityUuid(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get BeaconProximityUuid ...");
			_bleBase.getBeaconProximityUuid(_targetAddress, callback);
		}
	}

	public void getBeaconProximityUuid(String address, final IStringCallback callback) {
		if (isStillConnected(null)) {
			getBeaconProximityUuid(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getBeaconProximityUuid(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setBeaconProximityUuid(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set BeaconProximityUuid to %s", value);
			_bleBase.setBeaconProximityUuid(_targetAddress, value, callback);
		}
	}

	public void setBeaconProximityUuid(String address, final String value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setBeaconProximityUuid(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setBeaconProximityUuid(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getBeaconCalibratedRssi(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get BeaconCalibratedRssi ...");
			_bleBase.getBeaconCalibratedRssi(_targetAddress, callback);
		}
	}

	public void getBeaconCalibratedRssi(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			getBeaconCalibratedRssi(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getBeaconCalibratedRssi(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setBeaconCalibratedRssi(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set BeaconCalibratedRssi to %d", value);
			_bleBase.setBeaconCalibratedRssi(_targetAddress, value, callback);
		}
	}

	public void setBeaconCalibratedRssi(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setBeaconCalibratedRssi(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setBeaconCalibratedRssi(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getDeviceType(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get DeviceType ...");
			_bleBase.getDeviceType(_targetAddress, callback);
		}
	}

	public void getDeviceType(String address, final IStringCallback callback) {
		if (isStillConnected(null)) {
			getDeviceType(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getDeviceType(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setDeviceType(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set DeviceType to %s", value);
			_bleBase.setDeviceType(_targetAddress, value, callback);
		}
	}

	public void setDeviceType(String address, final String value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setDeviceType(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setDeviceType(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getFloor(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get Floor ...");
			_bleBase.getFloor(_targetAddress, callback);
		}
	}

	public void getFloor(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			getFloor(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getFloor(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setFloor(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set Floor to %s", value);
			_bleBase.setFloor(_targetAddress, value, callback);
		}
	}

	public void setFloor(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setFloor(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setFloor(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getRoom(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get Room ...");
			_bleBase.getRoom(_targetAddress, callback);
		}
	}

	public void getRoom(String address, final IStringCallback callback) {
		if (isStillConnected(null)) {
			getRoom(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getRoom(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setRoom(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set Room to %s", value);
			_bleBase.setRoom(_targetAddress, value, callback);
		}
	}

	public void setRoom(String address, final String value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setRoom(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setRoom(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getTxPower(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get TxPower ...");
			_bleBase.getTxPower(_targetAddress, callback);
		}
	}

	public void getTxPower(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			getTxPower(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getTxPower(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setTxPower(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set TxPower to %d", value);
			_bleBase.setTxPower(_targetAddress, value, callback);
		}
	}

	public void setTxPower(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setTxPower(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setTxPower(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getAdvertisementInterval(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get AdvertisementInterval ...");
			_bleBase.getAdvertisementInterval(_targetAddress, callback);
		}
	}

	public void getAdvertisementInterval(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			getAdvertisementInterval(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getAdvertisementInterval(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setAdvertisementInterval(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set AdvertisementInterval to %d", value);
			_bleBase.setAdvertisementInterval(_targetAddress, value, callback);
		}
	}

	public void setAdvertisementInterval(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setAdvertisementInterval(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setAdvertisementInterval(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setWifi(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set Wifi to %s", value);
			_bleBase.setWifi(_targetAddress, value, callback);
		}
	}

	public void setWifi(String address, final String value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setWifi(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setWifi(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getIp(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get Ip ...");
			// todo: continue here
			_bleBase.getIp(_targetAddress, callback);
		}
	}

	public void getIp(String address, final IStringCallback callback) {
		if (isStillConnected(null)) {
			getIp(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getIp(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getMinEnvTemp(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get minimum environment temperature...");
			_bleBase.getMinEnvTemp(_targetAddress, callback);
		}
	}

	public void getMinEnvTemp(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			getMinEnvTemp(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getMinEnvTemp(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setMinEnvTemp(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set minimum environment temperature to %d", value);
			_bleBase.setMinEnvTemp(_targetAddress, value, callback);
		}
	}

	public void setMinEnvTemp(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setMinEnvTemp(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setMinEnvTemp(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void getMaxEnvTemp(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Get maximum environment temperature...");
			_bleBase.getMaxEnvTemp(_targetAddress, callback);
		}
	}

	public void getMaxEnvTemp(String address, final IIntegerCallback callback) {
		if (isStillConnected(null)) {
			getMaxEnvTemp(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getMaxEnvTemp(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setMaxEnvTemp(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			LOGd("Set maximum environment temperature to %d", value);
			_bleBase.setMaxEnvTemp(_targetAddress, value, callback);
		}
	}

	public void setMaxEnvTemp(String address, final int value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			setMaxEnvTemp(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setMaxEnvTemp(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}


	//////////////////////////
	// Localization service //
	//////////////////////////

	public void readTrackedDevices(IByteArrayCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_LIST_TRACKED_DEVICES_UUID, callback)) {
			LOGd("Reading TrackedDevices value ...");
			_bleBase.readTrackedDevices(_targetAddress, callback);
		}
	}

	public void readTrackedDevices(String address, final IByteArrayCallback callback) {
		if (isStillConnected(null)) {
			readTrackedDevices(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readTrackedDevices(new IByteArrayCallback() {
						@Override
						public void onSuccess(byte[] result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void addTrackedDevice(BleTrackedDevice value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_ADD_TRACKED_DEVICE_UUID, callback)) {
			LOGd("Set TrackedDevice to %s", value.toString());
			_bleBase.addTrackedDevice(_targetAddress, value, callback);
		}
	}

	public void addTrackedDevice(String address, final BleTrackedDevice value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			addTrackedDevice(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					addTrackedDevice(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void listScannedDevices(IByteArrayCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_DEVICE_LIST_UUID, callback)) {
			LOGd("List scanned devices ...");
			_bleBase.listScannedDevices(_targetAddress, callback);
		}
	}

	public void listScannedDevices(String address, final IByteArrayCallback callback) {
		if (isStillConnected(null)) {
			listScannedDevices(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					listScannedDevices(new IByteArrayCallback() {
						@Override
						public void onSuccess(byte[] result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void writeScanDevices(boolean value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BleTypes.CHAR_DEVICE_SCAN_UUID, callback)) {
			LOGd("Scan Devices: %b", value);
			_bleBase.scanDevices(_targetAddress, value, callback);
		}
	}

	public void writeScanDevices(String address, final boolean value, final IStatusCallback callback) {
		if (isStillConnected(null)) {
			writeScanDevices(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					writeScanDevices(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onSuccess();
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void scanForDevices(final int scanDuration, final IByteArrayCallback callback) {
		writeScanDevices(true, new IStatusCallback() {
			@Override
			public void onSuccess() {
				_handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						writeScanDevices(false, new IStatusCallback() {
							@Override
							public void onSuccess() {
								// delay 500 ms, just wait, don't postdelay, since we are already
								// inside the handler, and hopefully 500ms delay won't cause havoc
								SystemClock.sleep(500);
								listScannedDevices(callback);
							}

							@Override
							public void onError(int error) {
								callback.onError(error);
							}
						});
					}
				}, scanDuration);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void scanForDevices(final String address, final int scanDuration, final IByteArrayCallback callback) {
		if (isStillConnected(null)) {
			scanForDevices(scanDuration, callback);
		} else {
			// connect and execute ...
			connectAndExecute(address,
				new IExecuteCallback() {
					@Override
					public void execute(final IStatusCallback startExecCallback) {
						// ... start scanning for devices
						writeScanDevices(true, new IStatusCallback() {
							@Override
							public void onSuccess() {
								// if successfully started, post the stop scan with scanDuration delay
								_handler.postDelayed(new Runnable() {
									@Override
									public void run() {
										// once scanDuration delay expired, connect and execute ...
										connectAndExecute(address,
											new IExecuteCallback() {
												@Override
												public void execute(final IStatusCallback stopExecCallback) {
													// ... stop scanning for devices
													writeScanDevices(false, new IStatusCallback() {
														@Override
														public void onSuccess() {
															// if successfully stopped, get the list of scanned devices ...

															// delay 500 ms to give time for list to be written to characteristic
															// just wait, don't postdelay, since we are already
															// inside the handler, and hopefully 500ms delay won't cause havoc
															SystemClock.sleep(500);
															// get the list ...
															listScannedDevices(new IByteArrayCallback() {
																@Override
																public void onSuccess(byte[] result) {
																	callback.onSuccess(result);
																	// ... and disconnect again once we have it
																	stopExecCallback.onSuccess();
																}

																@Override
																public void onError(int error) {
																	callback.onError(error);
																	// also disconnect if an error occurs
																	stopExecCallback.onSuccess();
																}
															});
														}

														@Override
														public void onError(int error) {
															callback.onError(error);
															// disconnect if an error occurs
															stopExecCallback.onSuccess();
														}
													});
												}
											}, new IStatusCallback() {
												@Override
												public void onSuccess() { /* don't care */ }

												@Override
												public void onError(int error) {
													callback.onError(error);
												}
											}
										);

									}
								}, scanDuration);
								// after posting, disconnect again
								startExecCallback.onSuccess();
							}

							@Override
							public void onError(int error) {
								callback.onError(error);
								// disconnect if an error occurs
								startExecCallback.onSuccess();
							}
						});
					}
				}, new IStatusCallback() {
					@Override
					public void onSuccess() { /* don't care */ }

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				}
			);
		}
	}








	/////////////////////////////////////////////////////////////////////////////////////

//	public void readXXX(ICallback callback) {
//		if (isConnected(callback) && hasCharacteristic(BleTypes.YYY, callback)) {
//			LOGd("Reading XXX value ...");
//			_bleBase.readXXX(_targetAddress, callback);
//		}
//	}
//
//	public void readXXX(String address, final ICallback callback) {
//		if (isStillConnected(null)) {
//			readXXX(callback);
//		} else {
//			connectAndExecute(address, new IExecuteCallback() {
//				@Override
//				public void execute(final IStatusCallback execCallback) {
//					readXXX(new ICallback() {
//						@Override
//						public void onSuccess(zzz result) {
//							callback.onSuccess(result);
//							execCallback.onSuccess();
//						}
//
//						@Override
//						public void onError(int error) {
//							execCallback.onSuccess();
//						}
//					});
//				}
//			}, new IStatusCallback() {
//				@Override
//				public void onSuccess() { /* don't care */ }
//
//				@Override
//				public void onError(int error) {
//					callback.onError(error);
//				}
//			});
//		}
//	}
//
//	public void writeXXX(zzz value, IStatusCallback callback) {
//		if (isConnected(callback) && hasCharacteristic(BleTypes.YYY, callback)) {
//			LOGd("Set XXX to %uuu", value);
//			_bleBase.writeXXX(_targetAddress, value, callback);
//		}
//	}
//
//	public void writeXXX(String address, final zzz value, final IStatusCallback callback) {
//		if (isStillConnected(null)) {
//			writeXXX(value, callback);
//		} else {
//			connectAndExecute(address, new IExecuteCallback() {
//				@Override
//				public void execute(final IStatusCallback execCallback) {
//					writeXXX(value, new IStatusCallback() {
//						@Override
//						public void onSuccess() {
//							callback.onSuccess();
//							execCallback.onSuccess();
//						}
//
//						@Override
//						public void onError(int error) {
//							execCallback.onSuccess();
//						}
//					});
//				}
//			}, new IStatusCallback() {
//				@Override
//				public void onSuccess() { /* don't care */ }
//
//				@Override
//				public void onError(int error) {
//					callback.onError(error);
//				}
//			});
//		}
//	}

}
