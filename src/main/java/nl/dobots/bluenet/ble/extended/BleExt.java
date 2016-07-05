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
import nl.dobots.bluenet.ble.base.callbacks.IBooleanCallback;
import nl.dobots.bluenet.ble.base.callbacks.IMeshDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IPowerSamplesCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStateCallback;
import nl.dobots.bluenet.ble.base.structs.AlertState;
import nl.dobots.bluenet.ble.base.structs.CommandMsg;
import nl.dobots.bluenet.ble.base.structs.PowerSamples;
import nl.dobots.bluenet.ble.base.structs.StateMsg;
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
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceMap;
import nl.dobots.bluenet.ble.base.structs.MeshMsg;
import nl.dobots.bluenet.ble.base.structs.TrackedDeviceMsg;
import nl.dobots.bluenet.utils.BleLog;

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

	private BleExtState _bleExtState;

	public BleExt() {
		_bleBase = new BleBase();

		_bleExtState = new BleExtState(this);

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

	public BleExtState getBleExtState() { return _bleExtState; }

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
		_handler.removeCallbacksAndMessages(null);
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
//					BleLog.LOGe(TAG, "Failed to parse json into device! Err: " + e.getMessage());
//					BleLog.LOGd(TAG, "json: " + json.toString());
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
//				_bleBase.reconnectDevice(_targetAddress, 30, dataCallback);
				_bleBase.connectDevice(_targetAddress, CONNECT_TIMEOUT, dataCallback);
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
//			BleLog.LOGe(TAG, "wrong connection state: %s instead of %s", _connectionState.toString(), state.toString());
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
//		BleLog.LOGd(TAG, "discovered characteristic: %s", characteristicUuid);
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

	/**
	 * Helper function to check if the device has the configuration characteristics. to enable
	 * configuration / settings, the device needs to have the following characteristics:
	 * 		* CHAR_CONFIG_COTNROL_UUID (to select the configuration to be read or to write a new value
	 * 							   to the configuration)
	 * 		* CHAR_CONFIG_READ_UUID (to read the value of the configuration previously selected)
	 * if one of the characteristics is missing, configuration is not available
	 * @param callback the callback to be informed about an error
	 * @return true if configuration characteristics are available, false otherwise
	 */
	public boolean hasConfigurationCharacteristics(IBaseCallback callback) {
		return hasCharacteristic(BluenetConfig.CHAR_CONFIG_CONTROL_UUID, callback) &&
				hasCharacteristic(BluenetConfig.CHAR_CONFIG_READ_UUID, callback);
	}

	/**
	 * Helper function to check if the device has the state characteristics. to read state variables,
	 * the device needs to have the following characteristics:
	 * 		* CHAR_STATE_COTNROL_UUID (to select the state to be read)
	 * 		* CHAR_STATE_READ_UUID (to read the value of the state previously selected)
	 * if one of the characteristics is missing, state variables are not available
	 * @param callback the callback to be informed about an error
	 * @return true if state characteristics are available, false otherwise
	 */
	public boolean hasStateCharacteristics(IBaseCallback callback) {
		return hasCharacteristic(BluenetConfig.CHAR_STATE_CONTROL_UUID, callback) &&
				hasCharacteristic(BluenetConfig.CHAR_STATE_READ_UUID, callback);
	}

	/**
	 * Helper function to check if the command control characteristic is avialable
	 * @param callback the callback to be informed about an error
	 * @return true if control characteristic is available, false otherwise
	 */
	public boolean hasControlCharacteristic(IBaseCallback callback) {
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
				_handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						discoverServices(callback);
					}
				}, 500);
