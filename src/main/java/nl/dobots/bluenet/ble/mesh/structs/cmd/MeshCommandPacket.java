package nl.dobots.bluenet.ble.mesh.structs.cmd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.mesh.structs.MeshPayload;
import nl.dobots.bluenet.ibeacon.BleIbeaconFilter;
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

	// 1B message type + 1B bitmask + 1B number of ids
	private static final int COMMAND_PACKET_HEADER_SIZE = 3;
	// 1B Crownstone ID
	private static final int CROWNSTONE_ID_SIZE = 1; // bytes

	private int _messageType;

	private int _bitMask;

	private int _numberOfIds;

	private ArrayList<Integer> _ids;

	private byte[] _payload;

	public MeshCommandPacket() {
		_messageType = -1;
		_bitMask = 0;
		_numberOfIds = 0;
		_ids = null;
	}

	public MeshCommandPacket(int messageType, int... ids) {
		_messageType = messageType;
		_bitMask = 0;
		_numberOfIds = ids.length;
		_ids = new ArrayList<>(_numberOfIds);
		for (int id: ids) {
			_ids.add(id);
		}
	}

	public int getBitmask() {
		return _bitMask;
	}

	public void setBitmask(int bitMask) {
		_bitMask = bitMask;
	}

	@Override
	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bytes.length < COMMAND_PACKET_HEADER_SIZE) {
			return false;
		}
		_messageType = BleUtils.toUint8(bb.get());
		_bitMask     = BleUtils.toUint8(bb.get());
		_numberOfIds = BleUtils.toUint8(bb.get());
		if (bytes.length < COMMAND_PACKET_HEADER_SIZE + _numberOfIds * CROWNSTONE_ID_SIZE) {
			_numberOfIds = 0;
			return false;
		}
		_ids = new ArrayList<>(_numberOfIds);
		for (int i = 0; i < _numberOfIds; i++) {
			_ids.add(BleUtils.toUint8(bb.get()));
		}
		_payload = new byte[bb.remaining()];
		bb.get(_payload);
		return true;
	}

	@Override
	public byte[] toArray() {
		if (_ids == null || _payload == null) {
			return null;
		}
		ByteBuffer bb = ByteBuffer.allocate(COMMAND_PACKET_HEADER_SIZE +
				_numberOfIds * CROWNSTONE_ID_SIZE + _payload.length);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put((byte)_messageType);
		bb.put((byte)_bitMask);
		bb.put((byte)_numberOfIds);
		if (_numberOfIds != _ids.size()) {
			return null;
		}
		for (int i = 0; i < _numberOfIds; i++) {
			bb.put(_ids.get(i).byteValue());
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

	public void addId(int id) {
		if (_ids == null) {
			_ids = new ArrayList<>();
			_numberOfIds = 0;
		}
		_ids.add(id);
		_numberOfIds++;
	}

	protected String payloadToString() {
		return BleUtils.bytesToString(_payload);
	}

	@Override
	public String toString() {
		return String.format(Locale.ENGLISH, "{messageType: %d, numIds: %d [%s], payload: %s}",
				_messageType, _numberOfIds, Arrays.toString(_ids.toArray()), payloadToString());
	}
}
