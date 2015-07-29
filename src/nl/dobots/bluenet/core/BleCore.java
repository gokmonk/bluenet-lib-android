package nl.dobots.bluenet.core;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.UUID;

import nl.dobots.bluenet.BleUtils;
import nl.dobots.bluenet.callbacks.IDataCallback;
import nl.dobots.bluenet.callbacks.IStatusCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/*
 * TODO: - implement for multiple connected devices
 */

public class BleCore {

	private static final String TAG = BleCore.class.getSimpleName();

	// Activity Result
	public static final int REQUEST_ENABLE_BT = 100;

	private BluetoothAdapter _bluetoothAdapter;

	private Context _context;

	private boolean _initialized = false;
	private boolean _receiverRegistered = false;

	private enum ConnectionState {
		DISCONNECTED,
		CONNECTING,
		CONNECTED,
		DISCONNECTING
	}

	private enum DiscoveryState {
		UNDISCOVERED,
		DISCOVERING,
		DISCOVERED
	}

	private class Connection {
		private BluetoothGatt _gatt;
		private ConnectionState _connectionState = ConnectionState.DISCONNECTED;
		private DiscoveryState _discoveryState = DiscoveryState.UNDISCOVERED;

		public BluetoothGatt getGatt() {
			return _gatt;
		}

		public void setGatt(BluetoothGatt gatt) {
			_gatt = gatt;
		}

		public ConnectionState getConnectionState() {
			return _connectionState;
		}

		public void setConnectionState(ConnectionState connectionState) {
			_connectionState = connectionState;
		}

		public DiscoveryState getDiscoveryState() {
			return _discoveryState;
		}

		public void setDiscoveryState(DiscoveryState discoveryState) {
			_discoveryState = discoveryState;
		}
	}

	private HashMap<String, Connection> _connections = new HashMap<>();

	private boolean _scanning;

	private IDataCallback _scanCallback;
	private IStatusCallback _initCallback;

	private Handler _timeoutHandler;

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


	public BleCore() {
		HandlerThread _timeoutThread = new HandlerThread("TimeoutHandler");
		_timeoutThread.start();
		_timeoutHandler = new Handler(_timeoutThread.getLooper());
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		destroy();
	}

	public void destroy() {
		if (_receiverRegistered) {
			_context.unregisterReceiver(_receiver);
			_receiverRegistered = false;
		}
	}

	private BroadcastReceiver _receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (_initCallback == null) return;

			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
				case BluetoothAdapter.STATE_OFF:
					_connections = new HashMap<>();
					_scanCallback = null;

					_initCallback.onError(BleCoreTypes.ERROR_BLUETOOTH_TURNED_OFF);
					break;
				case BluetoothAdapter.STATE_ON:

