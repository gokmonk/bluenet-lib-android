package nl.dobots.bluenet.ble.mesh.structs.cmd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

import nl.dobots.bluenet.ble.mesh.structs.MeshPayload;
import nl.dobots.bluenet.utils.BleUtils;

/**
 * Copyright (c) 2017 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p>
 * Created on 27-1-17
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class MeshCommandPacket implements MeshPayload {

	public static final String TAG = MeshCommandPacket.class.getCanonicalName();

	// 2B message type + 1B number of ids
	private static final int COMMAND_PACKET_HEADER_SIZE = 3;
	// 2B Crownstone ID
	private static final int CROWNSTONE_ID_SIZE = 2; // bytes

	private int _messageType;

	private int _numberOfIds;

	private int[] _ids;

	private byte[] _payload;

	protected MeshCommandPacket(int messageType, int... ids) {
		_messageType = messageType;
		_numberOfIds = ids.length;
		_ids = ids;
	}

	public MeshCommandPacket(int messageType, byte[] payload, int... ids) {
		_messageType = messageType;
		_numberOfIds = ids.length;
		_ids = ids;
		_payload = payload;
	}

	public MeshCommandPacket(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		_messageType = BleUtils.toUint16(bb.getShort());
		_numberOfIds = BleUtils.toUint8(bb.get());
		_ids = new int[_numberOfIds];
		for (int i = 0; i < _numberOfIds; i++) {
			_ids[i] = BleUtils.toUint16(bb.getShort());
		}
		_payload = new byte[bb.remaining()];
		bb.get(_payload);
	}

	@Override
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(COMMAND_PACKET_HEADER_SIZE +
				_numberOfIds * CROWNSTONE_ID_SIZE + _payload.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.putShort((short)_messageType);
		bb.put((byte)_numberOfIds);
		for (int i = 0; i < _numberOfIds; i++) {
			bb.putShort((short)_ids[i]);
		}
		bb.put(_payload);

		return bb.array();
	}

	public byte[] getPayload() {
		return _payload;
	}

	public void setPayload(byte[] payload) {
		_payload = payload;
	}

	protected String payloadToString() {
		return "";
	}

	@Override
	public String toString() {
		return String.format(Locale.ENGLISH, "{messageType: %d, numIds: %d [%s], payload: %s}",
				_messageType, _numberOfIds, Arrays.toString(_ids), payloadToString());
	}
}
