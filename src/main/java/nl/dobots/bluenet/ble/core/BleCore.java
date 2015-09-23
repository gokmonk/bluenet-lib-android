package nl.dobots.bluenet.ble.core;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.utils.BleUtils;

/*
 * TODO: - implement for multiple connected devices
 */

public class BleCore {

	private static final String TAG = BleCore.class.getCanonicalName();

	// Timeout for a bluetooth enable request. If timeout expires, an error is created
	private static final int BLUETOOTH_ENABLE_TIMEOUT = 15000;

	// bluetooth adapter used for ble calls
	private BluetoothAdapter _bluetoothAdapter;

	// context which tries to initialize the bluetooth adapter
	private Context _context;

	// Bluetooth Scanner objects for API > 21
	private BluetoothLeScanner _leScanner;
	private ArrayList<ScanFilter> _scanFilters;
	private ScanSettings _scanSettings;

	// flags to keep track of state
	private boolean _initialized = false;
	private boolean _receiverRegistered = false;

	// enum to keep track of device connection state
	private enum ConnectionState {
		DISCONNECTED,
		CONNECTING,
		CONNECTED,
		DISCONNECTING
	}

	// enum to keep track of device discovery state
	private enum DiscoveryState {
		UNDISCOVERED,
		DISCOVERING,
		DISCOVERED
	}

	private class Connection {
		// BluetoothGatt object, used to communicate with the BLE device
		private BluetoothGatt _gatt;
		// keep track of connection state
		private ConnectionState _connectionState = ConnectionState.DISCONNECTED;
		// keep track of discovery state
		private DiscoveryState _discoveryState = DiscoveryState.UNDISCOVERED;

		/**
		 * Return the BluetoothGatt object used by this connection to talk to the device
		 * @return BluetoothGatt object
		 */
		public BluetoothGatt getGatt() {
			return _gatt;
		}

		/**
		 * Assign a new BluetoothGatt object to the connection
		 * @param gatt new BluetoothGatt object
		 */
		public void setGatt(BluetoothGatt gatt) {
			_gatt = gatt;
		}

		/**
		 * Return the current connection state
		 * @return connection state
		 */
		public ConnectionState getConnectionState() {
			return _connectionState;
		}

		/**
		 * Set a new connection state
		 * @param connectionState new connection state
		 */
		public void setConnectionState(ConnectionState connectionState) {
			_connectionState = connectionState;
		}

		/**
		 * Get the current discovery state
		 * @return discovery state
		 */
		public DiscoveryState getDiscoveryState() {
			return _discoveryState;
		}

		/**
		 * Set a new discovery state
		 * @param discoveryState new discovery state
		 */
		public void setDiscoveryState(DiscoveryState discoveryState) {
			_discoveryState = discoveryState;
		}
	}

	// a list of connections for different devices
	private HashMap<String, Connection> _connections = new HashMap<>();

	// flag to indicate if currently scanning for devices
	private boolean _scanning;

	// callbacks used to notify events
	private IDataCallback _scanCallback;
	private IStatusCallback _btStateCallback;

	// timeout handler to check for function timeouts, e.g. bluetooth enable, connect, reconnect, etc.
	private Handler _timeoutHandler;

	// Helper functions for logging
	protected void LOGd(String message) {
		Log.d(TAG, message);
	}

	protected void LOGd(String fmt, Object ... args) {
		LOGd(String.format(fmt, args));
	}

	protected void LOGe(String message) {
		Log.e(TAG, message);
	}

	protected void LOGe(String fmt, Object ... args) {
		LOGe(String.format(fmt, args));
	}

	/**
	 * Default constructor
	 */
	public BleCore() {
		// create a timeout handler with it's own thread to take care of timeouts
		HandlerThread timeoutThread = new HandlerThread("TimeoutHandler");
		timeoutThread.start();
		_timeoutHandler = new Handler(timeoutThread.getLooper());
	}

