package nl.dobots.bluenet.ble.base.structs.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;

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
 * Created on 3-12-15
 *
 * @author Dominik Egger
 */
public class BleMeshData {

	private static final int SIZE_WITHOUT_DATA = 4;

	protected int opCode;
	protected int handle;
	protected int dataLength;

	protected byte[] data;

	/**
	 * Parses the given byte array into a
	 * @param bytes byte array containing the
	 */
	public BleMeshData(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		opCode = bb.get();
		handle = bb.getShort();
		dataLength = bb.get();
		data = new byte[bb.remaining()];
		bb.get(data);
	}

	/**
	 * @return byte array representation of
	 */
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(SIZE_WITHOUT_DATA + data.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put((byte) opCode);
		bb.putShort((short) handle);
		bb.put((byte) dataLength);
		bb.put(data);

		return bb.array();
	}

	@Override
	public String toString() {
		return "BleMeshData{" +
				"opCode=" + opCode +
				", handle=" + handle +
				", dataLength=" + dataLength +
				", data=" + Arrays.toString(data) +
				'}';
	}

	public int getDataLength() {
		return dataLength;
	}

	public int getOpCode() {
		return opCode;
	}

	public byte[] getData() {
		return data;
	}
}