//				discoverServices(callback);
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
	public synchronized boolean disconnectAndClose(boolean clearCache, final IStatusCallback callback) {
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

	/**
	 * Request permission to access BLE (locations) on Android > 6.0
	 * @param activity activity which will be notified about the permission request result. The
	 *                 activity will need to implement the onRequestPermissionsResult function and
	 *                 the library function @handlePermissionResult.
	 */
	public void requestPermissions(Activity activity) {
		_bleBase.requestPermissions(activity);
	}

	/**
	 * Helper function which will handle the result of the permission request. call this function
	 * from the activity's onActivityResult function
	 * @param requestCode The request code, received in onRequestPermissionResult
	 * @param permissions The requested permissions, received in onRequestPermissionResult
	 * @param grantResults The grant results for the corresponding permissions
	 *     which is either {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
	 *     or {@link android.content.pm.PackageManager#PERMISSION_DENIED}. Never null.
	 * @param callback the callback function which will be informed about success or failure of the
	 *                 permission request
	 */
	public boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, IStatusCallback callback) {
		return _bleBase.handlePermissionResult(requestCode, permissions, grantResults, callback);
	}

	public Handler getHandler() {
		return _handler;
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
		public synchronized void run() {
			BleLog.LOGd(TAG, "delayed disconnect timeout");
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
		BleLog.LOGd(TAG, "delay disconnect");
		// remove the previous delayed disconnect
		clearDelayedDisconnect();
		// if a callback is provided (or no delayedDisconnect runnable available)
		if (callback != null || _delayedDisconnect == null) {
			// create and register a new delayed disconnect with the new callback
			_delayedDisconnect = new DelayedDisconnectRunnable();
//			_delayedDisconnect.setCallback(callback);
		} // otherwise post the previous runnable again with the new timeout
		_handler.postDelayed(_delayedDisconnect, DELAYED_DISCONNECT_TIME);
	}

	/**
	 * Clear the delayed disconnect, either when the timer expires, or if the device
	 * disconnects for another reason
	 */
	private boolean clearDelayedDisconnect() {
		if (_delayedDisconnect != null) {
			BleLog.LOGd(TAG, "delay disconnect remove callbacks");
			_handler.removeCallbacks(_delayedDisconnect);
			return true;
		} else {
			return false;
		}
	}

	private static final int MAX_RETRIES = 3;
	private int _retries = 0;

	private boolean retry(final String address, final IExecuteCallback function, final IStatusCallback callback) {

		if (_retries < MAX_RETRIES) {
			_retries++;
			BleLog.LOGw(TAG, "retry: %d", _retries);
			connectAndExecute(address, function, callback);
			_retries = 0;
			return true;
		} else {
			_retries = 0;
			return false;
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
	public void connectAndExecute(final String address, final IExecuteCallback function, final IStatusCallback callback) {
		final boolean resumeDelayedDisconnect = clearDelayedDisconnect();
		if (checkConnection(address)) {
			function.execute(new IStatusCallback() {
				@Override
				public void onSuccess() {
					if (resumeDelayedDisconnect) {
						delayedDisconnect(null);
					}
					callback.onSuccess();
				}

				@Override
				public void onError(final int error) {
					if (error == BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND) {
						if (resumeDelayedDisconnect) {
							delayedDisconnect(null);
						}
						callback.onError(error);
					} else {
						if (!retry(address, function, callback)) {
							if (resumeDelayedDisconnect) {
								delayedDisconnect(null);
							}
							callback.onError(error);
						}
					}
//						delayedDisconnect(new IStatusCallback() {
//							@Override
//							public void onSuccess() {
//								if (!retry(address, function, callback)) {
//									callback.onError(error);
//								}
//							}
//
//							@Override
//							public void onError(int e) {
////								callback.onError(error);
//								if (!retry(address, function, callback)) {
//									callback.onError(error);
//								}
//							}
//						});
//						callback.onError(error);
				}
			});
		} else {
			connectAndDiscover(address, new IDiscoveryCallback() {
				@Override
				public void onDiscovery(String serviceUuid, String characteristicUuid) { /* don't care */ }

				@Override
				public void onSuccess() {

					// call execute function
					function.execute(new IStatusCallback() {
						@Override
						public void onSuccess() {
							delayedDisconnect(null);
							callback.onSuccess();
						}

						@Override
						public void onError(final int error) {
							if (!retry(address, function, callback)) {
								delayedDisconnect(null);
								callback.onError(error);
							}
//						delayedDisconnect(new IStatusCallback() {
//							@Override
//							public void onSuccess() {
//								if (!retry(address, function, callback)) {
//									callback.onError(error);
//								}
//							}
//
//							@Override
//							public void onError(int e) {
////								callback.onError(error);
//								if (!retry(address, function, callback)) {
//									callback.onError(error);
//								}
//							}
//						});
//						callback.onError(error);
						}
					});
				}

				@Override
				public void onError(final int error) {
					// todo: do we need to disconnect and close here?
					disconnectAndClose(true, new IStatusCallback() {
						@Override
						public void onSuccess() {
							if (!retry(address, function, callback)) {
								callback.onError(error);
							}
						}

						@Override
						public void onError(int e) {
							if (!retry(address, function, callback)) {
								callback.onError(error);
							}
						}
					});
				}
			});
		}
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
			if (checkConnectionState(BleDeviceConnectionState.connected, null) &&
					_bleBase.isDeviceConnected(_targetAddress)) {
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
	public synchronized boolean checkConnection(String address) {
		if (isConnected(null) && _targetAddress.equals(address)) {
			if (_delayedDisconnect != null) {
//				delayedDisconnect(null);
				clearDelayedDisconnect();
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
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				if (isConnected(callback)) {
					BleLog.LOGd(TAG, "Reading current PWM value ...");
					if (hasStateCharacteristics(null)) {
						_bleExtState.getSwitchState(_targetAddress, callback);
					} else if (hasCharacteristic(BluenetConfig.CHAR_PWM_UUID, callback)) {
						_bleBase.readPWM(_targetAddress, callback);
					}
				}
			}
		});
	}

	/**
	 * Function to read the current PWM value from the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 *
	 * @param address the MAC address of the device from which the PWM value should be read
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPwm(final String address, final IIntegerCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				BleLog.LOGd(TAG, "Reading current PWM value ...");
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
		});
	}

	/**
	 * Function to write the given PWM value to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the PWM value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writePwm(final int value, final IStatusCallback callback) {
		if (isConnected(callback)) {
			BleLog.LOGd(TAG, "Set PWM to %d", value);
			if (hasControlCharacteristic(null)) {
				BleLog.LOGd(TAG, "use control characteristic");
				_bleBase.sendCommand(_targetAddress, new CommandMsg(BluenetConfig.CMD_PWM, 1, new byte[]{(byte) value}), callback);
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
	public void writePwm(final String address, final int value, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				BleLog.LOGd(TAG, "Set PWM to %d", value);
//				if (checkConnection(address)) {
//					writePwm(value, callback);
//				} else {
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
//				}
			}
		});
	}

	/**
	 * Function to read the current relay value from the device.
	 * callback returns true if relay is on, false otherwise
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readRelay(final IBooleanCallback callback) {
		if (isConnected(callback)) {
			BleLog.LOGd(TAG, "Reading current Relay value ...");
			if (hasStateCharacteristics(null)) {
				_bleExtState.getSwitchState(_targetAddress, new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						callback.onSuccess(result > 0);
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
			} else if (hasCharacteristic(BluenetConfig.CHAR_RELAY_UUID, callback)) {
				_bleBase.readRelay(_targetAddress, callback);
			}
		}
	}

	/**
	 * Function to read the current relay value from the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 * callback returns true if relay is on, false otherwise
	 *
	 * @param address the MAC address of the device from which the Relay value should be read
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readRelay(String address, final IBooleanCallback callback) {
		BleLog.LOGd(TAG, "Reading current Relay value ...");
		if (checkConnection(address)) {
			readRelay(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readRelay(new IBooleanCallback() {
						@Override
						public void onSuccess(boolean result) {
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
	 * Function to write the given Relay value to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param relayOn true if the relay should be switched on, false otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeRelay(final boolean relayOn, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				if (isConnected(callback)) {
					BleLog.LOGd(TAG, "Set Relay to %b", relayOn);
					if (hasControlCharacteristic(null)) {
						BleLog.LOGd(TAG, "use control characteristic");
						int value = relayOn ? 100 : 0;
						_bleBase.sendCommand(_targetAddress, new CommandMsg(BluenetConfig.CMD_RELAY, 1, new byte[]{(byte) value}), callback);
					} else if (hasCharacteristic(BluenetConfig.CHAR_RELAY_UUID, callback)) {
						_bleBase.writeRelay(_targetAddress, relayOn, callback);
					}
				}
			}
		});
	}

	/**
	 * Function to write the given Relay value to the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param address the MAC address of the device to which the Relay value should be written
	 * @param relayOn true if the relay should be switched on, false otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeRelay(final String address, final boolean relayOn, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				BleLog.LOGd(TAG, "Set Relay to %b", relayOn);
				if (checkConnection(address)) {
					writeRelay(relayOn, callback);
				} else {
					connectAndExecute(address, new IExecuteCallback() {
						@Override
						public void execute(final IStatusCallback execCallback) {
							writeRelay(relayOn, new IStatusCallback() {
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
		});
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
		BleLog.LOGd(TAG, "Toggle power ...");
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

	/**
	 * Helper function to set power ON (sets pwm value to 255)
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void relayOn(IStatusCallback callback) {
		writeRelay(true, callback);
	}

	/**
	 * Helper function to set power ON (sets pwm value to 255)
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will be informed about success or failure
	 */
	public void relayOn(String address, final IStatusCallback callback) {
		writeRelay(address, true, callback);
	}

	/**
	 * Helper function to set power OFF (sets pwm value to 0)
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void relayOff(IStatusCallback callback) {
		writeRelay(false, callback);
	}

	/**
	 * Helper function to set power OFF (sets pwm value to 0)
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will be informed about success or failure
	 */
	public void relayOff(String address, final IStatusCallback callback) {
		writeRelay(address, false, callback);
	}

//	/**
//	 * If permanently connected to a device, state notifications can be requested. this means that
//	 * as soon as a state variable changes on the device, a notification will be sent and the
//	 * callback will trigger with the new value
//	 * Note: there is no version of this function with address parameter, as this functionality is
//	 * only available if we are permanently connected. so it would make no sense to connect and
//	 * disconnect again afterwards
//	 * @param type type of state variable which should be notified, see @BleStateTypes
//	 * @param len for verification, provide length of the variable, 1 for byte, 2 for short, etc.
//	 * @param callback callback which should be informed about a new value update
//	 */
//	public void getStateNotifications(final int type, final int len, final IIntegerCallback callback) {
//		if (isConnected(callback) && hasStateCharacteristics(callback)) {
//			_bleBase.getStateNotifications(_targetAddress, type,
//					new IIntegerCallback() {
//						@Override
//						public void onSuccess(int result) {
//
//						}
//
//						@Override
//						public void onError(int error) {
//
//						}
//					},
//					new IStateCallback() {
//						@Override
//						public void onSuccess(StateMsg state) {
//							if (state.getLength() == len) {
//								callback.onSuccess(state.getValue());
//							} else {
//								callback.onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
//							}
//						}
//
//						@Override
//						public void onError(int error) {
//							callback.onError(error);
//						}
//					});
//		}
//	}
//
//	/**
//	 * Function to read the value of the given state variable from the device.
//	 *
//	 * Note: needs to be already connected or an error is created! Use overloaded function
//	 * with address otherwise
//	 * @param callback the callback which will get the read value on success, or an error otherwise
//	 */
//	private void getState(int type, final int len, final IIntegerCallback callback) {
//		if (isConnected(callback) && hasStateCharacteristics(callback)) {
//			_bleBase.getState(_targetAddress, type, new IStateCallback() {
//				@Override
//				public void onSuccess(StateMsg state) {
//					if (state.getLength() == len) {
//						callback.onSuccess(state.getValue());
//					} else {
//						callback.onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
//					}
//				}
//
//				@Override
//				public void onError(int error) {
//					callback.onError(error);
//				}
//			});
//		}
//	}
//
//	/**
//	 * Function to read the value of the given state variable from the device.
//	 *
//	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
//	 * @param address the MAC address of the device
//	 * @param callback the callback which will get the read value on success, or an error otherwise
//	 */
//	private void getState(String address, final int type, final int len, final IIntegerCallback callback) {
//		if (checkConnection(address)) {
//			getState(type, len, callback);
//		} else {
//			connectAndExecute(address, new IExecuteCallback() {
//				@Override
//				public void execute(final IStatusCallback execCallback) {
//					getState(type, len, new IIntegerCallback() {
//						@Override
//						public void onSuccess(int result) {
//							callback.onSuccess(result);
//							execCallback.onSuccess();
//						}
//
//						@Override
//						public void onError(int error) {
//							execCallback.onError(error);
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
				_bleExtState.getPowerUsage(_targetAddress, callback);
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
		BleLog.LOGd(TAG, "Reading power consumption value ...");
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
	public void readPowerSamples(final IPowerSamplesCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_POWER_SAMPLES_UUID, callback)) {
			BleLog.LOGd(TAG, "Reading PowerSamples value ...");
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
	public void readPowerSamples(String address, final IPowerSamplesCallback callback) {
		BleLog.LOGd(TAG, "Reading PowerSamples value ...");
		if (checkConnection(address)) {
			readPowerSamples(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readPowerSamples(new IPowerSamplesCallback() {
						@Override
						public void onData(PowerSamples result) {
							callback.onData(result);
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
			if (hasControlCharacteristic(null)) {
				BleLog.LOGd(TAG, "use control characteristic");
				_bleBase.sendCommand(_targetAddress, new CommandMsg(BluenetConfig.CMD_RESET, 1, new byte[]{(byte) value}), callback);
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
		BleLog.LOGd(TAG, "Set Reset to %d", value);
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
				_bleExtState.getTemperature(_targetAddress, callback);
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
		BleLog.LOGd(TAG, "Reading Temperature value ...");
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
	public void writeMeshMessage(MeshMsg value, IStatusCallback callback) {
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
	public void writeMeshMessage(String address, final MeshMsg value, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "Set MeshMessage to %s", value.toString());
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
			_bleBase.readTrackedDevices(getTargetAddress(), callback);
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
		BleLog.LOGd(TAG, "Reading TrackedDevices value ...");
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
	public void addTrackedDevice(TrackedDeviceMsg value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_TRACK_CONTROL_UUID, callback)) {
			BleLog.LOGd(TAG, "Set TrackedDevice to %s", value.toString());
			_bleBase.addTrackedDevice(getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to add a new device to be tracked
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new device to be tracked
	 * @param callback the callback which will be informed about success or failure
	 */
	public void addTrackedDevice(String address, final TrackedDeviceMsg value, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "Set TrackedDevice to %s", value.toString());
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
			_bleBase.listScannedDevices(getTargetAddress(), callback);
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
		BleLog.LOGd(TAG, "List scanned devices ...");
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
			if (hasControlCharacteristic(null)) {
				BleLog.LOGd(TAG, "use control characteristic");
				int scan = (value ? 1 : 0);
				_bleBase.sendCommand(getTargetAddress(), new CommandMsg(BluenetConfig.CMD_SCAN_DEVICES, 1, new byte[]{(byte) scan}), callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_SCAN_CONTROL_UUID, callback)) {
				_bleBase.scanDevices(getTargetAddress(), value, callback);
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
		BleLog.LOGd(TAG, "Scan Devices: %b", value);
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
		BleLog.LOGd(TAG, "Scan for devices ...");
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
			_bleBase.readAlert(getTargetAddress(), callback);
		}
	}

	public void readAlert(String address, final IAlertCallback callback) {
		BleLog.LOGd(TAG, "Reading Alert value ...");
		if (checkConnection(address)) {
			readAlert(callback);
		} else {
			connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					readAlert(new IAlertCallback() {
						@Override
						public void onSuccess(AlertState result) {
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
			_bleBase.writeAlert(getTargetAddress(), 0, callback);
		}
	}

	public void resetAlert(String address, final IStatusCallback callback) {
		BleLog.LOGd(TAG, "Reset Alert");
		if (checkConnection(address)) {
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
				public void onSuccess() {
				}

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
			_bleBase.readMeshData(getTargetAddress(), callback);
		}
	}

	public void subscribeMeshData(final IMeshDataCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, callback)) {
			BleLog.LOGd(TAG, "subscribe to mesh data");
			_bleBase.subscribeMeshData(getTargetAddress(), callback);
		}
	}

	public void unsubscribeMeshData(final IMeshDataCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, callback)) {
			BleLog.LOGd(TAG, "unsubscribe from mesh data");
			_bleBase.unsubscribeMeshData(getTargetAddress(), callback);
		}
	}

//	public void writeConfiguration(ConfigurationMsg value, IStatusCallback callback) {
//		if (isConnected(callback) && hasConfigurationCharacteristics(callback)) {
//			BleCore.LOGd(TAG, "Set Configuration to %s", value.toString());
//			_bleBase.writeConfiguration(_targetAddress, value, callback);
//		}
//	}

//	public void writeConfiguration(String address, final ConfigurationMsg value, final IStatusCallback callback) {
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
//						public void onDeviceScanned(ConfigurationMsg result) {
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

}
