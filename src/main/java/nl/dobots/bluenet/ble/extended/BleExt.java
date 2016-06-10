package nl.dobots.bluenet.ble.extended;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;

import nl.dobots.bluenet.ble.base.BleBase;
import nl.dobots.bluenet.ble.base.callbacks.IAlertCallback;
import nl.dobots.bluenet.ble.base.callbacks.IMeshDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStateCallback;
import nl.dobots.bluenet.ble.base.structs.BleAlertState;
import nl.dobots.bluenet.ble.base.structs.BleCommand;
import nl.dobots.bluenet.ble.base.structs.BleState;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.core.BleCore;
import nl.dobots.bluenet.ble.base.callbacks.IBaseCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IStringCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceMap;
import nl.dobots.bluenet.ble.base.structs.BleMeshMessage;
import nl.dobots.bluenet.ble.base.structs.BleTrackedDevice;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;

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

	// default timeout for connection attempt
	private static final int CONNECT_TIMEOUT = 10; // 10 seconds

	// default time used for delayed disconnects
	public static final int DELAYED_DISCONNECT_TIME = 5000; // 5 seconds

	private BleBase _bleBase;

	// list of devices. scanned devices that pass through the filter will be stored in the list
	private BleDeviceMap _devices = new BleDeviceMap();

	// address of the device we are connecting / talking to
	private String _targetAddress;

	// filter, used to filter devices based on "type", eg. only report crownstone devices, or
	// only report guidestone devices
	private BleDeviceFilter _scanFilter = BleDeviceFilter.all;

	// current connection state
	private BleDeviceConnectionState _connectionState = BleDeviceConnectionState.uninitialized;

	// keep a list of discovered services and characteristics
	private ArrayList<String> _detectedCharacteristics = new ArrayList<>();

	// handler used for delayed execution and timeouts
	private Handler _handler;

	private ArrayList<String> _blackList;
	private ArrayList<String> _whiteList;

	private IBleDeviceCallback _cloudScanCB;

	public BleExt() {
		_bleBase = new BleBase();

		// create handler with its own thread
		HandlerThread handlerThread = new HandlerThread("BleExtHandler");
		handlerThread.start();
		_handler = new Handler(handlerThread.getLooper());
	}

	/**
	 * Get access to the base bluenet object. Only use it if you need to change some low level
	 * settings. Usually this is Not necessary.
	 * @return BleBase object used by this exented object to interact with the Android Bluetooth
	 * functions
	 */
	public BleBase getBleBase() {
		return _bleBase;
	}

	/**
	 * Set the scan device filter. by setting a filter, only the devices specified will
	 * pass through the filter and be reported to the application, any other detected devices
	 * will be ignored.
	 * @param filter the filter to be used
	 */
	public void setScanFilter(BleDeviceFilter filter) {
		_scanFilter = filter;
	}

	/**
	 * Get the currently set filter
	 * @return the device filter
	 */
	public BleDeviceFilter getScanFilter() {
		return _scanFilter;
	}

	/**
	 * Get the current target address, i.e. the address of the device we are connected to
	 * @return the MAC address of the device
	 */
	public String getTargetAddress() {
		return _targetAddress;
	}

	/**
	 * Get the list of scanned devices. The list is updated every time a device is detected, the
	 * rssi is updated, the average rssi is computed and if the device is an iBeacon, the distance
	 * is estimated
	 * @return the list of scanned devices
	 */
	public synchronized BleDeviceMap getDeviceMap() {
		// make sure it is refreshed
		_devices.refresh();
		return _devices;
	}

	/**
	 * Clear the list of scanned devices.
	 */
	public synchronized void clearDeviceMap() {
		_devices.clear();
	}

	/**
	 * Get the current connection state
	 * @return connection state
	 */
	public BleDeviceConnectionState getConnectionState() {
		return _connectionState;
	}

	/**
	 * Initializes the BLE Modules and tries to enable the Bluetooth adapter. Note, the callback
	 * provided as parameter will persist. The callback will be triggered whenever the state of
	 * the bluetooth adapter changes. That means if the user turns off bluetooth, then the onError
	 * of the callback will be triggered. And again if the user turns bluetooth on, the onSuccess
	 * will be triggered. If the user denies enabling bluetooth, then onError will be called after
	 * a timeout expires
	 * @param context the context used to enable bluetooth, this can be a service or an activity
	 * @param callback callback, used to report back if bluetooth is enabled / disabled
	 */
	public void init(Context context, final IStatusCallback callback) {
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

	/**
	 * Close the library and release all callbacks
	 */
	public void destroy() {
		_bleBase.destroy();
	}

	/**
	 * Set the given addresses as black list. any address on the black list will be ignored
	 * during a scan and not returned as a scan result
	 * @param addresses the MAC addresses of the devices which should be ignored during a scan
	 */
	public void setBlackList(String[] addresses) {
		_blackList = new ArrayList<>(Arrays.asList(addresses.clone()));
	}

	/**
	 * Clear the black list again in order to get all devices during a scan
	 */
	public void clearBlackList() {
		_blackList = null;
	}

	/**
	 * Set the given addresses as white list. only devices on the white list will be returned
	 * during a scan. any other device will be ignored.
	 * @param addresses the MAC addresses of the devices which should be returned during a scan
	 */
	public void setWhiteList(String[] addresses) {
		_whiteList = new ArrayList<>(Arrays.asList(addresses.clone()));
	}

	/**
	 * Clear the white list again in order to get all devices during a scan
	 */
	public void clearWhiteList() {
		_whiteList = null;
	}

	/**
	 * Start scanning for devices. devices will be provided through the callback. see
	 * startEndlessScan for details
	 *
	 * Note: clears the device list on start
	 * @param callback callback used to report back scanned devices
	 * @return true if the scan was started, false if an error occurred
	 */
	public boolean startScan(final IBleDeviceCallback callback) {
		clearDeviceMap();
		return startEndlessScan(callback);
	}

	/**
	 * Starts a scan without clearing the device list. used for interval scanning, i.e.,
	 * scan for some time, then pause for some time, then scan again. see
	 * startEndlessScan for details
	 * @param callback callback used to report back scanned devices
	 * @return true if the scan was started, false if an error occurred
	 */
	public boolean startIntervalScan(final IBleDeviceCallback callback) {
		return startEndlessScan(callback);
	}

	/**
	 * Helper function to start an endless scan. endless meaning, it will continue scanning
	 * until stopScan is called. If a device is detected, the data is parsed into a BleDevice
	 * object. Then the filter is checked to see if the device passes through the filter. If
	 * it passes, the device list is updated, which triggers a recalculation of the devices
	 * average RSSI (and the distance if it is an iBeacon). After the update, it is
	 * reported back through the callbacks onDeviceScanned function.
	 *
	 * @param callback callback used to report back scanned devices
	 * @return true if the scan was started, false if an error occurred
	 */
	private boolean startEndlessScan(final IBleDeviceCallback callback) {
		checkConnectionState(BleDeviceConnectionState.initialized, null);
//		if (_connectionState != BleDeviceConnectionState.initialized) {
//			BleCore.LOGe(TAG, "State is not initialized: %s", _connectionState.toString());
//			callback.onError(BleCoreTypes.ERROR_WRONG_STATE);
//			return false;
//		}

		_connectionState = BleDeviceConnectionState.scanning;

		return _bleBase.startEndlessScan(new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				BleDevice device;
				try {
					device = new BleDevice(json);
				} catch (JSONException e) {
					BleLog.LOGe(TAG, "Failed to parse json into device! Err: " + e.getMessage());
					BleLog.LOGd(TAG, "json: " + json.toString());
					return;
				}

				if (_blackList != null && _blackList.contains(device.getAddress())) {
					return;
				}
				if (_whiteList != null && !_whiteList.contains(device.getAddress())) {
					return;
				}

				switch (_scanFilter) {
					case crownstone:
						// if filter set to crownstone, but device is not a crownstone, abort
						if (!device.isCrownstone()) return;
						break;
					case guidestone:
						if (!device.isGuidestone()) return;
						break;
					case iBeacon:
						// if filter set to beacon, but device is not a beacon, abort
						if (!device.isIBeacon()) return;
						break;
					case fridge:
						if (!device.isFridge()) return;
						break;
					case all:
						// return any device that was detected
						break;
				}

				// update the device list, this triggers recalculation of the average RSSI (and
				// distance estimation if it is a beacon)
				device = updateDevice(device);
				// report the updated device
				callback.onDeviceScanned(device);

				if (_cloudScanCB != null) {
					_cloudScanCB.onDeviceScanned(device);
				}
			}

			@Override
			public void onError(int error) {
				_connectionState = BleDeviceConnectionState.initialized;
				callback.onError(error);
			}
		});
	}

	private synchronized BleDevice updateDevice(BleDevice device) {
		return _devices.updateDevice(device);
	}

	/**
	 * Stop scanning for devices
	 * @param callback the callback used to report success or failure of the stop scan
	 * @return true if the scan was stopped, false otherwise
	 */
	public boolean stopScan(final IStatusCallback callback) {
		_connectionState = BleDeviceConnectionState.initialized;
		return _bleBase.stopEndlessScan(callback);
	}

	/**
	 * Check if currently scanning for devices
	 * @return true if scanning, false otherwise
	 */
	public boolean isScanning() {
		return _bleBase.isScanning();
	}

	/**
	 * Every time a device is scanned, the onDeviceScanned function of the
	 * callback provided as scanCB parameter will trigger. Use this to enable
	 * cloud upload, i.e. forward the scan to the crownstone-loopack-sdk
	 * @param scanCB
	 */
	public void enableCloudUpload(IBleDeviceCallback scanCB) {
		_cloudScanCB = scanCB;
	}

	/**
	 * Disable cloud upload again
	 */
	public void disableCloudUpload() {
		_cloudScanCB = null;
	}

	/**
	 * Connect to the device with the given MAC address. Scan first for devices to find possible
	 * devices or make sure that the device you want to connect to is there.
	 * @param address the MAC address of the device, in the form of "12:34:56:AB:CD:EF"
	 * @param callback the callback used to report success or failure. onSuccess will be called
	 *                 if the device was successfully connected. onError will be called with an
	 *                 ERROR to report failure.
	 */
	private void connect(String address, final IStatusCallback callback) {

		if (checkConnectionState(BleDeviceConnectionState.initialized, null)) {

			if (address != null) {
				_targetAddress = address;
			}

			if (_targetAddress == null) {
				callback.onError(BleErrors.ERROR_NO_ADDRESS_PROVIDED);
				return;
			}

			_connectionState = BleDeviceConnectionState.connecting;

			IDataCallback dataCallback = new IDataCallback() {
				@Override
				public void onData(JSONObject json) {
					String status = BleCore.getStatus(json);
					if (status == "connected") {
						onConnect();
						callback.onSuccess();
					} else {
						BleLog.LOGe(TAG, "wrong status received: %s", status);
						_connectionState = BleDeviceConnectionState.initialized;
						callback.onError(BleErrors.ERROR_CONNECT_FAILED);
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
		} else if (checkConnectionState(BleDeviceConnectionState.connected, null)) {
			if (_targetAddress.equals(address)) {
				callback.onSuccess();
			} else {
				callback.onError(BleErrors.ERROR_STILL_CONNECTED);
			}
		} else {
			callback.onError(BleErrors.ERROR_WRONG_STATE);
		}

	}

	/**
	 * Helper function to handle connect events. E.g. update the connection state
	 */
	private void onConnect() {
		BleLog.LOGd(TAG, "successfully connected");
		// todo: timeout?
		_connectionState = BleDeviceConnectionState.connected;
	}

	/**
	 * Disconnect from the currently connected device. use the callback to report back
	 * success or failure
	 * @param callback the callback used to report back if the disconnect was successful or not
	 * @return true if disconnect procedure is started, false if an error occurred and disconnect
	 *         procedure could not be started
	 */
	public boolean disconnect(final IStatusCallback callback) {
		checkConnectionState(BleDeviceConnectionState.connected, null);
//		if (!checkConnectionState(BleDeviceConnectionState.connected, callback)) return false;

		_connectionState = BleDeviceConnectionState.disconnecting;
		return _bleBase.disconnectDevice(_targetAddress, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				String status = BleCore.getStatus(json);
				if (status == "disconnected") {
					onDisconnect();
					callback.onSuccess();
				} else {
					BleLog.LOGe(TAG, "wrong status received: %s", status);
					callback.onError(BleErrors.ERROR_DISCONNECT_FAILED);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Helper function to handle disconnect events, e.g. update the connection state
	 */
	private void onDisconnect() {
		BleLog.LOGd(TAG, "successfully disconnected");
		// todo: timeout?
		_connectionState = BleDeviceConnectionState.initialized;
		clearDelayedDisconnect();
//		_detectedCharacteristics.clear();
	}

	/**
	 * Helper function to check the connection state. calles the callbacks onError function
	 * with an ERROR_WRONG_STATE if the current state does not match the one provided as a parameter
	 * @param state the required state, is checked against the current state
	 * @param callback the callback used to report an error if the states don't match, can be null
	 *                 if error doesn't need to be reported, in which case the return value is enough
	 * @return true if states match, false otherwise
	 */
	private boolean checkConnectionState(BleDeviceConnectionState state, IBaseCallback callback) {
		if (_connectionState != state) {
			BleLog.LOGe(TAG, "wrong connection state: %s instead of %s", _connectionState.toString(), state.toString());
			if (callback != null) {
				callback.onError(BleErrors.ERROR_WRONG_STATE);
			}
			return false;
		}
		return true;
	}

	/**
	 * Close the device after a disconnect. A device has to be closed after a disconnect before
	 * you can connect to it again.
	 * By providing true as the clearCache parameter, the device cache will be cleared, making sure
	 * that next time we connect, the most up to date service and characteristic list will be
	 * retrieved. providing false as parameter, the device cache will not be cleared, and on next
	 * connect and discover, the services and characteristics are retrieved from the cache, and do
	 * not necessarily match the ones on the device. This speeds up discovery if you can be sure
	 * that the device did not change.
	 * @param clearCache provide true if the device cache should be cleared, will make sure that
	 *                   services and characteristics are read from the device and not from the cache
	 *                   provide false to leave the cached services and characteristics, making
	 *                   the next connect and discover much faster
	 * @param callback the callback used to report success or failure
	 */
	public void close(boolean clearCache, IStatusCallback callback) {
		BleLog.LOGd(TAG, "closing device ...");
		_bleBase.closeDevice(_targetAddress, clearCache, callback);
	}

	/**
	 * Discover the available services and characteristics of the connected device. The callbacks
	 * onDiscovery function will be called with service UUID and characteristic UUID for each
	 * discovered characteristic. Once the discovery completes, the onSuccess is called or the onError
	 * if an error occurs
	 *
	 * Note: if you get wrong services and characteristics returned, try to clear the cache by calling
	 * close with parameter clearCache set to true. this makes sure that next discover will really
	 * read the services and characteristics from the device and not the cache
	 * @param callback the callback used to report discovered services and characteristics
	 */
	public void discoverServices(final IDiscoveryCallback callback) {
		BleLog.LOGd(TAG, "discovering services ...");
		_detectedCharacteristics.clear();
		_bleBase.discoverServices(_targetAddress, new IDiscoveryCallback() {
			@Override
			public void onDiscovery(String serviceUuid, String characteristicUuid) {
				onCharacteristicDiscovered(serviceUuid, characteristicUuid);
				callback.onDiscovery(serviceUuid, characteristicUuid);
			}

			@Override
			public void onSuccess() {
				BleLog.LOGd(TAG, "... discovery done");
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				BleLog.LOGe(TAG, "... discovery failed");
				callback.onError(error);
			}
		});
	}

	/**
	 * Helper function to handle discovered characteristics. the discovered characteristics
	 * are stored in a list, to later make sure that characteristics are only read/ written to
	 * if they were actually discovered (are present on the device)
	 * @param serviceUuid the UUID of the service in which the characteristic was found
	 * @param characteristicUuid the UUID of the characteristic
	 */
	private void onCharacteristicDiscovered(String serviceUuid, String characteristicUuid) {
		BleLog.LOGd(TAG, "discovered characteristic: %s", characteristicUuid);
		// todo: might have to store both service and characteristic uuid, because the characteristic
		//       UUID is not unique!
		_detectedCharacteristics.add(characteristicUuid);
	}

	/**
	 * Helper function to check if the connected device has the requested characteristic
	 * @param characteristicUuid the UUID of the characteristic which should be present
	 * @param callback the callback on which the onError is called if the characteristic was not found.
	 *                 can be null if no error has to be reported, in which case the return value should
	 *                 be enough
	 * @return true if the device has the characteristic, false otherwise
	 */
	public boolean hasCharacteristic(String characteristicUuid, IBaseCallback callback) {
		if (_detectedCharacteristics.indexOf(characteristicUuid) == -1) {
			if (callback != null) {
				BleLog.LOGe(TAG, "characteristic not found");
				callback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
			}
			return false;
		}
		return true;
	}

	public boolean hasCommandCharacteristic(IBaseCallback callback) {
		return hasCharacteristic(BluenetConfig.CHAR_CONTROL_UUID, null);
	}

	/**
	 * Connect to the given device, once connection is established, discover the available
	 * services and characteristics. The connection will be kept open. Need to disconnect and
	 * close manually afterwards.
	 * @param address the MAC address of the device for which we want to discover the services
	 * @param callback the callback which will be notified about discovered services and
	 *                 characteristics
	 */
	public void connectAndDiscover(String address, final IDiscoveryCallback callback) {
		connect(address, new IStatusCallback() {
			@Override
			public void onSuccess() {
				/* [05.01.16] I am sometimes getting the behaviour that the connect first succeeds
				 *   and then a couple ms later I receive a disconnect again. In such a case, delaying
				 *   the discover leads to the library trying to discover services although a disconnect
				 *   was received in between.
				 *   If the delay is really necessary, we need to find a better solution
				 */
//				_handler.postDelayed(new Runnable() {
//					@Override
//					public void run() {
//						discoverServices(callback);
//					}
//				}, 500);
				discoverServices(callback);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Disconnect and close the device if disconnect was successful. See @disconnect and @close
	 * functions for details.
	 * @param clearCache set to true if device cache should be cleared on close. see @close for details
	 * @param callback the callback which will be notified about success or failure
	 * @return
	 */
	public boolean disconnectAndClose(boolean clearCache, final IStatusCallback callback) {
		checkConnectionState(BleDeviceConnectionState.connected, null);
//		if (!checkConnectionState(BleDeviceConnectionState.connected, callback)) return false;

		_connectionState = BleDeviceConnectionState.disconnecting;
		return _bleBase.disconnectAndCloseDevice(_targetAddress, clearCache, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				String status = BleCore.getStatus(json);
				if (status == "closed") {
					onDisconnect();
					// give the bluetooth adapter some time to settle after a close
					_handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							callback.onSuccess();
						}
					}, 300);
				} else if (status != "disconnected") {
					BleLog.LOGe(TAG, "wrong status received: %s", status);
				}
			}

			@Override
			public void onError(int error) {
				switch (error) {
					case BleErrors.ERROR_NEVER_CONNECTED:
					case BleErrors.ERROR_NOT_CONNECTED:
						_connectionState = BleDeviceConnectionState.initialized;
						break;
				}
				callback.onError(error);
			}
		});
	}

	public void requestPermissions(Activity activity) {
		_bleBase.requestPermissions(activity);
	}

	public boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, IStatusCallback callback) {
		return _bleBase.handlePermissionResult(requestCode, permissions, grantResults, callback);
	}

//	abstract class CallbackRunnable implements Runnable {
//		IStatusCallback _callback;
//
//		public void setCallback(IStatusCallback callback) {
//			_callback = callback;
//		}
//	}


	/**
	 * a runnable with a callback. if the runnable is called it disconnects and
	 * closes the device, then calls the callbacks onSuccess or onError function
	 * used in connectAndXXX functions, which call disconnect after a timeout, but if several
	 * functions are called in succession, only connects once and disconnects automatically
	 * after the last call
 	 */
	class DelayedDisconnectRunnable implements Runnable {
		IStatusCallback _callback;

		public void setCallback(IStatusCallback callback) {
			_callback = callback;
		}

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
	}

	// the runnable used for the delayed disconnect.
	private DelayedDisconnectRunnable _delayedDisconnect = null;

	/**
	 * Helper function to set a delayed disconnect. this will clear the previous delayed
	 * disconnect, then register a new delayed disconnect with the DELAYED_DISCONNECT_TIME timeout
	 * @param callback the callback which should be notified once the disconnect and close completed
	 */
	private void delayedDisconnect(IStatusCallback callback) {
		// remove the previous delayed disconnect
		clearDelayedDisconnect();
		// if a callback is provided (or no delayedDisconnect runnable available)
		if (callback != null || _delayedDisconnect == null) {
			// create and register a new delayed disconnect with the new callback
			_delayedDisconnect = new DelayedDisconnectRunnable();
			_delayedDisconnect.setCallback(callback);
		} // otherwise post the previous runnable again with the new timeout
		_handler.postDelayed(_delayedDisconnect, DELAYED_DISCONNECT_TIME);
	}

	private void clearDelayedDisconnect() {
		if (_delayedDisconnect != null) {
			_handler.removeCallbacks(_delayedDisconnect);
		}
	}

	/**
	 * Connects to the given device, discovers the available services, then executes the provided
	 * function, before disconnecting and closing the device again. Once everything completed, the
	 * callbacks onSuccess function is called.
	 * Note: the disconnect and close will be delayed, so consequent calls (within the timeout) to
	 * connectAndExecute functions will keep the connection alive until the last call expires
	 * @param address the MAC address of the device on which the function should be executed
	 * @param function the function to be executed, i.e. the object providing the execute function
	 *                 which should be executed
	 * @param callback the callback which should be notified once the connectAndExecute function
	 *                 completed (after closing the device, or if an error occurs)
	 */
	public void connectAndExecute(String address, final IExecuteCallback function, final IStatusCallback callback) {
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
				// todo: do we need to disconnect and close here?
//				disconnectAndClose(new IStatusCallback() {
//					@Override
//					public void onDeviceScanned() {}
//
//					@Override
//					public void onError(int error) {}
//				});
			}
		});
	}

	/**
	 * Check if we are currently connected to a device
	 * @param callback callback to be notified with an error if we are not connected. provide
	 *                 null if no notification is necessary, in which case the return value
	 *                 should be enough.
	 * @return true if device is connected, false otherwise
	 */
	public boolean isConnected(IBaseCallback callback) {
//		if (checkConnectionState(BleDeviceConnectionState.connected, callback)) {
			if (_bleBase.isDeviceConnected(_targetAddress)) {
				return true;
			} else {
				if (callback != null) {
					callback.onError(BleErrors.ERROR_NOT_CONNECTED);
				}
				return false;
			}
//		}
//		return false;
	}

	/**
	 * Helper function to check if we are already / still connected, and if a delayed disconnect is
	 * active, restart the delay.
	 * @return true if we are connected, false otherwise
	 * @param address
	 */
	public boolean checkConnection(String address) {
		if (isConnected(null) && _targetAddress.equals(address)) {
			if (_delayedDisconnect != null) {
				delayedDisconnect(null);
			}
			return true;
		}
		return false;
	}

	///////////////////
	// Power service //
	///////////////////

	/**
	 * Function to read the current PWM value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPwm(final IIntegerCallback callback) {
		if (isConnected(callback)) {
			BleLog.LOGd(TAG, "Reading current PWM value ...");
			if (hasStateCharacteristics(null)) {
				getState(BluenetConfig.STATE_SWITCH_STATE, 1, callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_PWM_UUID, callback)) {
				_bleBase.readPWM(_targetAddress, callback);
			}
		}
	}

	/**
	 * Function to read the current PWM value from the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 *
	 * @param address the MAC address of the device from which the PWM value should be read
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPwm(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Function to write the given PWM value to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the PWM value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writePwm(int value, IStatusCallback callback) {
		if (isConnected(callback)) {
			BleLog.LOGd(TAG, "Set PWM to %d", value);
			if (hasCommandCharacteristic(null)) {
				BleLog.LOGd(TAG, "use control characteristic");
				_bleBase.sendCommand(_targetAddress, new BleCommand(BluenetConfig.CMD_PWM, 1, new byte[]{(byte) value}), callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_PWM_UUID, callback)) {
				_bleBase.writePWM(_targetAddress, value, callback);
			}
		}
	}

	/**
	 * Function to write the given PWM value to the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param address the MAC address of the device to which the PWM value should be written
	 * @param value the PWM value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writePwm(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Toggle power between ON (pwm = 255) and OFF (pwm = 0). Reads first the current PW<
	 * value from the device, then switches the PWM value accordingly.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback callback which will be informed about success or failure
	 */
	public void togglePower(final IStatusCallback callback) {
		readPwm(new IIntegerCallback() {
			@Override
			public void onSuccess(int result) {
				if (result > 0) {
					writePwm(0, callback);
				} else {
					writePwm(255, callback);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Toggle power between ON (pwm = 255) and OFF (pwm = 0). Reads first the current PW<
	 * value from the device, then switches the PWM value accordingly.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback callback which will be informed about success or failure
	 */
	public void togglePower(String address, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Helper function to set power ON (sets pwm value to 255)
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void powerOn(IStatusCallback callback) {
		writePwm(255, callback);
	}

	/**
	 * Helper function to set power ON (sets pwm value to 255)
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will be informed about success or failure
	 */
	public void powerOn(String address, final IStatusCallback callback) {
		writePwm(address, 255, callback);
	}

	/**
	 * Helper function to set power OFF (sets pwm value to 0)
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void powerOff(IStatusCallback callback) {
		writePwm(0, callback);
	}

	/**
	 * Helper function to set power OFF (sets pwm value to 0)
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will be informed about success or failure
	 */
	public void powerOff(String address, final IStatusCallback callback) {
		writePwm(address, 0, callback);
	}

	private void getState(int type, final int len, final IIntegerCallback callback) {
		_bleBase.getState(_targetAddress, type, new IStateCallback() {
			@Override
			public void onSuccess(BleState state) {
				if (state.getLength() == len) {
					switch (len) {
					case 1:
						callback.onSuccess(BleUtils.toUint8(state.getPayload()[0]));
						return;
					case 2:
						callback.onSuccess(BleUtils.byteArrayToShort(state.getPayload()));
						return;
					case 4:
						callback.onSuccess(BleUtils.byteArrayToInt(state.getPayload()));
						return;
					}
				} else {
					callback.onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Function to read the current consumption value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPowerConsumption(final IIntegerCallback callback) {
		if (isConnected(callback)) {
			BleLog.LOGd(TAG, "Reading power consumption value ...");
			if (hasStateCharacteristics(null)) {
				getState(BluenetConfig.STATE_POWER_USAGE, 4, callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_POWER_CONSUMPTION_UUID, callback)) {
				_bleBase.readPowerConsumption(_targetAddress, callback);
			}
		}
	}

	/**
	 * Function to read the current consumption value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPowerConsumption(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
			readPowerConsumption(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readPowerConsumption(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
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

	/**
	 * Function to read the current curve from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPowerSamples(final IByteArrayCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_POWER_SAMPLES_UUID, callback)) {
			BleLog.LOGd(TAG, "Reading CurrentCurve value ...");
			_bleBase.readPowerSamples(_targetAddress, callback);
		}
	}

	/**
	 * Function to read the current curve from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPowerSamples(String address, final IByteArrayCallback callback) {
		if (checkConnection(address)) {
			readPowerSamples(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readPowerSamples(new IByteArrayCallback() {
						@Override
						public void onSuccess(byte[] result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
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

	/**
	 * Function to write the given reset value to the device. This will reset the device, and
	 * the behaviour after the reset depends on the given value. see @BluenetTypes for possible
	 * values
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	private void writeReset(int value, IStatusCallback callback) {
		if (isConnected(callback)) {
			BleLog.LOGd(TAG, "Set Reset to %d", value);
			if (hasCommandCharacteristic(null)) {
				BleLog.LOGd(TAG, "use control characteristic");
				_bleBase.sendCommand(_targetAddress, new BleCommand(BluenetConfig.CMD_RESET, 1, new byte[]{(byte) value}), callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_RESET_UUID, callback)) {
				_bleBase.writeReset(_targetAddress, value, callback);
			}
		}
	}

	/**
	 * Function to write the given reset value to the device. This will reset the device, and
	 * the behaviour after the reset depends on the given value. see @BluenetTypes for possible
	 * values
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	private void writeReset(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Function to reset / reboot the device
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetDevice(IStatusCallback callback) {
		writeReset(BluenetConfig.RESET_DEFAULT, callback);
	}

	/**
	 * Function to reset / reboot the device
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetDevice(String address, final IStatusCallback callback) {
		writeReset(address, BluenetConfig.RESET_DEFAULT, callback);
	}

	/**
	 * Function to reset / reboot the device to the bootloader, so that a new device firmware
	 * can be uploaded
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetToBootloader(IStatusCallback callback) {
		writeReset(BluenetConfig.RESET_DFU, callback);
	}

	/**
	 * Function to reset / reboot the device to the bootloader, so that a new device firmware
	 * can be uploaded
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetToBootloader(String address, final IStatusCallback callback) {
		writeReset(address, BluenetConfig.RESET_DFU, callback);
	}

	/**
	 * Function to read the current temperature value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTemperature(IIntegerCallback callback) {
		if (isConnected(callback)) {
			BleLog.LOGd(TAG, "Reading Temperature value ...");
			if (hasStateCharacteristics(null)) {
				getState(BluenetConfig.STATE_TEMPERATURE, 4, callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_TEMPERATURE_UUID, callback)) {
				_bleBase.readTemperature(_targetAddress, callback);
			}
		}
	}

	/**
	 * Function to read the current temperature value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTemperature(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Function to write the given mesh message to the device. the mesh message will be
	 * forwarded by the device into the mesh network
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the message to be sent to the mesh (through the device)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeMeshMessage(BleMeshMessage value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_MESH_CONTROL_UUID, callback)) {
			BleLog.LOGd(TAG, "Set MeshMessage to %s", value.toString());
			_bleBase.writeMeshMessage(_targetAddress, value, callback);
		}
	}

	/**
	 * Function to write the given mesh message to the device. the mesh message will be
	 * forwarded by the device into the mesh network
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the message to be sent to the mesh (through the device)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeMeshMessage(String address, final BleMeshMessage value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Helper function to check if the device has the configuration characteristics. to enable
	 * configuration / settings, the device needs to have all following three characteristics:
	 * 		* CHAR_SELECT_CONFIGURATION_UUID (to select the configuration to be read)
	 * 		* CHAR_GET_CONFIGURATION_UUID (to read the value of the configuration previously selected)
	 * 		* CHAR_SET_CONFIGURATION_UUID (to set a new configuration value)
	 * if one of the characteristics is missing, configuration is not available
	 * @param callback the callback to be informed about an error
	 * @return true if configuration characteristics are available, false otherwise
	 */
	public boolean hasConfigurationCharacteristics(IBaseCallback callback) {
		return hasCharacteristic(BluenetConfig.CHAR_CONFIG_CONTROL_UUID, callback) &&
				hasCharacteristic(BluenetConfig.CHAR_CONFIG_READ_UUID, callback);
	}

	public boolean hasStateCharacteristics(IBaseCallback callback) {
		return hasCharacteristic(BluenetConfig.CHAR_STATE_CONTROL_UUID, callback) &&
				hasCharacteristic(BluenetConfig.CHAR_STATE_READ_UUID, callback);
	}

//	public void writeConfiguration(BleConfiguration value, IStatusCallback callback) {
//		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
//			BleCore.LOGd(TAG, "Set Configuration to %s", value.toString());
//			_bleBase.writeConfiguration(_targetAddress, value, callback);
//		}
//	}

//	public void writeConfiguration(String address, final BleConfiguration value, final IStatusCallback callback) {
//		if (checkConnection(null)) {
//			writeConfiguration(value, callback);
//		} else {
//			connectAndExecute(address, new IExecuteCallback() {
//				@Override
//				public void execute(final IStatusCallback execCallback) {
//					writeConfiguration(value, new IStatusCallback() {
//						@Override
//						public void onDeviceScanned() {
//							callback.onDeviceScanned();
//							execCallback.onDeviceScanned();
//						}
//
//						@Override
//						public void onError(int error) {
//							execCallback.onDeviceScanned();
//						}
//					});
//				}
//			}, new IStatusCallback() {
//				@Override
//				public void onDeviceScanned() { /* don't care */ }
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
//			BleCore.LOGd(TAG, "Reading Configuration value ...");
//			_bleBase.getConfiguration(_targetAddress, configurationType, callback);
//		}
//	}

//	public void readConfiguration(String address, final int configurationType, final IConfigurationCallback callback) {
//		if (checkConnection(null)) {
//			readConfiguration(configurationType, callback);
//		} else {
//			connectAndExecute(address, new IExecuteCallback() {
//				@Override
//				public void execute(final IStatusCallback execCallback) {
//					readConfiguration(configurationType, new IConfigurationCallback() {
//						@Override
//						public void onDeviceScanned(BleConfiguration result) {
//							callback.onDeviceScanned(result);
//							execCallback.onDeviceScanned();
//						}
//
//						@Override
//						public void onError(int error) {
//							execCallback.onDeviceScanned();
//						}
//					});
//				}
//			}, new IStatusCallback() {
//				@Override
//				public void onDeviceScanned() { /* don't care */ }
//
//				@Override
//				public void onError(int error) {
//					callback.onError(error);
//				}
//			});
//		}
//	}

	/**
	 * Write the given device name to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new device name
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setDeviceName(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set DeviceName to %s", value);
			_bleBase.setDeviceName(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given device name to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new device name
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setDeviceName(String address, final String value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current device name from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the device name on success, or an error otherwise
	 */
	public void getDeviceName(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get DeviceName ...");
			_bleBase.getDeviceName(_targetAddress, callback);
		}
	}

	/**
	 * Read the current device name from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the device name on success, or an error otherwise
	 */
	public void getDeviceName(String address, final IStringCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current beacon major from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMajor(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Reading BeaconMajor value ...");
			_bleBase.getBeaconMajor(_targetAddress, callback);
		}
	}

	/**
	 * Read the current beacon major from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMajor(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given beacon major to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new beacon major
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMajor(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set BeaconMajor to %d", value);
			_bleBase.setBeaconMajor(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given beacon major to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new beacon major
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMajor(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current beacon minor from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMinor(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get BeaconMinor ...");
			_bleBase.getBeaconMinor(_targetAddress, callback);
		}
	}

	/**
	 * Read the current beacon minor from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMinor(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given beacon minor to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new beacon minor
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMinor(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set BeaconMinor to %d", value);
			_bleBase.setBeaconMinor(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given beacon minor to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new beacon minor
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMinor(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current beacon proximity uuid from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconProximityUuid(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get BeaconProximityUuid ...");
			_bleBase.getBeaconProximityUuid(_targetAddress, callback);
		}
	}

	/**
	 * Read the current beacon proximity uuid from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconProximityUuid(String address, final IStringCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given beacon proximity UUID to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new beacon proximity UUID
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconProximityUuid(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set BeaconProximityUuid to %s", value);
			_bleBase.setBeaconProximityUuid(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given beacon proximity UUID to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new beacon proximity UUID
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconProximityUuid(String address, final String value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current beacon calibrated rssi (rssi value at 1 m) from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconCalibratedRssi(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get BeaconCalibratedRssi ...");
			_bleBase.getBeaconCalibratedRssi(_targetAddress, callback);
		}
	}

	/**
	 * Read the current beacon calibrated rssi (rssi value at 1 m) from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconCalibratedRssi(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given beacon calibrated rssi to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new beacon calibrated rssi
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconCalibratedRssi(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set BeaconCalibratedRssi to %d", value);
			_bleBase.setBeaconCalibratedRssi(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given beacon calibrated rssi to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new beacon calibrated rssi
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconCalibratedRssi(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current device type from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getDeviceType(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get DeviceType ...");
			_bleBase.getDeviceType(_targetAddress, callback);
		}
	}

	/**
	 * Read the current device type from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getDeviceType(String address, final IStringCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given device type to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new device type
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setDeviceType(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set DeviceType to %s", value);
			_bleBase.setDeviceType(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given device type to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new device type
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setDeviceType(String address, final String value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current floor from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getFloor(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get Floor ...");
			_bleBase.getFloor(_targetAddress, callback);
		}
	}

	/**
	 * Read the current floor from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getFloor(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given floor to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new floor
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setFloor(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set Floor to %s", value);
			_bleBase.setFloor(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given floor to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new floor
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setFloor(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current room from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getRoom(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get Room ...");
			_bleBase.getRoom(_targetAddress, callback);
		}
	}

	/**
	 * Read the current room from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getRoom(String address, final IStringCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given room to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new room
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setRoom(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set Room to %s", value);
			_bleBase.setRoom(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given room to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new room
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setRoom(String address, final String value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current tx power from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getTxPower(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get TxPower ...");
			_bleBase.getTxPower(_targetAddress, callback);
		}
	}

	/**
	 * Read the current tx power from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getTxPower(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given tx power to the configuration. This can be one of the following values:
	 *  -30, -20, -16, -12, -8, -4, 0, or 4
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new tx power
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setTxPower(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set TxPower to %d", value);
			_bleBase.setTxPower(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given tx power to the configuration. This can be one of the following values:
	 *  -30, -20, -16, -12, -8, -4, 0, or 4
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new tx power
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setTxPower(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current advertisement interval from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getAdvertisementInterval(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get AdvertisementInterval ...");
			_bleBase.getAdvertisementInterval(_targetAddress, callback);
		}
	}

	/**
	 * Read the current advertisement interval from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getAdvertisementInterval(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given advertisement interval to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new advertisement interval
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setAdvertisementInterval(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set AdvertisementInterval to %d", value);
			_bleBase.setAdvertisementInterval(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given advertisement interval to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new advertisement interval
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setAdvertisementInterval(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given wifi value to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new wifi value
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setWifi(String value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set Wifi to %s", value);
			_bleBase.setWifi(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given wifi value to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new wifi value
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setWifi(String address, final String value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current ip from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getIp(IStringCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get Ip ...");
			// todo: continue here
			_bleBase.getIp(_targetAddress, callback);
		}
	}

	/**
	 * Read the current ip from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getIp(String address, final IStringCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current minimum environment temperature from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMinEnvTemp(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get minimum environment temperature...");
			_bleBase.getMinEnvTemp(_targetAddress, callback);
		}
	}

	/**
	 * Read the current minimum environment temperature from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMinEnvTemp(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given minimum environment temperature to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new minimum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMinEnvTemp(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set minimum environment temperature to %d", value);
			_bleBase.setMinEnvTemp(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given minimum environment temperature to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new minimum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMinEnvTemp(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Read the current maximum environment temperature from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMaxEnvTemp(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get maximum environment temperature...");
			_bleBase.getMaxEnvTemp(_targetAddress, callback);
		}
	}

	/**
	 * Read the current maximum environment temperature from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMaxEnvTemp(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Write the given maximum environment temperature to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new maximum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMaxEnvTemp(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set maximum environment temperature to %d", value);
			_bleBase.setMaxEnvTemp(_targetAddress, value, callback);
		}
	}

	/**
	 * Write the given maximum environment temperature to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new maximum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMaxEnvTemp(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Function to read the current limit value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getCurrentLimit(IIntegerCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Reading CurrentLimit value ...");
			_bleBase.getCurrentLimit(_targetAddress, callback);
		}
	}

	/**
	 * Function to read the current limit value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getCurrentLimit(String address, final IIntegerCallback callback) {
		if (checkConnection(address)) {
			getCurrentLimit(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getCurrentLimit(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
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

	/**
	 * Function to write the given current limit to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setCurrentLimit(int value, IStatusCallback callback) {
		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set CurrentLimit to %d", value);
			_bleBase.setCurrentLimit(_targetAddress, value, callback);
		}
	}

	/**
	 * Function to write the given current limit to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setCurrentLimit(String address, final int value, final IStatusCallback callback) {
		if (checkConnection(address)) {
			setCurrentLimit(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setCurrentLimit(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
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

	/**
	 * Function to read the list of tracked devices from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTrackedDevices(IByteArrayCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_TRACKED_DEVICES_UUID, callback)) {
			BleLog.LOGd(TAG, "Reading TrackedDevices value ...");
			_bleBase.readTrackedDevices(_targetAddress, callback);
		}
	}

	/**
	 * Function to read the list of tracked devices from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTrackedDevices(String address, final IByteArrayCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Function to add a new device to be tracked
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new device to be tracked
	 * @param callback the callback which will be informed about success or failure
	 */
	public void addTrackedDevice(BleTrackedDevice value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_TRACK_CONTROL_UUID, callback)) {
			BleLog.LOGd(TAG, "Set TrackedDevice to %s", value.toString());
			_bleBase.addTrackedDevice(_targetAddress, value, callback);
		}
	}

	/**
	 * Function to add a new device to be tracked
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new device to be tracked
	 * @param callback the callback which will be informed about success or failure
	 */
	public void addTrackedDevice(String address, final BleTrackedDevice value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Function to get the list of scanned BLE devices from the device. Need to call writeScanDevices
	 * first to start and to stop the scan. Consider using @scanForDevices instead
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void listScannedDevices(IByteArrayCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_SCANNED_DEVICES_UUID, callback)) {
			BleLog.LOGd(TAG, "List scanned devices ...");
			_bleBase.listScannedDevices(_targetAddress, callback);
		}
	}

	/**
	 * Function to get the list of scanned BLE devices from the device. Need to call writeScanDevices
	 * first to start and to stop the scan. Consider using @scanForDevices instead
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void listScannedDevices(String address, final IByteArrayCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Function to start / stop a scan for BLE devices. After starting a scan, it will run indefinite
	 * until this function is called again to stop it.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value true to start scanning for devices, false to stop the scan
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeScanDevices(boolean value, IStatusCallback callback) {
		if (isConnected(callback)) {
			BleLog.LOGd(TAG, "Scan Devices: %b", value);
			if (hasCommandCharacteristic(null)) {
				BleLog.LOGd(TAG, "use control characteristic");
				int scan = (value ? 1 : 0);
				_bleBase.sendCommand(_targetAddress, new BleCommand(BluenetConfig.CMD_SCAN_DEVICES, 1, new byte[]{(byte) scan}), callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_SCAN_CONTROL_UUID, callback)) {
				_bleBase.scanDevices(_targetAddress, value, callback);
			}
		}
	}

	/**
	 * Function to start / stop a scan for BLE devices. After starting a scan, it will run indefinite
	 * until this function is called again to stop it.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value true to start scanning for devices, false to stop the scan
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeScanDevices(String address, final boolean value, final IStatusCallback callback) {
		if (checkConnection(address)) {
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
							execCallback.onError(error);
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

	/**
	 * Function to start a scan for devices, stop it again after scanDuration expired, then
	 * return the list of devices
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param scanDuration the duration (in ms) for which the device should scan for other BLE
	 *                     devices
	 * @param callback the callback which will return the list of scanned devices
	 */
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

	/**
	 * Function to start a scan for devices, stop it again after scanDuration expired, then
	 * return the list of devices
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param scanDuration the duration (in ms) for which the device should scan for other BLE
	 *                     devices
	 * @param callback the callback which will return the list of scanned devices
	 */
	public void scanForDevices(final String address, final int scanDuration, final IByteArrayCallback callback) {
		if (checkConnection(address)) {
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
//																	callback.onError(error);
																	// also disconnect if an error occurs
																	stopExecCallback.onError(error);
																}
															});
														}

														@Override
														public void onError(int error) {
//															callback.onError(error);
															// disconnect if an error occurs
															stopExecCallback.onError(error);
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
//								callback.onError(error);
								// disconnect if an error occurs
								startExecCallback.onError(error);
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

	public void readAlert(final IAlertCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_NEW_ALERT_UUID, callback)) {
			BleLog.LOGd(TAG, "Reading Alert value ...");
			_bleBase.readAlert(_targetAddress, callback);
		}
	}

	public void readAlert(String address, final IAlertCallback callback) {
		if (checkConnection(address)) {
			readAlert(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readAlert(new IAlertCallback() {
						@Override
						public void onSuccess(BleAlertState result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
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

	public void resetAlert(IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_NEW_ALERT_UUID, callback)) {
			BleLog.LOGd(TAG, "Reset Alert");
			_bleBase.writeAlert(_targetAddress, 0, callback);
		}
	}

	public void resetAlert(String address, final IStatusCallback callback) {
		if (checkConnection(null)) {
			resetAlert(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					resetAlert(new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void readMeshData(final IMeshDataCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, callback)) {
			BleLog.LOGd(TAG, "subscribe to mesh data");
			_bleBase.readMeshData(_targetAddress, callback);
		}
	}

	public void subscribeMeshData(final IMeshDataCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, callback)) {
			BleLog.LOGd(TAG, "subscribe to mesh data");
			_bleBase.subscribeMeshData(_targetAddress, callback);
		}
	}

	public void unsubscribeMeshData(final IMeshDataCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, callback)) {
			BleLog.LOGd(TAG, "unsubscribe from mesh data");
			_bleBase.unsubscribeMeshData(_targetAddress, callback);
		}
	}

	/////////////////////////////////////////////////////////////////////////////////////

/*
	public void readXXX(ICallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.YYY, callback)) {
			BleCore.LOGd(TAG, "Reading XXX value ...");
			_bleBase.readXXX(_targetAddress, callback);
		}
	}

	public void readXXX(String address, final ICallback callback) {
		if (checkConnection(null)) {
			readXXX(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readXXX(new ICallback() {
						@Override
						public void onSuccess(zzz result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void writeXXX(zzz value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.YYY, callback)) {
			BleCore.LOGd(TAG, "Set XXX to %uuu", value);
			_bleBase.writeXXX(_targetAddress, value, callback);
		}
	}

	public void writeXXX(String address, final zzz value, final IStatusCallback callback) {
		if (checkConnection(null)) {
			writeXXX(value, callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					writeXXX(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

*/

}
