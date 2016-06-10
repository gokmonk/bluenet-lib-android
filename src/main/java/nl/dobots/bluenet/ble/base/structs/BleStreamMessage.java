package nl.dobots.bluenet.ble.base.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 9-6-16
 * 
 * This class is an object to hold and ease conversion of a Bluenet stream messages. A
 * stream message is used to read and write to a stream characteristic. A stream
 * message contains the following 4 fields:
 * 		* Type
 * 		* OpCode
 * 		* Length
 * 		* Payload
 *
 * And the format is as follows:
 * 		-----------------------------------------------------------------------------
 * 		| Type | OpCode | Length (LSB) | Length (MSB) | Payload (Length bytes) .... |
 * 		-----------------------------------------------------------------------------
 *
 * 	The reserved byte is used for byte alignment of the length and payload.
 * 	Type defines the type of stream and defines the layout of the payload, see
 * 	  @BluenetConfig for a list of possible types
 * 	Length defines the number of bytes used by the payload
 *
 * 	Note: Byte Order is Little-Endian, i.e. Least Significant Bit comes first
 *
 *
 * @author Dominik Egger
 */
public class BleStreamMessage {

	// the type of the stream, see @BluenetConfig for a list of possible types
	private int type;
	// type of operation (read, write, notify)
	private int opCode;
	// the length (in bytes) of the payload
	private int length;
	// the payload, the layout of the payload is defined by the type
	private byte[] payload;

	/**
	 * Create a ble stream message from the type, op code, length and payload
	 * @param type type of the stream
	 * @param opCode op code of the stream
	 * @param length number of bytes of the payload
	 * @param payload payload (actual stream value to be written)
	 */
	public BleStreamMessage(int type, int opCode, int length, byte[] payload) {
		this.type = type;
		this.opCode = opCode;
		this.length = length;
		this.payload = payload;
	}

	/**
	 * Create a ble stream message from the type, length and payload
	 * this is a simplification, because in most of the cases, creating a stream object
	 * through the library is for a WRITE operation, so if no opCode is provided, it uses
	 * opCode = WRITE_VALUE as default
	 * @param type type of the stream
	 * @param length number of bytes of the payload
	 * @param payload payload (actual stream value to be written)
	 */
	public BleStreamMessage(int type, int length, byte[] payload) {
		this.type = type;
		this.opCode = BluenetConfig.WRITE_VALUE;
		this.length = length;
		this.payload = payload;
	}

	/**
	 * Creates a ble stream message by parsing the byte array
	 * @param bytes byte array containing a stream message
	 */
	public BleStreamMessage(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		type = bb.get();
		opCode = bb.get();
		length = bb.getShort();
		payload = new byte[length];
		bb.get(payload);
	}

	/**
	 * Convert this ble stream message to a byte array to be written to the stream
	 * characteristic
	 * @return byte array representation of the ble stream message
	 */
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(4 + payload.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put((byte) type);
		bb.put((byte) opCode);
		bb.putShort((short) length);
		bb.put(payload);

		return bb.array();
	}

	@Override
	/**
	 * For debug purposes, create a string representation of the ble stream message
	 */
	public String toString() {
		return String.format("{type: %d, opCode: %d, length: %d, payload: %s}", type, opCode, length, Arrays.toString(payload));
	}

	/**
	 * Return the type of the stream message, see BluenetConfig for a list of possible types
	 * @return type of the stream message
	 */
	public int getType() {
		return type;
	}

//	/**
//	 * Manually set the type of the stream message, see BluenetConfig for a list of possible
//	 * types
//	 * @param type new type
//	 */
//	public void setType(int type) {
//		this.type = type;
//	}

	/**
	 * Return the op code of the stream message, see ValueOpCodes in BluenetConfig for a list of
	 * possible op codes
	 * @return op code of the stream message
	 */
	protected int getOpCode() {
		return opCode;
	}

//	/**
//	 * Manually set the op code of the stream message, ee ValueOpCodes in BluenetConfig for a list of
//	 * possible op codes
//	 * @param type new type
//	 */
//	protected void setOpCode(int opCode) {
//		this.opCode = opCode;
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
	 * Return the payload of the stream message
	 * @return payload of the stream message
	 */
	public byte[] getPayload() {
		return payload;
	}

//	public void setPayload(byte[] payload) {
//		this.payload = payload;
//	}

}