					_initCallback.onSuccess();
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
	 * will be triggered.
	 * @param context
	 * @param callback
	 * @return
	 */
	@SuppressLint("NewApi")
	public void init(Context context, IStatusCallback callback) {
		_context = context;
		_initCallback = callback;

		LOGd("Initialize BLE hardware");
		BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		_bluetoothAdapter = bluetoothManager.getAdapter();

		if (!_receiverRegistered) {
			_context.registerReceiver(_receiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
			_receiverRegistered = true;
		}

//		if (_bluetoothAdapter == null) {
////			throw new Exception("No Bluetooth adapter available, ABORT!");
//			LOGe("No Bluetooth adapter available. Make sure your phone has Bluetooth 4.+");
//			return false;
//		}

		if (_bluetoothAdapter == null || !_bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			if (context instanceof Activity) {
				((Activity)_context).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			} else {
				_context.startActivity(enableBtIntent);
			}
		} else {
			LOGd("Bluetooth successfully initialized");
			_initialized = true;
			callback.onSuccess();
		}

	}


	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_ENABLE_BT:
			// don't need to do this check, because we have the broadcast receiver registered.
			// which will trigger onSuccess if the bluetooth adapter is turned on and onError
			// if it is turned off. However, we need to catch the case that the bluetooth adapter
			// was never on, and the initialization failed, or the user cancelled. That can
			// be done with simply checking if the adapter is enabled
			if (!_bluetoothAdapter.isEnabled()) {
				_initialized = false;
				_initCallback.onError(BleCoreTypes.ERROR_BLUETOOTH_NOT_ENABLED);
			}
//			if (resultCode == Activity.RESULT_OK) {
//				if (_bluetoothAdapter.isEnabled()) {
//					LOGd("Bluetooth successfully initialized");
//					_initialized = true;
//					_initCallback.onSuccess();
//				} else {
//					LOGe("activity result OK, but adapter still not enabled??!!!");
//					_initialized = false;
//					_initCallback.onError(BleCoreTypes.ERROR_BLUETOOTH_TURNED_OFF);
//				}
//			} else {
//				LOGe("Bluetooth initialization canceled!");
//				_initialized = false;
//				_initCallback.onError(BleCoreTypes.ERROR_BLUETOOTH_INITIALIZATION_CANCELLED);
//			}
			break;
		}
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
				_connectionCallback.onError(BleCoreTypes.ERROR_RECONNECT_FAILED);
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
				connection.setConnectionState(ConnectionState.DISCONNECTED);
				connection.getGatt().close();
				_connections.remove(address);
				_connectionCallback.onError(BleCoreTypes.ERROR_CONNECT_FAILED);
				_connectionCallback = null;
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
			callback.onError(BleCoreTypes.ERROR_NOT_INITIALIZED);
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
			callback.onError(BleCoreTypes.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleCoreTypes.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.DISCONNECTED) {
			LOGe(".. not disconnected");
			callback.onError(BleCoreTypes.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		boolean result = gatt.connect();

		if (!result) {
			LOGe(".. reconnect failed");
			callback.onError(BleCoreTypes.ERROR_RECONNECT_FAILED);
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
			callback.onError(BleCoreTypes.ERROR_NOT_INITIALIZED);
			return;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleCoreTypes.ERROR_NEVER_CONNECTED);
			return;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			LOGe(".. not connected");
			callback.onError(BleCoreTypes.ERROR_NOT_CONNECTED);
			return;
		}

		JSONObject json;

		BluetoothGatt gatt = connection.getGatt();

		switch (connection.getDiscoveryState()) {
		case DISCOVERING:
			LOGd(".. already running");
			callback.onError(BleCoreTypes.ERROR_ALREADY_DISCOVERING);
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
		BleUtils.setStatus(deviceJson, BleCoreTypes.STATUS_DISCOVERED);
		BleUtils.addDeviceInfo(deviceJson, device);

		// add all services ...
		JSONArray servicesArray = new JSONArray();
		for (BluetoothGattService service : gatt.getServices()) {
			JSONObject serviceJson = new JSONObject();

			BleUtils.addProperty(serviceJson, BleCoreTypes.PROPERTY_SERVICE_UUID, BleUtils.uuidToString(service.getUuid()));

			// .. for each service, add all characteristics ...
			JSONArray characteristicsArray = new JSONArray();
			for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
				JSONObject characteristicJson = new JSONObject();

				BleUtils.addProperty(characteristicJson, BleCoreTypes.PROPERTY_CHARACTERISTIC_UUID, BleUtils.uuidToString(characteristic.getUuid()));
				BleUtils.addProperty(characteristicJson, BleCoreTypes.PROPERTY_PROPERTIES, getProperties(characteristic));

				// .. for each characteristics, add all descriptors ...
				JSONArray descriptorsArray = new JSONArray();
				for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
					JSONObject descriptorJson = new JSONObject();

					BleUtils.addProperty(descriptorJson, BleCoreTypes.PROPERTY_DESCRIPTOR_UUID, BleUtils.uuidToString(descriptor.getUuid()));
					descriptorsArray.put(descriptorJson);
				}

				BleUtils.addProperty(characteristicJson, BleCoreTypes.PROPERTY_DESCRIPTORS, descriptorsArray);
				characteristicsArray.put(characteristicJson);
			}

			BleUtils.addProperty(serviceJson, BleCoreTypes.PROPERTY_CHARACTERISTICS_LIST, characteristicsArray);
			servicesArray.put(serviceJson);
		}

		BleUtils.addProperty(deviceJson, BleCoreTypes.PROPERTY_SERVICES_LIST, servicesArray);

		return deviceJson;
	}

	private JSONObject getProperties(BluetoothGattCharacteristic characteristic) {

		int properties = characteristic.getProperties();
		JSONObject propertiesJSON = new JSONObject();

		BleUtils.addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_BROADCAST, BleUtils.hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_BROADCAST));
		BleUtils.addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_READ, BleUtils.hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_READ));
		BleUtils.addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_WRITE_NO_RESPONSE, BleUtils.hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE));
		BleUtils.addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_WRITE, BleUtils.hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_WRITE));
		BleUtils.addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_NOTIFY, BleUtils.hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_NOTIFY));
		BleUtils.addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_INDICATE, BleUtils.hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_INDICATE));
		BleUtils.addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_SIGNED_WRITE, BleUtils.hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE));
		BleUtils.addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_EXTENDED_PROPERTIES, BleUtils.hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS));

