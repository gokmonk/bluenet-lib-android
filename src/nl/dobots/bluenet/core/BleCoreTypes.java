package nl.dobots.bluenet.core;

public class BleCoreTypes {

	public static final String BASE_UUID_START = "0000";
	public static final String BASE_UUID_END = "-0000-1000-8000-00805f9b34fb";
	
	// Characteristic properties
	public static final String CHARACTERISTIC_PROP_EXTENDED_PROPERTIES = "extendedProperties";
	public static final String CHARACTERISTIC_PROP_SIGNED_WRITE = "signedWrite";
	public static final String CHARACTERISTIC_PROP_INDICATE = "indicate";
	public static final String CHARACTERISTIC_PROP_NOTIFY = "notify";
	public static final String CHARACTERISTIC_PROP_WRITE = "write";
	public static final String CHARACTERISTIC_PROP_WRITE_NO_RESPONSE = "writeNoResponse";
	public static final String CHARACTERISTIC_PROP_READ = "read";
	public static final String CHARACTERISTIC_PROP_BROADCAST = "broadcast";
	
	// JSON property fields
	public static final String PROPERTY_VALUE = "value";
	public static final String PROPERTY_SCAN_RESULT = "scanResult";
	public static final String PROPERTY_ADVERTISEMENT = "advertisement";
	public static final String PROPERTY_RSSI = "rssi";
	public static final String PROPERTY_SERVICES = "services";
	public static final String PROPERTY_CHARACTERISTICS = "characteristics";
	public static final String PROPERTY_DESCRIPTORS = "descriptors";
	public static final String PROPERTY_DESCRIPTOR_UUID = "descriptorUUID";
	public static final String PROPERTY_PROPERTIES = "properties";
	public static final String PROPERTY_CHARACTERISTIC_UUID = "characteristicUUID";
	public static final String PROPERTY_SERVICE_UUID = "serviceUUID";
	public static final String PROPERTY_STATUS = "status";
	public static final String PROPERTY_NAME = "name";
	public static final String PROPERTY_ADDRESS = "address";
	
	// JSON status values
	public static final String STATUS_DISCOVERED = "discovered";
	public static final String STATUS_WRITTEN = "written";
	
	// ERROR callback values
	public static final int DISCOVERY_FAILED = 200;
	public static final int CHARACTERISTIC_READ_FAILED = 201;
	public static final int CHARACTERISTIC_WRITE_FAILED = 202;
	public static final int NOT_INITIALIZED = 203;
	public static final int NEVER_CONNECTED = 204;
	public static final int NOT_CONNECTED = 205;
	public static final int ALREADY_DISCOVERING = 206;
	public static final int ALREADY_SCANNING = 207;
	public static final int SCAN_FAILED = 208;
	public static final int NOT_SCANNING = 209;
	public static final int CONNECT_FAILED = 210;
	public static final int RECONNECT_FAILED = 211;
	public static final int SERVICE_NOT_FOUND = 212;
	public static final int CHARACTERISTIC_NOT_FOUND = 213;
	public static final int WRITE_VALUE_NOT_SET = 214;
	public static final int WRITE_FAILED = 215;
	public static final int DEVICE_NOT_FOUND = 216;

	public static final int WRONG_LENGTH_PARAMETER = 217;
	public static final int EMPTY_VALUE = 218;
	public static final int BLUETOOTH_INITIALIZATION_CANCELLED = 219;
	public static final int BLUETOOTH_TURNED_OFF = 220;
	public static final int BLUETOOTH_NOT_ENABLED = 221;
}
