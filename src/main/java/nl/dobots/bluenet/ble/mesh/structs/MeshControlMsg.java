package nl.dobots.bluenet.ble.mesh.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

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
 * Created on 15-7-15
 * Updated for new protocol on 27-1-17
 *
 * This class holds a mesh control message which can be written to the connected device. The _payload
 * of the message will then be forwarded to all other nodes in the mesh network.
 *
 * The mesh message has the following fields:
 * 		* Handle: the _handle on which the message should be sent, see MeshMessageTypes for details
 * 		* Length: the _length in bytes of the _payload
 * 		* Payload: the _payload data of the mesh message, this layout depends on the type
 *
 * The format is:
 * 		--------------------------------------------------
 * 		| Handle | Reserved | Length (2B) | Payload (xB) |
 * 		--------------------------------------------------
 *
 * Note: Byte Order is Little-Endian, i.e. Least Significant Bit comes first
 *
 * @author Dominik Egger
 */
public class MeshControlMsg {

	// size of message without _payload is:
	// 1B _handle + 1B RESERVED + 2B _length
	private static final int SIZE_WITHOUT_PAYLOAD = 4;

	// _handle on which the message is sent in the mesh network
	private int _handle;
	// _length of data, includes target, type and _payload
	private int _length;
	// _payload of the mesh message, i.e. the data to be sent into the mesh
	private byte[] _payload;

	/**
	 * Create a mesh message from the parameters to be written to devices mesh characteristic
	 * @param handle _handle on which the message is sent in the mesh network
	 * @param length _length of data, includes target, type and _payload
	 * @param payload _payload of the mesh message, i.e. the data to be sent into the mesh
	 */
	public MeshControlMsg(int handle, int length, byte[] payload) {
		this._handle = handle;
		this._length = length;
		this._payload = payload;
	}
	
//	public MeshControlMsg(int _handle, int _length, MeshPayload _payload) {
//
//	}

	/**
	 * Parses the given byte array into a mesh message
	 * @param bytes byte array containing the mesh message
	 */
	public MeshControlMsg(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		_handle = bb.get();
		bb.get(); // skip reserved field
		_length = bb.getShort();
		_payload = new byte[bb.remaining()];
		bb.get(_payload);
	}

	/**
	 * Convert the mesh message into a byte array to be written to the mesh characteristic
	 * @return byte array representation of the mesh message
	 */
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(SIZE_WITHOUT_PAYLOAD + _payload.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put((byte) _handle);
		bb.put((byte) BluenetConfig.RESERVED);
		bb.putShort((short) _length);
		bb.put(_payload);

		return bb.array();
	}

	/**
	 * For debug purposes, create a string representation of the mesh message
	 * @return string representation of the object
	 */
	@Override
	public String toString() {
		return String.format(Locale.ENGLISH, "{_handle: %d, _length: %d, _payload: %s}",
				_handle, _length, Arrays.toString(_payload));
	}

	/**
	 * Get the handle
	 * @return handle
	 */
	public int getHandle() {
		return _handle;
	}

	/**
	 * Set a new handle
	 * @param handle new handle
	 */
	public void setHandle(int handle) {
		this._handle = handle;
	}

	/**
	 * Get the payload size
	 * @return size of the payload in bytes
	 */
	public int getPayloadSize() {
		return _length;
	}

//	/**
//	 * Set a new payload size
//	 * @param new size in bytes
//	 */
//	public void setPayloadSize(int length) {
//		this._length = length;
//	}

	/**
	 * Get the mesh message's _payload
	 * @return _payload
	 */
	public byte[] getPayload() {
		return _payload;
	}

	/**
	 * Set a new message _payload
	 * @param payload new _payload
	 */
	public void setPayload(byte[] payload) {
		this._payload = payload;
	}

}
