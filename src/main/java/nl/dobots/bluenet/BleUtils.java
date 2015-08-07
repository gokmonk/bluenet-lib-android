package nl.dobots.bluenet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import nl.dobots.bluenet.core.BleCoreTypes;

import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Base64;
import android.util.Log;

public class BleUtils {

	private static final String TAG = BleUtils.class.getCanonicalName();

	public static String uuidToString(UUID uuid) {
		String uuidString = uuid.toString();

		if (uuidString.startsWith(BleCoreTypes.BASE_UUID_START) && uuidString.endsWith(BleCoreTypes.BASE_UUID_END))
		{
			return uuidString.substring(4, 8);
		}

		return uuidString;
	}

	public static UUID[] stringToUuid(String[] uuids) {
		UUID[] result = new UUID[uuids.length];

		for (int i = 0; i < uuids.length; ++i) {
			result[i] = stringToUuid(uuids[i]);
		}
		return result;
	}

	public static UUID stringToUuid(String uuidStr) {
		if (uuidStr.length() == 4) {
			uuidStr = BleCoreTypes.BASE_UUID_START + uuidStr + BleCoreTypes.BASE_UUID_END;
		}
		return UUID.fromString(uuidStr);
	}

	public static byte[] uuidToBytes(String uuidStr) {
		UUID uuid = stringToUuid(uuidStr);

		ByteBuffer bb = ByteBuffer.allocate(16);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putLong(uuid.getLeastSignificantBits());
		bb.putLong(uuid.getMostSignificantBits());
		return bb.array();
	}

	public static String bytesToUuid(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		long lsb = bb.getLong();
		long msb = bb.getLong();
		UUID uuid = new UUID(msb, lsb);
		return uuidToString(uuid);
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
		String value = bytesToEncodedString(bytes);
		addProperty(json, field, value);
	}

	public static byte[] getBytes(JSONObject json, String field) {
		try {
			String value = json.getString(field);
			return encodedStringToBytes(value);
		} catch (JSONException e) {
			Log.e(TAG, "failed to read bytes");
			e.printStackTrace();
		}

		return null;
	}

	public static byte[] encodedStringToBytes(String encoded) {
		return Base64.decode(encoded, Base64.NO_WRAP);
	}

	public static String bytesToEncodedString(byte[] bytes) {
		return Base64.encodeToString(bytes, Base64.NO_WRAP);
	}

	public static int byteArrayToShort(byte[] bytes) {
		return byteArrayToShort(bytes, 0);
	}

	public static int byteArrayToShort(byte[] bytes, int offset) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		return bb.getShort(offset);
	}

	public static byte[] shortToByteArray(int value) {
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putShort((short) value);
		return bb.array();
	}

	public static byte[] getValue(JSONObject json) {
		return getBytes(json, BleCoreTypes.PROPERTY_VALUE);
	}

	public static void setValue(JSONObject json, byte[] value) {
		BleUtils.addBytes(json, BleCoreTypes.PROPERTY_VALUE, value);
	}

	public static int signedToUnsignedByte(byte b) {
		return b & 0xFF;
	}

	private static final int STR_ADDRESS_LENGTH = 17;
	// String Bluetooth address, such as "00:43:A8:23:10:F0"
	private static final int ADDRESS_LENGTH = 6;

	public static byte[] addressToBytes(String address) {
		if (address == null || address.length() != STR_ADDRESS_LENGTH) {
			return null;
		}

		byte[] result = new byte[ADDRESS_LENGTH];

		for (int i = 0; i < ADDRESS_LENGTH; ++i) {
			result[i] = Byte.valueOf(address.substring(3*i, 3*i+2), 16);
		}

		return result;
	}

	public static String bytesToAddress(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02X:", b));
		}
		sb.deleteCharAt(sb.length()-1); // remove last semicolon
		return sb.toString();
	}

}
