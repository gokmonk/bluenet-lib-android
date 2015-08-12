package nl.dobots.bluenet.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import nl.dobots.bluenet.BleUtils;

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
