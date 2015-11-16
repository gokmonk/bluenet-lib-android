package nl.dobots.bluenet.ble.cfg;

/**
 * Defines error values used and created by the bluenet library
 *
 * Created on 16-9-15
 * @author Dominik Egger
 */
public class BleErrors {

	// BleCore

	public static final int ERROR_DISCOVERY_FAILED = 200;
	public static final int ERROR_CHARACTERISTIC_READ_FAILED = 201;
	public static final int ERROR_CHARACTERISTIC_WRITE_FAILED = 202;
	public static final int ERROR_NOT_INITIALIZED = 203;
	public static final int ERROR_NEVER_CONNECTED = 204;
	public static final int ERROR_NOT_CONNECTED = 205;
	public static final int ERROR_ALREADY_DISCOVERING = 206;
	public static final int ERROR_ALREADY_SCANNING = 207;
	public static final int ERROR_SCAN_FAILED = 208;
	public static final int ERROR_NOT_SCANNING = 209;
	public static final int ERROR_CONNECT_FAILED = 210;
	public static final int ERROR_RECONNECT_FAILED = 211;
	public static final int ERROR_SERVICE_NOT_FOUND = 212;
	public static final int ERROR_CHARACTERISTIC_NOT_FOUND = 213;
	public static final int ERROR_WRITE_VALUE_NOT_SET = 214;
	public static final int ERROR_WRITE_FAILED = 215;
	public static final int ERROR_DEVICE_NOT_FOUND = 216;
	public static final int ERROR_WRONG_LENGTH_PARAMETER = 217;
	public static final int ERROR_EMPTY_VALUE = 218;
	public static final int ERROR_BLUETOOTH_INITIALIZATION_CANCELLED = 219;
	public static final int ERROR_BLUETOOTH_TURNED_OFF = 220;
	public static final int ERROR_BLUETOOTH_NOT_ENABLED = 221;
	public static final int ERROR_DISCONNECT_FAILED = 222;
	public static final int ERROR_NO_ADDRESS_PROVIDED = 223;
	public static final int ERROR_STILL_CONNECTED = 224;
	public static final int ERROR_RETURN_VALUE_PARSING = 225;
	public static final int ERROR_ADVERTISEMENT_PARSING = 226;
	public static final int ERROR_BLE_HARDWARE_MISSING = 227;

	// BleExt

	public static final int ERROR_WRONG_STATE = 500;
	public static final int ERROR_JSON_PARSING = 501;

}
