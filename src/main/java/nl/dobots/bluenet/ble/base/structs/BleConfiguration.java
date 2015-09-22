package nl.dobots.bluenet.ble.base.structs;

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
 * Created on 15-7-15
 *
 * This class is an object to hold and ease conversion of a Bluenet Configuration message. A
 * Configuration message is used to read and write to a configuration characteristic. A configuration
 * message contains the following 3 fields:
 * 		* Type
 * 		* Length
 * 		* Payload
 *
 * And the format is as follows:
 * 		-------------------------------------------------------------------------------
 * 		| Type | Reserved | Length (LSB) | Length (MSB) | Payload (Length bytes) .... |
 * 		-------------------------------------------------------------------------------
 *
 * 	The reserved byte is used for byte alignment of the length and payload.
 * 	Type defines the type of configuration and defines the layout of the payload, see
 * 	  @BluenetConfig for a list of possible types
 * 	Length defines the number of bytes used by the payload
 *
 * 	Note: Byte Order is Little-Endian, i.e. Least Significant Bit comes first
 *
 * @author Dominik Egger
 */
public class BleConfiguration {

	// the type of the configuration, see @BluenetConfig for a list of possible types
	private int type;
	// the length (in bytes) of the payload
	private int length;
	// the payload, the layout of the payload is defined by the type
	private byte[] payload;

	/**
	 * Create a ble configuration message from the type, length and payload
	 * @param type type of the configuration
	 * @param length number of bytes of the payload
	 * @param payload payload (actual configuration value to be written)
	 */
	public BleConfiguration(int type, int length, byte[] payload) {
		this.type = type;
		this.length = length;
		this.payload = payload;
	}

	/**
	 * Creates a ble configuration message by parsing the byte array
	 * @param bytes byte array containing a configuration message
	 */
	public BleConfiguration(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		type = bb.get();
		bb.get(); // skip the RESERVED
		length = bb.getShort();
		payload = new byte[length];
		bb.get(payload);
	}

	/**
	 * Convert this ble configuration message to a byte array to be written to the configuration
	 * characteristic
	 * @return byte array representation of the ble configuration message
	 */
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(4 + payload.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put((byte) type);
		bb.put((byte) BluenetConfig.RESERVED); // add RESERVED (for byte alignment)
		bb.putShort((short) length);
		bb.put(payload);

		return bb.array();
	}

	@Override
	/**
	 * For debug purposes, create a string representation of the ble configuration message
	 */
	public String toString() {
		return String.format("{type: %d, length: %d, payload: %s}", type, length, Arrays.toString(payload));
	}

	/**
	 * Return the type of the configuration message, see BluenetConfig for a list of possible types
	 * @return type of the configuration message
	 */
	public int getType() {
		return type;
	}

//	/**
//	 * Manually set the type of the configuration message, see BluenetConfig for a list of possible
//	 * types
//	 * @param type new type
//	 */
//	public void setType(int type) {
//		this.type = type;
//	}

	/**
	 * Get the number of bytes used for the payload
	 * @return number of bytes of the payload
	 */
	public int getLength() {
		return length;
	}

//	/**
//	 * Manually set the number of bytes of the payload
//	 * @param length new length
//	 */
//	public void setLength(int length) {
//		this.length = length;
//	}

	/**
	 * Return the payload of the configuration message
	 * @return payload of the configuration message
	 */
	public byte[] getPayload() {
		return payload;
	}

//	public void setPayload(byte[] payload) {
//		this.payload = payload;
//	}

}