	/**
	 * Make sure to close and unregister receiver if the object is not used anymore
	 * @throws Throwable
	 */
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		destroy();
	}

	/**
	 * Unregister the broadcast receiver (if it is registered)
	 */
	public void destroy() {
		if (_receiverRegistered) {
			_context.unregisterReceiver(_receiver);
			_receiverRegistered = false;
		}
	}

	/**
	 * Broadcast receiver used to handle Bluetooth Events, i.e. turning on and off of the
	 * Bluetooth Adapter
	 */
	private BroadcastReceiver _receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (_btStateCallback == null) return;

			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
					case BluetoothAdapter.STATE_OFF:
						_connections = new HashMap<>();
						_scanCallback = null;
						_initialized = false;

						// if bluetooth is turned off, call onError on the bt state callback
						_btStateCallback.onError(BleErrors.ERROR_BLUETOOTH_TURNED_OFF);
						break;
					case BluetoothAdapter.STATE_ON:

						_initialized = true;
						if (Build.VERSION.SDK_INT >= 21) {
							// create the ble scanner object used for API > 21
							_leScanner = _bluetoothAdapter.getBluetoothLeScanner();
							// todo: see if we want to make the scan mode configurable
							_scanSettings = new ScanSettings.Builder()
									.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
									.build();
							_scanFilters = new ArrayList<>();
						}
						// inform the callback about the enabled bluetooth
						_btStateCallback.onSuccess();
						// bluetooth was successfully enabled, cancel the timeout
						_timeoutHandler.removeCallbacksAndMessages(null);
						break;
				}
			}
		}
	};

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
	@SuppressLint("NewApi")
	public void init(Context context, IStatusCallback callback) {
		_context = context;
		_btStateCallback = callback;

		LOGd("Initialize BLE hardware");
		BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		_bluetoothAdapter = bluetoothManager.getAdapter();

		if (!_receiverRegistered) {
			_context.registerReceiver(_receiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
			_receiverRegistered = true;
		}

		if (_bluetoothAdapter == null || !_bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			_context.startActivity(enableBtIntent);
			// use a timeout to check if bluetooth was enabled. if bluetooth is enabled
			// the timeout will be cancelled in the broadcast receiver. if the user denies bluetooth
			// enabling, this will trigger an error.
			_timeoutHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (!_bluetoothAdapter.isEnabled()) {
						_initialized = false;
						if (_btStateCallback != null) {
							_btStateCallback.onError(BleErrors.ERROR_BLUETOOTH_NOT_ENABLED);
						}
					}
				}
			}, BLUETOOTH_ENABLE_TIMEOUT);
		} else {
			LOGd("Bluetooth successfully initialized");
			_initialized = true;
			if (Build.VERSION.SDK_INT >= 21) {
				_leScanner = _bluetoothAdapter.getBluetoothLeScanner();
				_scanSettings = new ScanSettings.Builder()
						.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
						.build();
				_scanFilters = new ArrayList<>();
			}
			callback.onSuccess();
		}

	}

	/**
	 * Reset all callbacks.
	 */
	public void finish() {
		_initialized = false;
		_connectionCallback = null;
		_discoveryCallback = null;
		_btStateCallback = null;
		_scanCallback = null;
		_characteristicsReadCallback = null;
		_characteristicsWriteCallback = null;
	}

	public boolean isDeviceConnected(String address) {

		if (!isInitialized()) return false;

		Connection connection = _connections.get(address);
		return !(connection == null ||
				connection.getConnectionState() != ConnectionState.CONNECTED);

	}

	private void setReconnectTimeout(final String address, int timeout) {
		_connectTimeout = new Runnable() {

			@Override
			public void run() {
				LOGe("timeout reconnecting to %s, ABORT!", address);
				Connection connection = _connections.get(address);
				connection.setConnectionState(ConnectionState.DISCONNECTED);
				connection.getGatt().disconnect();
				_connectionCallback.onError(BleErrors.ERROR_RECONNECT_FAILED);
				_connectionCallback = null;
			}
		};

		_timeoutHandler.postDelayed(_connectTimeout, timeout * 1000);
	}

	private Runnable _connectTimeout;

	private void setConnectTimeout(final String address, int timeout) {
		_connectTimeout = new Runnable() {

			@Override
			public void run() {
				LOGe("timeout connecting to %s, ABORT!", address);
				Connection connection = _connections.get(address);
				if (connection != null) {
					connection.setConnectionState(ConnectionState.DISCONNECTED);
					connection.getGatt().close();
					_connections.remove(address);
					_connectionCallback.onError(BleErrors.ERROR_CONNECT_FAILED);
					_connectionCallback = null;
				}
			}
		};

		_timeoutHandler.postDelayed(_connectTimeout, timeout * 1000);
	}

	private void clearConnectTimeout() {
		LOGd("clear connect timeout");
		_timeoutHandler.removeCallbacks(_connectTimeout);
	}

	public void connectDevice(String address, int timeout, IDataCallback callback) {
		LOGd("Connecting to %s with %d second timeout ...", address, timeout);
		_connectionCallback = callback;

		if (!isInitialized()) {
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return;
		}

		BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(address);

		Connection connection = new Connection();
		_connections.put(address, connection);

		BluetoothGatt gatt = device.connectGatt(_context, false, new BluetoothGattCallbackExt());
		connection.setGatt(gatt);

		setConnectTimeout(address, timeout);

	}

	public boolean reconnectDevice(String address, int timeout, IDataCallback callback) {

		LOGd("reconnecting device ...");

		if (!isInitialized()) {
			LOGe(".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.DISCONNECTED) {
			LOGe(".. not disconnected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		boolean result = gatt.connect();

		if (!result) {
			LOGe(".. reconnect failed");
			callback.onError(BleErrors.ERROR_RECONNECT_FAILED);
			return false;
		}

		connection.setConnectionState(ConnectionState.CONNECTING);
		connection.setDiscoveryState(DiscoveryState.UNDISCOVERED);
		_connectionCallback = callback;

		setReconnectTimeout(address, timeout);

		return true;
	}

	private boolean isInitialized() {
		if (_initialized) {
			return _bluetoothAdapter.isEnabled();
		} else {
			return false;
		}
	}

	public void discoverServices(String address, IDataCallback callback) {
		// by default, return cached discovery if present, otherwise start new discovery
		discoverServices(address, false, callback);
	}

	/**
	 *
	 * @param address
	 * @param forceDiscover, set to true to force a new discovery,
	 *						 if false and cached discovery found, return the cached
	 * @param callback
	 */
	public void discoverServices(String address, boolean forceDiscover, IDataCallback callback) {
		LOGd("Discovering services ...");
		_discoveryCallback = callback;

		if (!isInitialized()) {
			LOGe(".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			LOGe(".. not connected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return;
		}

		JSONObject json;

		BluetoothGatt gatt = connection.getGatt();

		switch (connection.getDiscoveryState()) {
			case DISCOVERING:
				LOGd(".. already running");
				callback.onError(BleErrors.ERROR_ALREADY_DISCOVERING);
				return;
			case DISCOVERED:
				if (!forceDiscover) {
					LOGd(".. already done, return existing discovery");
					json = getDiscovery(gatt);
					callback.onData(json);
					return;
				}
				// else go to discovery, no break needed!
			default:
				LOGd(".. start discovery");
				connection.setDiscoveryState(DiscoveryState.DISCOVERING);
				gatt.discoverServices();
				break;
		}
	}

	private JSONObject getDiscovery(BluetoothGatt gatt) {

		JSONObject deviceJson = new JSONObject();
		BluetoothDevice device = gatt.getDevice();

		// add status and device info
		setStatus(deviceJson, BleCoreTypes.STATUS_DISCOVERED);
		addDeviceInfo(deviceJson, device);

		// add all services ...
		JSONArray servicesArray = new JSONArray();
		for (BluetoothGattService service : gatt.getServices()) {
			JSONObject serviceJson = new JSONObject();

			addProperty(serviceJson, BleCoreTypes.PROPERTY_SERVICE_UUID, BleUtils.uuidToString(service.getUuid()));

			// .. for each service, add all characteristics ...
			JSONArray characteristicsArray = new JSONArray();
			for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
				JSONObject characteristicJson = new JSONObject();

				addProperty(characteristicJson, BleCoreTypes.PROPERTY_CHARACTERISTIC_UUID, BleUtils.uuidToString(characteristic.getUuid()));
				addProperty(characteristicJson, BleCoreTypes.PROPERTY_PROPERTIES, getProperties(characteristic));

				// .. for each characteristics, add all descriptors ...
				JSONArray descriptorsArray = new JSONArray();
				for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
					JSONObject descriptorJson = new JSONObject();

					addProperty(descriptorJson, BleCoreTypes.PROPERTY_DESCRIPTOR_UUID, BleUtils.uuidToString(descriptor.getUuid()));
					descriptorsArray.put(descriptorJson);
				}

				addProperty(characteristicJson, BleCoreTypes.PROPERTY_DESCRIPTORS, descriptorsArray);
				characteristicsArray.put(characteristicJson);
			}

			addProperty(serviceJson, BleCoreTypes.PROPERTY_CHARACTERISTICS_LIST, characteristicsArray);
			servicesArray.put(serviceJson);
		}

		addProperty(deviceJson, BleCoreTypes.PROPERTY_SERVICES_LIST, servicesArray);

		return deviceJson;
	}

	private JSONObject getProperties(BluetoothGattCharacteristic characteristic) {

		int properties = characteristic.getProperties();
		JSONObject propertiesJSON = new JSONObject();

		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_BROADCAST, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_BROADCAST));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_READ, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_READ));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_WRITE_NO_RESPONSE, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_WRITE, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_WRITE));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_NOTIFY, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_NOTIFY));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_INDICATE, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_INDICATE));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_SIGNED_WRITE, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_EXTENDED_PROPERTIES, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS));

