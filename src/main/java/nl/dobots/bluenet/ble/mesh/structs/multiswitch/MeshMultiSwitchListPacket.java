package nl.dobots.bluenet.ble.mesh.structs.multiswitch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

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
public class MeshMultiSwitchListPacket implements MeshMultiSwitchPayload {
	public static final String TAG = MeshMultiSwitchListPacket.class.getCanonicalName();

	public class MultiSwitchListItem {
		private int _crownstoneId;
		private int _switchState;
		private int _timeout;
		private int _intent;

		public MultiSwitchListItem(int crownstoneId, int switchState, int timeout, int intent) {
			this._crownstoneId = crownstoneId;
			this._switchState = switchState;
			this._timeout = timeout;
			this._intent = intent;
		}

		public MultiSwitchListItem(ByteBuffer bb) {
			_crownstoneId =    BleUtils.toUint8(bb.get());
			_switchState =     BleUtils.toUint8(bb.get());
			_timeout =         BleUtils.toUint16(bb.getShort());
			_intent =          BleUtils.toUint8(bb.get());
		}

		public void toArray(ByteBuffer bb) {
			bb.put((byte) _crownstoneId);
			bb.put((byte) _switchState);
			bb.putShort((short) _timeout);
			bb.put((byte) _intent);
		}

		public String toString() {
			return String.format(Locale.ENGLISH, "{id: %d, switch: %d, timeout: %d, intent: %d}", _crownstoneId, _switchState, _timeout, _intent);
		}
	}

	// 1B count
	private static final int HEADER_SIZE = 1;
	// 1B Crownstone ID + 1B switch state + 2B timeout + 1B intent
	private static final int ITEM_SIZE = 5;
	// Max number of items in the list
	private static final int MAX_ITEMS = (MeshMultiSwitchPacket.MULTI_SWITCH_PACKET_MAX_PAYLOAD_SIZE - HEADER_SIZE) / ITEM_SIZE;

	// Number of items in the list
	private int _count;
	// List of items
	private MultiSwitchListItem[] _list = new MultiSwitchListItem[MAX_ITEMS];


	public MeshMultiSwitchListPacket() {
		_count = 0;
	}


	@Override
	public boolean fromArray(ByteBuffer bb) {
		if (bb.remaining() < HEADER_SIZE) {
			_count = 0;
			return false;
		}
		_count = BleUtils.toUint8(bb.get());
		if ((_count > MAX_ITEMS) || (bb.remaining() < _count * ITEM_SIZE)) {
//			BleLog.getInstance().LOGe(TAG, "Invalid length: " + _size);
//			BleLog.getInstance().LOGe(TAG, "from mesh message: " + BleUtils.bytesToString(bytes));
			_count = 0;
			return false;
		}

		for (int i=0; i < _count; i++) {
			_list[i] = new MultiSwitchListItem(bb);
		}
		return true;
	}

	/**
	 * Convert the packet into a byte array to be set as payload
	 * @return byte array representation of the packet
	 */
	@Override
	public boolean toArray(ByteBuffer bb) {
		if (bb.remaining() < getSize()) {
			return false;
		}
		bb.put((byte)_count);

		for (int i = 0; i < _count; i++) {
			_list[i].toArray(bb);
		}
		return true;
	}

	/**
	 * Convert the packet into a byte array to be set as payload
	 * @return byte array representation of the packet
	 */
	@Override
	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(getSize());
		bb.order(ByteOrder.LITTLE_ENDIAN);
		toArray(bb);
		return bb.array();
	}

	/**
	 * Get the size of the packet in bytes
	 * @return size
	 */
	@Override
	public int getSize() {
		return HEADER_SIZE + _count * ITEM_SIZE;
	}

	/**
	 * For debug purposes, create a string representation of the multi switch packet
	 * @return string representation of the object
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < _count; i++) {
			sb.append(_list[i].toString());
		}
		return String.format(Locale.ENGLISH, "{size: %d, list: [%s]}", _count, sb.toString());
	}

	/**
	 * Get the number of items
	 * @return count
	 */
	public int getCount() {
		return _count;
	}

	public boolean addItem(int crownstoneId, int switchState, int timeout, int intent) {
		if (_count < MAX_ITEMS) {
			_list[_count++] = new MultiSwitchListItem(crownstoneId, switchState, timeout, intent);
			return true;
		}
		else {
			BleLog.getInstance().LOGe(TAG, "List is full");
			return false;
		}
	}

	public MultiSwitchListItem getMultiSwitchItem(int index) {
		if (index < _count) {
			return _list[index];
		}
		else {
			BleLog.getInstance().LOGe(TAG, "index out of bounds");
			return null;
		}
	}
}
