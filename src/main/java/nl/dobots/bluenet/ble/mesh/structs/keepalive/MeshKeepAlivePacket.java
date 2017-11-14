package nl.dobots.bluenet.ble.mesh.structs.keepalive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.mesh.structs.MeshPayload;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;


/**
 * Copyright (c) 2015 Bart van Vliet <bart@dobots.nl>. All rights reserved.
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
 * Created on 13-11-17
 *
 * @author Bart van Vliet
 */

public class MeshKeepAlivePacket implements MeshPayload {

	private static final String TAG = MeshKeepAlivePacket.class.getCanonicalName();


	// 1B type
	private static final int KEEP_ALIVE_PACKET_HEADER_SIZE = 1;
	protected static final int KEEP_ALIVE_PACKET_MAX_PAYLOAD_SIZE = (BluenetConfig.MESH_MAX_PAYLOAD_SIZE - KEEP_ALIVE_PACKET_HEADER_SIZE);

	// Define the different types
	private static final int TYPE_SAME_TIMEOUT = 1;

	// Type of the payload
	private int _type;

	// The payload
	private MeshKeepAlivePayload _payload;

	/**
	 * Create an empty, invalid, packet
	 */
	public MeshKeepAlivePacket() {
		_type = -1;
		_payload = null;
	}

	/**
	 * Set the payload of the packet, automatically sets the correct type.
	 * @param payload The payload to set.
	 * @return true on success.
	 */
	public boolean setPayload(MeshKeepAlivePayload payload) {
		if (payload == null) {
			return false;
		}
		if (payload instanceof MeshKeepAliveSameTimeoutPacket) {
			_type = TYPE_SAME_TIMEOUT;
		}
		_payload = payload;
		return true;
	}

	/**
	 * Parses the given byte array into a keep alive packet
	 * @param bytes byte array containing the keep alive packet
	 * @return true when parsing was successful
	 */
	@Override
	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bytes.length < KEEP_ALIVE_PACKET_HEADER_SIZE) {
//			BleLog.getInstance().LOGe(TAG, "Invalid length: " + _size);
//			BleLog.getInstance().LOGe(TAG, "from mesh message: " + BleUtils.bytesToString(bytes));
			_type = -1;
			_payload = null;
			return false;
		}

		_type = BleUtils.toUint8(bb.get());
		boolean success = false;
		switch (_type) {
			case TYPE_SAME_TIMEOUT:
				_payload = new MeshKeepAliveSameTimeoutPacket();
				success = _payload.fromArray(bb);
				break;
		}
		if (!success) {
			_type = -1;
			_payload = null;
			return false;
		}
		return true;
	}

	/**
	 * Convert the keep alive packet into a byte array to be set as payload
	 * @return byte array representation of the keep alive packet
	 */
//	@Override
	public boolean toArray(ByteBuffer bb) {
		boolean success = false;
		switch (_type) {
			case TYPE_SAME_TIMEOUT:
				success = true;
				break;
		}
		if (!success) {
			return false;
		}
		if (_payload == null) {
			return false;
		}
		if (bb.remaining() < KEEP_ALIVE_PACKET_HEADER_SIZE + _payload.getSize()) {
			return false;
		}

		bb.put((byte)_type);
		return _payload.toArray(bb);
	}

	/**
	 * Convert the keep alive packet into a byte array to be set as payload
	 * @return byte array representation of the keep alive packet
	 */
	@Override
	public byte[] toArray() {
		if (_payload == null) {
			return null;
		}
		ByteBuffer bb = ByteBuffer.allocate(getSize());
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (!toArray(bb)) {
			return null;
		}
		return bb.array();
	}

	/**
	 * Get the size in bytes
	 * @return size
	 */
	public int getSize() {
		if (_payload == null) {
			return KEEP_ALIVE_PACKET_HEADER_SIZE;
		}
		return KEEP_ALIVE_PACKET_HEADER_SIZE + _payload.getSize();
	}

	/**
	 * For debug purposes, create a string representation of the keep alive packet
	 * @return string representation of the object
	 */
	@Override
	public String toString() {
		if (_payload == null) {
			return String.format(Locale.ENGLISH, "type: %i payload: null", _type);
		}
		return String.format(Locale.ENGLISH, "type: %i payload: %s", _type, _payload.toString());
	}
}