//		addProperty(propertiesJSON, "notifyEncryptionRequired", hasCharacteristicProperty(properties, 0x100));
//		addProperty(propertiesJSON, "indicateEncryptionRequired", hasCharacteristicProperty(properties, 0x200));

		return propertiesJSON;
	}

	public void discoverCharacteristic(String address, String serviceUuid, String characteristicUuid,
									   IStatusCallback callback) {
		discoverCharacteristic(address, serviceUuid, characteristicUuid, false, callback);
	}

	public void discoverCharacteristic(String address, final String serviceUuid, final String characteristicUuid,
									   boolean forceDiscover, final IStatusCallback callback) {

		discoverServices(address, forceDiscover, new IDataCallback() {

			@Override
			public void onError(int error) {
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				try {
					JSONArray servicesArray = json.getJSONArray(BleCoreTypes.PROPERTY_SERVICES_LIST);
					JSONObject service;
					for (int i = 0; i < servicesArray.length(); ++i) {
						service = servicesArray.getJSONObject(i);
						if (service.getString(BleCoreTypes.PROPERTY_SERVICE_UUID).matches(serviceUuid)) {
							// found service
							JSONArray characteristicsArray = service.getJSONArray(BleCoreTypes.PROPERTY_CHARACTERISTICS_LIST);
							JSONObject characteristic;
							for (int j = 0; j < characteristicsArray.length(); ++j) {
								characteristic = characteristicsArray.getJSONObject(j);
								if (characteristic.getString(BleCoreTypes.PROPERTY_CHARACTERISTIC_UUID).matches(characteristicUuid)) {
									// found characteristic
									callback.onSuccess();
								}
							}
						}
					}
				} catch (JSONException e) {
					LOGe("failed to decode discovered data");
					callback.onError(BleErrors.ERROR_DISCOVERY_FAILED);
				}
			}
		});
	}

	public boolean startEndlessScan(IDataCallback callback) {
		return startEndlessScan(new String[] {}, callback);
	}

	public boolean startEndlessScan(String[] uuids, IDataCallback callback) {

		if (!isInitialized()) {
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		if (isScanning()) {
			callback.onError(BleErrors.ERROR_ALREADY_SCANNING);
			return false;
		}

		_scanCallback = callback;

		// use new functionality if possible
		if (Build.VERSION.SDK_INT >= 21) {
			if (_coreScanCallback == null) {
				createCoreScanCallback();
			}

			if (uuids.length == 0) {
				_scanFilters.clear();
			} else {
				UUID[] serviceUuids = BleUtils.stringToUuid(uuids);
				for (UUID uuid : serviceUuids) {
					ScanFilter filter = new ScanFilter.Builder()
							.setServiceUuid(new ParcelUuid(uuid))
							.build();
					_scanFilters.add(filter);
				}
			}
			_leScanner.startScan(_scanFilters, _scanSettings, _coreScanCallback);
		} else {
			if (uuids.length == 0) {
				_scanning = _bluetoothAdapter.startLeScan(_coreLeScanCallback);
			} else {
				UUID[] serviceUuids = BleUtils.stringToUuid(uuids);
				_scanning = _bluetoothAdapter.startLeScan(serviceUuids, _coreLeScanCallback);
			}

			if (!_scanning) {
				callback.onError(BleErrors.ERROR_SCAN_FAILED);
				return false;
			}
		}

		return true;
	}

	@TargetApi(21)
	private void createCoreScanCallback() {
		_coreScanCallback = new ScanCallback() {
			@Override
			public void onScanResult(int callbackType, ScanResult result) {
				_scanning = true;

				JSONObject scanResult = new JSONObject();
				addDeviceInfo(scanResult, result.getDevice());
				addProperty(scanResult, BleCoreTypes.PROPERTY_RSSI, result.getRssi());
				if (result.getScanRecord() != null) {
					addBytes(scanResult, BleCoreTypes.PROPERTY_ADVERTISEMENT, result.getScanRecord().getBytes());
				}
				setStatus(scanResult, BleCoreTypes.PROPERTY_SCAN_RESULT);

				if (_scanCallback != null) {
					_scanCallback.onData(scanResult);
				}
			}

			@Override
			public void onBatchScanResults(List<ScanResult> results) {
				for (ScanResult result : results) {
					onScanResult(0, result);
				}
			}

			@Override
			public void onScanFailed(int errorCode) {
				_scanning = false;
				_scanCallback.onError(errorCode);
			}
		};
	}

	public boolean stopEndlessScan(IStatusCallback callback) {

		if (!isInitialized()) {
			if (callback != null) callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

//		if (!isScanning()) {
//			callback.onError(BleCoreTypes.ERROR_NOT_SCANNING);
//			return false;
//		}

		if (Build.VERSION.SDK_INT >= 21) {
			_leScanner.stopScan(_coreScanCallback);
		} else {
			_bluetoothAdapter.stopLeScan(_coreLeScanCallback);
		}
		_scanCallback = null;

		_scanning = false;

		if (callback != null) callback.onSuccess();
		return true;
	}

	private boolean isScanning() {
		return _scanning;
	}

	private LeScanCallback _coreLeScanCallback = new LeScanCallback() {

		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

			JSONObject scanResult = new JSONObject();
			addDeviceInfo(scanResult, device);
			addProperty(scanResult, BleCoreTypes.PROPERTY_RSSI, rssi);
			addBytes(scanResult, BleCoreTypes.PROPERTY_ADVERTISEMENT, scanRecord);
			setStatus(scanResult, BleCoreTypes.PROPERTY_SCAN_RESULT);

			if (_scanCallback != null) {
				_scanCallback.onData(scanResult);
			}
		}

	};

	//	@SuppressLint("NewApi")
	private ScanCallback _coreScanCallback;

	protected void parseAdvertisement(byte[] scanRecord, int search, IByteArrayCallback callback) {

		ByteBuffer bb = ByteBuffer.wrap(scanRecord);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		try {
			while (bb.hasRemaining()) {
				int length = BleUtils.signedToUnsignedByte(bb.get());
				int type = BleUtils.signedToUnsignedByte(bb.get());
				if (type == search) {
					byte[] result = new byte[length - 1];
					bb.get(result, 0, length - 1);
					callback.onSuccess(result);
				} else if (type == 0 && length == 0) {
					return;
				} else {
					// skip length elements
					bb.position(bb.position() + length - 1); // length also includes the type field, so only advance by length-1
				}
			}
		} catch (Exception e) {
			LOGe("failed to parse advertisement");
//			e.printStackTrace();
			callback.onError(BleErrors.ERROR_ADVERTISEMENT_PARSING);
		}
	}

	public boolean disconnectDevice(String address, IDataCallback callback) {

		LOGd("disconnecting device ...");

		if (!isInitialized()) {
			LOGe(".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			LOGe(".. not connected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothDevice device = gatt.getDevice();

		JSONObject deviceJson = new JSONObject();
		addDeviceInfo(deviceJson, device);

		switch (connection.getConnectionState()) {
			case CONNECTING:
				_connectionCallback = null;
				connection.setConnectionState(ConnectionState.DISCONNECTED);
				break;
			default:
				_connectionCallback = callback;
				connection.setConnectionState(ConnectionState.DISCONNECTING);
		}

		gatt.disconnect();

		LOGd("... done");

		return true;

	}

	public boolean isConnected(String address) {
		Connection connection = _connections.get(address);
		return connection.getConnectionState() == ConnectionState.CONNECTED;
	}

	public boolean isDisconnected(String address) {
		Connection connection = _connections.get(address);
		return connection.getConnectionState() == ConnectionState.DISCONNECTED;
	}

	public boolean isClosed(String address) {
		return !_connections.containsKey(address);
	}

	public boolean closeDevice(String address, boolean clearCache, IStatusCallback callback) {

		LOGd("closing device ...");

		if (!isInitialized()) {
			LOGe(".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.DISCONNECTED) {
			LOGe(".. still connected?");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();

		if (clearCache) {
			refreshDeviceCache(gatt);
		}

		gatt.close();
		_connections.remove(address);

		LOGd("... done");

		callback.onSuccess();
		return true;

	}

	private void refreshDeviceCache(final BluetoothGatt gatt) {
		LOGd("refreshDeviceCache");
		/*
		 * If the device is bonded this is up to the Service Changed characteristic to notify Android that the services has changed.
		 * There is no need for this trick in that case.
		 * If not bonded, the Android should not keep the services cached when the Service Changed characteristic is present in the target device database.
		 * However, due to the Android bug (still exists in Android 5.0.1), it is keeping them anyway and the only way to clear services is by using this hidden refresh method.
		 */
//		if (force || gatt.getDevice().getBondState() == BluetoothDevice.BOND_NONE) {
		// Log.i(TAG, "refresh");
			/*
			 * There is a refresh() method in BluetoothGatt class but for now it's hidden. We will call it using reflections.
			 */
		try {
			final Method refresh = gatt.getClass().getMethod("refresh");
			if (refresh != null) {
				final boolean success = (Boolean) refresh.invoke(gatt);
				LOGd("Refreshing result: " + success);
			}
		} catch (Exception e) {
			Log.e(TAG, "An exception occurred while refreshing device", e);
			LOGe("Refreshing failed");
		}
//		}
	}

	public boolean disconnectAndCloseDevice(final String address, final boolean clearCache, final IDataCallback callback) {

		return disconnectDevice(address, new IDataCallback() {

			private void close() {
				closeDevice(address, clearCache, new IStatusCallback() {
					@Override
					public void onSuccess() {
						JSONObject returnJson = new JSONObject();
						setStatus(returnJson, "closed");
						callback.onData(returnJson);
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
			}

			@Override
			public void onData(JSONObject json) {
				String status = getStatus(json);
				if (status == "disconnected") {
					callback.onData(json);
					close();
				} else {
					LOGe("wrong status received: %s", status);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
				// also try to close even if disconnect fails
				close();
			}
		});
	}

	public boolean read(String address, String serviceUuid, String characteristicUuid, IDataCallback callback) {

		if (!isInitialized()) {
			LOGe(".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			LOGe(".. not connected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothGattService service = gatt.getService(BleUtils.stringToUuid(serviceUuid));

		if (service == null) {
			LOGe(".. service not found!");
			callback.onError(BleErrors.ERROR_SERVICE_NOT_FOUND);
			return false;
		}

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);

		if (characteristic == null) {
			LOGe(".. characteristic not found!");
			callback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
			return false;
		}

		// todo: one callback per characteristic / read / write / operation
		_characteristicsReadCallback = callback;

		boolean result = gatt.readCharacteristic(characteristic);
		if (!result) {
			LOGe(".. failed to read from characteristic!");
			callback.onError(BleErrors.ERROR_CHARACTERISTIC_READ_FAILED);
			return false;
		}

		return true;
	}

	public boolean write(String address, String serviceUuid, String characteristicUuid, byte[] value, IStatusCallback callback) {
		return write(address, serviceUuid, characteristicUuid, value, callback, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
	}

	public boolean writeNoResponse(String address, String serviceUuid, String characteristicUuid, byte[] value, IStatusCallback callback) {
		return write(address, serviceUuid, characteristicUuid, value, callback, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
	}

	private boolean write(String address, String serviceUuid, String characteristicUuid, byte[] value, IStatusCallback callback, int writeType) {

		if (!isInitialized()) {
			LOGe(".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			LOGe(".. not connected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothGattService service = gatt.getService(BleUtils.stringToUuid(serviceUuid));

		if (service == null) {
			LOGe(".. service not found!");
			callback.onError(BleErrors.ERROR_SERVICE_NOT_FOUND);
			return false;
		}

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);

		if (characteristic == null) {
			LOGe(".. characteristic not found!");
			callback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
			return false;
		}

		characteristic.setWriteType(writeType);

		boolean result = characteristic.setValue(value);
		if (!result) {
			LOGe(".. failed to set value!");
			callback.onError(BleErrors.ERROR_WRITE_VALUE_NOT_SET);
			return false;
		}

		_characteristicsWriteCallback = callback;

		result = gatt.writeCharacteristic(characteristic);
		if (!result) {
			LOGe(".. failed to write characteristic!");
			callback.onError(BleErrors.ERROR_WRITE_FAILED);
			return false;
		}

		return true;
	}


	IDataCallback _connectionCallback = null;
	IDataCallback _discoveryCallback = null;
	IDataCallback _characteristicsReadCallback = null;
	IStatusCallback _characteristicsWriteCallback = null;

	private class BluetoothGattCallbackExt extends BluetoothGattCallback {


		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//			String intentAction;

			BluetoothDevice device = gatt.getDevice();
			Connection connection = _connections.get(device.getAddress());
			if (status == BluetoothGatt.GATT_SUCCESS) {

				if (newState == BluetoothProfile.STATE_CONNECTED) {

					connection.setConnectionState(ConnectionState.CONNECTED);
					LOGd("Connected to GATT server.");

					JSONObject json = new JSONObject();
					setStatus(json, "connected");

					if (_connectionCallback != null) {
						_connectionCallback.onData(json);
					}

					//				intentAction = ACTION_GATT_CONNECTED;
					//				broadcastUpdate(intentAction);

				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

					connection.setConnectionState(ConnectionState.DISCONNECTED);
					LOGd("Disconnected from GATT server.");

					JSONObject json = new JSONObject();
					setStatus(json, "disconnected");

					if (_connectionCallback != null) {
						_connectionCallback.onData(json);
					}

					//				intentAction = ACTION_GATT_DISCONNECTED;
					//				broadcastUpdate(intentAction);

				} else {
					LOGd("..ing state: %d", status);
					return;
				}
			} else {
				LOGe("BluetoothGatt Error, status: %d", status);

				gatt.close();
				connection.setConnectionState(ConnectionState.DISCONNECTED);

				if (_connectionCallback != null) {
					_connectionCallback.onError(status);
				}
			}

			clearConnectTimeout();
		}

		@Override
		// New services discovered
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {

			BluetoothDevice device = gatt.getDevice();
			Connection connection = _connections.get(device.getAddress());

			if (status == BluetoothGatt.GATT_SUCCESS) {
				connection.setDiscoveryState(DiscoveryState.DISCOVERED);

				JSONObject json = getDiscovery(gatt);

				if (_discoveryCallback != null) {
					_discoveryCallback.onData(json);
				}

//				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

			} else {
				connection.setDiscoveryState(DiscoveryState.UNDISCOVERED);

				LOGe("Discovery failed, status: %d", status);

				if (_discoveryCallback != null) {
					_discoveryCallback.onError(BleErrors.ERROR_DISCOVERY_FAILED);
				}
			}
		}

		@Override
		// Result of a characteristic read operation
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

			if (status == BluetoothGatt.GATT_SUCCESS) {

				JSONObject json = new JSONObject();

				setStatus(json, BleCoreTypes.CHARACTERISTIC_PROP_READ);
				setCharacteristic(json, characteristic);
				setValue(json, characteristic.getValue());

				if (_characteristicsReadCallback != null) {
					_characteristicsReadCallback.onData(json);
				}

//				broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

			} else {
				LOGe("Characteristic read failed, status: %d", status);

				if (_characteristicsReadCallback != null) {
					_characteristicsReadCallback.onError(BleErrors.ERROR_CHARACTERISTIC_READ_FAILED);
				}
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

			JSONObject json = new JSONObject();

			setStatus(json, BleCoreTypes.CHARACTERISTIC_PROP_NOTIFY);
			setCharacteristic(json, characteristic);
			setValue(json, characteristic.getValue());

			if (_characteristicsReadCallback != null) {
				_characteristicsReadCallback.onData(json);
			}

		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

			if (status == BluetoothGatt.GATT_SUCCESS) {

				// do we need to send back the status and the value we wrote??
//				JSONObject json = new JSONObject();
//				setStatus(json, BleCoreTypes.STATUS_WRITTEN);
//				setCharacteristic(json, characteristic);
//				setValue(json, characteristic.getValue());

				if (_characteristicsWriteCallback != null) {
//					_characteristicsWriteCallback.onData(json);
					_characteristicsWriteCallback.onSuccess();
				}

			} else {
				LOGe("Characteristic write failed, status: %d", status);

				if (_characteristicsWriteCallback != null) {
					_characteristicsWriteCallback.onError(BleErrors.ERROR_CHARACTERISTIC_WRITE_FAILED);
				}
			}
		}

	}


	public static boolean hasCharacteristicProperty(int properties, int property) {
		return (properties & property) == property;
	}

	public static void addProperty(JSONObject json, String key, Object value) {
		try {
			json.put(key, value);
		} catch (JSONException e) {
			e.printStackTrace();
			Log.e(TAG, "Failed to encode json");
		}
	}

	public static void addDeviceInfo(JSONObject json, BluetoothDevice device) {
		addProperty(json, BleCoreTypes.PROPERTY_ADDRESS, device.getAddress());
		addProperty(json, BleCoreTypes.PROPERTY_NAME, device.getName());
	}

	public static void setCharacteristic(JSONObject json, BluetoothGattCharacteristic characteristic) {
		addProperty(json, BleCoreTypes.PROPERTY_SERVICE_UUID, BleUtils.uuidToString(characteristic.getService().getUuid()));
		addProperty(json, BleCoreTypes.PROPERTY_CHARACTERISTIC_UUID, BleUtils.uuidToString(characteristic.getUuid()));
	}

	public static void setStatus(JSONObject json, String status) {
		addProperty(json, BleCoreTypes.PROPERTY_STATUS, status);
	}

	public static String getStatus(JSONObject json) {
		try {
			return json.getString(BleCoreTypes.PROPERTY_STATUS);
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void addBytes(JSONObject json, String field, byte[] bytes) {
		String value = BleUtils.bytesToEncodedString(bytes);
		addProperty(json, field, value);
	}

	public static byte[] getBytes(JSONObject json, String field) {
		try {
			String value = json.getString(field);
			return BleUtils.encodedStringToBytes(value);
		} catch (JSONException e) {
			Log.e(TAG, "failed to read bytes");
			e.printStackTrace();
		}

		return null;
	}

	public static byte[] getValue(JSONObject json) {
		return getBytes(json, BleCoreTypes.PROPERTY_VALUE);
	}

	public static void setValue(JSONObject json, byte[] value) {
		addBytes(json, BleCoreTypes.PROPERTY_VALUE, value);
	}

}
