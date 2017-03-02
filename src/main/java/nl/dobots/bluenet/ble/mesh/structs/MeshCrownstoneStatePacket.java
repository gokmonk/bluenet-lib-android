package nl.dobots.bluenet.ble.mesh.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.utils.BleLog;
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
public class MeshCrownstoneStatePacket implements MeshPayload {

	private static final String TAG = MeshCrownstoneStatePacket.class.getCanonicalName();

	public class CrownstoneStateItem {

		private int _crownstoneId;

		private int _switchState;

		private int _powerUsage;

		private int _accumulatedEnergy;

		public CrownstoneStateItem(int crownstoneId, int switchState, int powerUsage, int accumulatedEnergy) {
			_crownstoneId = crownstoneId;
			_switchState = switchState;
			_powerUsage = powerUsage;
			_accumulatedEnergy = accumulatedEnergy;
		}

		public int getCrownstoneId() {
			return _crownstoneId;
		}

		public void setCrownstoneId(int crownstoneId) {
			_crownstoneId = crownstoneId;
		}

		public int getSwitchState() {
			return _switchState;
		}

		public void setSwitchState(int switchState) {
			_switchState = switchState;
		}

		public int getPowerUsage() {
			return _powerUsage;
		}

		public void setPowerUsage(int powerUsage) {
			_powerUsage = powerUsage;
		}

		public int getAccumulatedEnergy() {
			return _accumulatedEnergy;
		}

		public void setAccumulatedEnergy(int accumulatedEnergy) {
			_accumulatedEnergy = accumulatedEnergy;
		}

		@Override
		public String toString() {
			return String.format(Locale.ENGLISH, "{id: %d, switchState: %d, powerUsage: %d, accumulatedEnergy: %d}",
					_crownstoneId, _switchState, _powerUsage, _accumulatedEnergy);
		}
	}

	// 1B head + 1B tail + 1B size
	private static final int CROWNSTONE_STATE_PACKET_HEADER_SIZE = 3;
	// 2B Crownstone ID + 1B switch state + 4B power usage + 4B accumulated energy
	private static final int CROWNSTONE_STATE_ITEM_SIZE = 11; // bytes
	// max capacity of the list
	private static final int MAX_LIST_ELEMENTS = BluenetConfig.MESH_MAX_PAYLOAD_SIZE / CROWNSTONE_STATE_ITEM_SIZE;

	private int _head;

	private int _tail;

	private int _size;

	private CrownstoneStateItem[] _list = new CrownstoneStateItem[MAX_LIST_ELEMENTS];

	public MeshCrownstoneStatePacket() {}

	public MeshCrownstoneStatePacket(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		_head = BleUtils.toUint8(bb.get());
		_tail = BleUtils.toUint8(bb.get());
		_size = BleUtils.toUint8(bb.get());

		for (int i = 0; i < MAX_LIST_ELEMENTS; i++) {
			int crownstoneId = BleUtils.toUint16(bb.getShort());
			int switchState = BleUtils.toUint8(bb.get());
			int powerUsage = bb.getInt();
			int accumulatedEnergy = bb.getInt();
			_list[i] = new CrownstoneStateItem(crownstoneId, switchState, powerUsage, accumulatedEnergy);
		}

	}

	@Override
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(BluenetConfig.MESH_MAX_PAYLOAD_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		bb.put((byte)_head);
		bb.put((byte)_tail);
		bb.put((byte)_size);

		for (int i = 0; i < _size; i++) {
			bb.putShort((short) _list[i].getCrownstoneId());
			bb.put((byte) _list[i].getSwitchState());
			bb.putInt(_list[i].getPowerUsage());
			bb.putInt(_list[i].getAccumulatedEnergy());
		}

		return bb.array();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < _size; i++) {
			sb.append(getCrownstoneState(i).toString());
		}
		return String.format(Locale.ENGLISH, "{head: %d, tail: %d, size: %d, list: [%s]", _head, _tail, _size,
				sb.toString());
	}

	private void incTail() {
		_tail = (_tail + 1) % MAX_LIST_ELEMENTS;
		if (++_size > MAX_LIST_ELEMENTS) {
			incHead();
		}
	}

	private void incHead() {
		_head = (_head + 1) % MAX_LIST_ELEMENTS;
	}

	public void addCrownstoneState(int crownstoneId, int switchState, int powerUsage, int accumulatedEnergy) {
		_list[_tail] = new CrownstoneStateItem(crownstoneId, switchState, powerUsage, accumulatedEnergy);
		incTail();
	}

	public CrownstoneStateItem getCrownstoneState(int index) {
		if (index < _size) {
			return _list[(_head + index) % MAX_LIST_ELEMENTS];
		} else {
			BleLog.getInstance().LOGe(TAG, "index out of bounds");
			return null;
		}
	}

	public CrownstoneStateItem popCrownstoneState() {
		CrownstoneStateItem item = _list[_head];
		incHead();
		return item;
	}
}
