package nl.dobots.bluenet.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import nl.dobots.bluenet.BleUtils;

/**
 * Created by dominik on 15-7-15.
 */
public class BleTrackedDevice {

	byte[] address;
	int rssi;

	public BleTrackedDevice(byte[] address, int rssi) {
		this.address = address;
		this.rssi = rssi;
	}

	public BleTrackedDevice(String address, int rssi) {
		this.rssi = rssi;
		BleUtils.addressToBytes(address);
	}

	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(7);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put(address);
		bb.put((byte) rssi);

		return bb.array();
	}

	@Override
	public String toString() {
		return String.format("{address: %s, rssi: %d}", Arrays.toString(address), rssi);
	}

	public byte[] getAddress() {
		return address;
	}

	public void setAddress(byte[] address) {
		this.address = address;
	}

	public int getRssi() {
		return rssi;
	}

	public void setRssi(int rssi) {
		this.rssi = rssi;
	}
}
