package nl.dobots.bluenet.ble.base.structs.mesh;

import junit.framework.Assert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.utils.BleUtils;

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
 * This class holds a mesh message which can be written to the connected device and will
 * be forwarded to all other nodes in the mesh network.
 *
 * The mesh message has the following fields:
 * 		* Channel: the channel on which the message should be sent, currently only one channel is
 * 					used, which is DATA_CHANNEL, see MeshMessageTypes for details
 * 		* Length: the length in bytes of the message, this includes target, type and payload
 * 		* Target: the MAC address of the target of this mesh message. or all 0 for a Broadcast
 * 					message
 * 		* Type: type of the mesh message, see MeshMessageTypes for details
 * 		* Payload: the payload data of the mesh message, this layout depends on the type
 *
 * The format is:
 * 		-----------------------------------------------------------------------------
 * 		| Channel | Reserved | Length (2B) | Target (6B) | Type (2B) | Payload (xB) |
 * 		-----------------------------------------------------------------------------
 *
 * A mesh message expects a target, which is the MAC address of the device for which the mesh message
 * is destined. Any node in the mesh network will forward the message, but only the node with the
 * given MAC address will process it.
 * Exception, by providing 0s as target, it becomes a broadcast and will be processed by every node
 * in the network
 *
 * Note: Byte Order is Little-Endian, i.e. Least Significant Bit comes first
 *
 * @author Dominik Egger
 */
public class BleMeshHubData extends BleMeshData {

	public static final int SCAN_MESSAGE = 101;

	// size of message without payload is:
	private static final int SIZE_WITHOUT_PAYLOAD = 8;

	protected byte[] sourceAddress;
	protected int messageType;
	protected byte[] payload;

//	/**
//	 * Create a mesh message from the parameters to be written to devices mesh characteristic
//	 * @param channel channel on which the message is sent in the mesh network
//	 * @param target MAC address of the message's recipient
//	 * @param type type of message included as payload
//	 * @param length length of data, includes target, type and payload
//	 * @param payload payload of the mesh message, i.e. the data to be sent into the mesh
//	 */
//	public BleMeshHubData(int channel, byte[] target, int type, int length, byte[] payload) {
//		Assert.assertTrue("target has to have length 6", target.length == 6);
//
//		this.channel = channel;
//		this.length = length;
//		this.target = target;
//		this.type = type;
//		this.payload = payload;
//	}

	/**
	 * Parses the given byte array into a mesh message
	 * @param bytes byte array containing the mesh message
	 */
	public BleMeshHubData(byte[] bytes) {
		super(bytes);

		ByteBuffer bb = ByteBuffer.wrap(data);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		// need to reverse the address because it is in little endian, and even though we
		// set the byte order to little endian, arrays are still being read as is, and not
		// reversed automatically
		byte[] address = new byte[BluenetConfig.BLE_DEVICE_ADDRESS_LENGTH];
		bb.get(address);
		sourceAddress = BleUtils.reverse(address);

		messageType = bb.getShort();
		payload = new byte[bb.remaining()];
		bb.get(payload);
	}

	/**
	 * Convert the mesh message into a byte array to be written to the mesh characteristic
	 * @return byte array representation of the mesh message
	 */
	public byte[] toArray() {

		ByteBuffer bb = ByteBuffer.allocate(SIZE_WITHOUT_PAYLOAD + payload.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put(sourceAddress);
		bb.putShort((short) messageType);
		bb.put(payload);

		data = bb.array();

		return super.toArray();
	}

	public int getMessageType() {
		return messageType;
	}

	public String getSourceAddress() {
		return BleUtils.bytesToAddress(sourceAddress);
	}

//	public byte[] getSourceAddress() {
//		return sourceAddress;
//	}

	/**
	 * For debug purposes, create a string representation of the mesh message
	 * @return string representation of the object
	 */
//	@Override
//	public String toString() {
//		return String.format("{channel: %d, length: %d, target: %s, type: %s, payload: %s}",
//				channel, length, Arrays.toString(target), type, Arrays.toString(payload));
//	}

	/**
	 * Get the mesh message's payload
	 * @return payload
	 */
	public byte[] getPayload() {
		return payload;
	}

	/**
	 * Set a new message payload
	 * @param payload new payload
	 */
	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

}
