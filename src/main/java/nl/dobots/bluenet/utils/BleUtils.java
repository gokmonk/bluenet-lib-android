package nl.dobots.bluenet.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import android.util.Base64;

public class BleUtils {

	public static final String BASE_UUID_START = "0000";
	public static final String BASE_UUID_END = "-0000-1000-8000-00805f9b34fb";
	private static final String TAG = BleUtils.class.getCanonicalName();

	private static final int STR_ADDRESS_LENGTH = 17;
	// String Bluetooth address, such as "00:43:A8:23:10:F0"
	private static final int ADDRESS_LENGTH = 6;

	public static String uuidToString(UUID uuid) {
		String uuidString = uuid.toString();

		if (uuidString.startsWith(BASE_UUID_START) && uuidString.endsWith(BASE_UUID_END))
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
			uuidStr = BASE_UUID_START + uuidStr + BASE_UUID_END;
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

	public static int signedToUnsignedByte(byte b) {
		return b & 0xFF;
	}

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