//		BleUtils.addProperty(propertiesJSON, "notifyEncryptionRequired", BleUtils.hasCharacteristicProperty(properties, 0x100));
//		BleUtils.addProperty(propertiesJSON, "indicateEncryptionRequired", BleUtils.hasCharacteristicProperty(properties, 0x200));

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
					callback.onError(BleCoreTypes.ERROR_DISCOVERY_FAILED);
				}
			}
		});
	}

	public boolean startEndlessScan(IDataCallback callback) {
		return startEndlessScan(new String[] {}, callback);
	}

	public boolean startEndlessScan(String[] uuids, IDataCallback callback) {

		if (!isInitialized()) {
			callback.onError(BleCoreTypes.ERROR_NOT_INITIALIZED);
			return false;
		}

		if (isScanning()) {
			callback.onError(BleCoreTypes.ERROR_ALREADY_SCANNING);
			return false;
		}

		_scanCallback = callback;

		if (uuids.length == 0) {
			_scanning = _bluetoothAdapter.startLeScan(scanCallback);
		} else {
			UUID[] serviceUuids = BleUtils.stringToUuid(uuids);
			_scanning = _bluetoothAdapter.startLeScan(serviceUuids, scanCallback);
		}

		if (!_scanning) {
			callback.onError(BleCoreTypes.ERROR_SCAN_FAILED);
			return false;
		}

		return true;
	}

	public boolean stopEndlessScan(IStatusCallback callback) {

		if (!isInitialized()) {
			callback.onError(BleCoreTypes.ERROR_NOT_INITIALIZED);
			return false;
		}

		if (!isScanning()) {
			callback.onError(BleCoreTypes.ERROR_NOT_SCANNING);
			return false;
		}

		_bluetoothAdapter.stopLeScan(scanCallback);
		_scanCallback = null;
		_scanning = false;

		callback.onSuccess();
		return true;
	}

	private boolean isScanning() {
		return _scanning;
	}

	private LeScanCallback scanCallback = new LeScanCallback() {

		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

			if (_scanCallback == null) {
				return;
			}

			JSONObject scanResult = new JSONObject();
			BleUtils.addDeviceInfo(scanResult, device);
			BleUtils.addProperty(scanResult, BleCoreTypes.PROPERTY_RSSI, rssi);
			BleUtils.addBytes(scanResult, BleCoreTypes.PROPERTY_ADVERTISEMENT, scanRecord);
			BleUtils.setStatus(scanResult, BleCoreTypes.PROPERTY_SCAN_RESULT);
			 _scanCallback.onData(scanResult);
		}

	};

	protected byte[] parseAdvertisement(byte[] scanRecord, int search) {

		ByteBuffer bb = ByteBuffer.wrap(scanRecord);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		try {
			while (bb.hasRemaining()) {
				int length = BleUtils.signedToUnsignedByte(bb.get());
				int type = BleUtils.signedToUnsignedByte(bb.get());
				if (type == search) {
					byte[] result = new byte[length - 1];
					bb.get(result, 0, length - 1);
					return result;
				} else {
					// skip length elements
					bb.position(bb.position() + length - 1); // length also includes the type field, so only advance by length-1
				}
			}
		} catch (Exception e) {
			LOGe("failed to parse advertisement");
			e.printStackTrace();
		}

		return null;

	}

	public boolean disconnectDevice(String address, IDataCallback callback) {

		LOGd("disconnecting device ...");

		if (!isInitialized()) {
			LOGe(".. not initialized");
			callback.onError(BleCoreTypes.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleCoreTypes.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			LOGe(".. not connected");
			callback.onError(BleCoreTypes.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothDevice device = gatt.getDevice();

		JSONObject deviceJson = new JSONObject();
		BleUtils.addDeviceInfo(deviceJson, device);

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
			callback.onError(BleCoreTypes.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleCoreTypes.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.DISCONNECTED) {
			LOGe(".. still connected?");
			callback.onError(BleCoreTypes.ERROR_NOT_CONNECTED);
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
						BleUtils.setStatus(returnJson, "closed");
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
				String status = BleUtils.getStatus(json);
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
			callback.onError(BleCoreTypes.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleCoreTypes.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			LOGe(".. not connected");
			callback.onError(BleCoreTypes.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothGattService service = gatt.getService(BleUtils.stringToUuid(serviceUuid));

		if (service == null) {
			LOGe(".. service not found!");
			callback.onError(BleCoreTypes.ERROR_SERVICE_NOT_FOUND);
			return false;
		}

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);

		if (characteristic == null) {
			LOGe(".. characteristic not found!");
			callback.onError(BleCoreTypes.ERROR_CHARACTERISTIC_NOT_FOUND);
			return false;
		}

		// todo: one callback per characteristic / read / write / operation
		_characteristicsReadCallback = callback;

		boolean result = gatt.readCharacteristic(characteristic);
		if (!result) {
			LOGe(".. failed to read from characteristic!");
			callback.onError(BleCoreTypes.ERROR_CHARACTERISTIC_READ_FAILED);
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
			callback.onError(BleCoreTypes.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			LOGe(".. never connected");
			callback.onError(BleCoreTypes.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			LOGe(".. not connected");
			callback.onError(BleCoreTypes.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothGattService service = gatt.getService(BleUtils.stringToUuid(serviceUuid));

		if (service == null) {
			LOGe(".. service not found!");
			callback.onError(BleCoreTypes.ERROR_SERVICE_NOT_FOUND);
			return false;
		}

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);

		if (characteristic == null) {
			LOGe(".. characteristic not found!");
			callback.onError(BleCoreTypes.ERROR_CHARACTERISTIC_NOT_FOUND);
			return false;
		}

		characteristic.setWriteType(writeType);

		boolean result = characteristic.setValue(value);
		if (!result) {
			LOGe(".. failed to set value!");
			callback.onError(BleCoreTypes.ERROR_WRITE_VALUE_NOT_SET);
			return false;
		}

		_characteristicsWriteCallback = callback;

		result = gatt.writeCharacteristic(characteristic);
		if (!result) {
			LOGe(".. failed to write characteristic!");
			callback.onError(BleCoreTypes.ERROR_WRITE_FAILED);
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
					BleUtils.setStatus(json, "connected");

					if (_connectionCallback != null) {
						_connectionCallback.onData(json);
					}

	//				intentAction = ACTION_GATT_CONNECTED;
	//				broadcastUpdate(intentAction);

				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

					connection.setConnectionState(ConnectionState.DISCONNECTED);
					LOGd("Disconnected from GATT server.");

					JSONObject json = new JSONObject();
					BleUtils.setStatus(json, "disconnected");

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
					_discoveryCallback.onError(BleCoreTypes.ERROR_DISCOVERY_FAILED);
				}
			}
		}

		@Override
		// Result of a characteristic read operation
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

			if (status == BluetoothGatt.GATT_SUCCESS) {

				JSONObject json = new JSONObject();

				BleUtils.setStatus(json, BleCoreTypes.CHARACTERISTIC_PROP_READ);
				BleUtils.setCharacteristic(json, characteristic);
				BleUtils.setValue(json, characteristic.getValue());

				if (_characteristicsReadCallback != null) {
					_characteristicsReadCallback.onData(json);
				}

//				broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

			} else {
				LOGe("Characteristic read failed, status: %d", status);

				if (_characteristicsReadCallback != null) {
					_characteristicsReadCallback.onError(BleCoreTypes.ERROR_CHARACTERISTIC_READ_FAILED);
				}
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

			JSONObject json = new JSONObject();

			BleUtils.setStatus(json, BleCoreTypes.CHARACTERISTIC_PROP_NOTIFY);
			BleUtils.setCharacteristic(json, characteristic);
			BleUtils.setValue(json, characteristic.getValue());

			if (_characteristicsReadCallback != null) {
				_characteristicsReadCallback.onData(json);
			}

		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

			if (status == BluetoothGatt.GATT_SUCCESS) {

				// do we need to send back the status and the value we wrote??
//				JSONObject json = new JSONObject();
//				BleUtils.setStatus(json, BleCoreTypes.STATUS_WRITTEN);
//				BleUtils.setCharacteristic(json, characteristic);
//				BleUtils.setValue(json, characteristic.getValue());

				if (_characteristicsWriteCallback != null) {
//					_characteristicsWriteCallback.onData(json);
					_characteristicsWriteCallback.onSuccess();
				}

			} else {
				LOGe("Characteristic write failed, status: %d", status);

				if (_characteristicsWriteCallback != null) {
					_characteristicsWriteCallback.onError(BleCoreTypes.ERROR_CHARACTERISTIC_WRITE_FAILED);
				}
			}
		}

	}

}
