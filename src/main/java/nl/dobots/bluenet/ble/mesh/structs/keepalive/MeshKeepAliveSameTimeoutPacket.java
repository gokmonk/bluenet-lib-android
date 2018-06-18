package nl.dobots.bluenet.ble.mesh.structs.keepalive;

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
public class MeshKeepAliveSameTimeoutPacket implements MeshKeepAlivePayload {

	private static final String TAG = MeshKeepAliveSameTimeoutPacket.class.getCanonicalName();

	public class MeshKeepAliveSameTimeoutItem {

		private int _crownstoneId;

		private int _actionSwitchState;

		public MeshKeepAliveSameTimeoutItem(int crownstoneId, int actionSwitchState) {
			_crownstoneId = crownstoneId;
			_actionSwitchState = actionSwitchState;
		}

		public MeshKeepAliveSameTimeoutItem(ByteBuffer bb) {
			_crownstoneId = BleUtils.toUint8(bb.get());
			_actionSwitchState = BleUtils.toUint8(bb.get());
		}

		public void toArray(ByteBuffer bb) {
			bb.put((byte) _crownstoneId);
			bb.put((byte) _actionSwitchState);
		}

		public int getCrownstoneId() {
			return _crownstoneId;
		}

		public void setCrownstoneId(int crownstoneId) {
			_crownstoneId = crownstoneId;
		}

		public int getActionSwitchState() {
			return _actionSwitchState;
		}

		public void setActionSwitchState(int actionSwitchState) {
			_actionSwitchState = actionSwitchState;
		}

		@Override
		public String toString() {
			return String.format(Locale.ENGLISH, "{id: %d, action: %d}", _crownstoneId, _actionSwitchState);
		}
	}

	// 2B timeout + 1B count
	private static final int HEADER_SIZE = 3;
	// 1B Crownstone ID + 1B action + switch state
	private static final int ITEM_SIZE = 2;
	// Max number of items in the list
	private static final int MAX_ITEMS = (MeshKeepAlivePacket.KEEP_ALIVE_PACKET_MAX_PAYLOAD_SIZE - HEADER_SIZE) / ITEM_SIZE;

	// Timeout in seconds
	private int _timeout;
	// Number of items in the list
	private int _count;
	// List of items
	private MeshKeepAliveSameTimeoutItem[] _list = new MeshKeepAliveSameTimeoutItem[MAX_ITEMS];

	/**
	 * Create an empty, invalid, ble stream message
	 */
	public MeshKeepAliveSameTimeoutPacket() {
		_timeout = 0;
		_count = 0;
	}

	public MeshKeepAliveSameTimeoutPacket(int timeout) {
		_timeout = timeout;
		_count = 0;
	}

//	/**
//	 * Parses the given byte array into a keep alive packet
//	 * @param bytes byte array containing the keep alive packet
//	 */
//	public MeshKeepAlivePacket(byte[] bytes) {
//	}

	/**
	 * Parses the given byte array into a keep alive packet
	 * @param bb byte buffer containing the keep alive packet
	 * @return true when parsing was successful
	 */
	@Override
	public boolean fromArray(ByteBuffer bb) {
//		ByteBuffer bb = ByteBuffer.wrap(bytes);
//		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bb.remaining() < HEADER_SIZE) {
//			BleLog.getInstance().LOGe(TAG, "Invalid length: " + _size);
//			BleLog.getInstance().LOGe(TAG, "from mesh message: " + BleUtils.bytesToString(bytes));
			_timeout = 0;
			_count = 0;
			return false;
		}

		_timeout = BleUtils.toUint16(bb.getShort());
		_count = BleUtils.toUint8(bb.get());
		if ((_count > MAX_ITEMS) || (bb.remaining() < _count * ITEM_SIZE)) {
//			BleLog.getInstance().LOGe(TAG, "Invalid length: " + _size);
//			BleLog.getInstance().LOGe(TAG, "from mesh message: " + BleUtils.bytesToString(bytes));
			_timeout = 0;
			_count = 0;
			return false;
		}

		for (int i = 0; i < _count; i++) {
			_list[i] = new MeshKeepAliveSameTimeoutItem(bb);
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
		bb.putShort((short) _timeout);
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
	 * For debug purposes, create a string representation of the keep alive packet
	 * @return string representation of the object
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < _count; i++) {
			sb.append(_list[i].toString());
		}
		return String.format(Locale.ENGLISH, "{timeout: %d, size: %d, list: [%s]}", _timeout, _count, sb.toString());
	}

	public int getTimeout() {
		return _timeout;
	}

	public void setTimeout(int timeout) {
		_timeout = timeout;
	}

	/**
	 * Get the number of items
	 * @return count
	 */
	public int getCount() {
		return _count;
	}

	public boolean addItem(int crownstoneId, int actionSwitchState) {
		if (_count < MAX_ITEMS) {
			_list[_count++] = new MeshKeepAliveSameTimeoutItem(crownstoneId, actionSwitchState);
			return true;
		}
		else {
			BleLog.getInstance().LOGe(TAG, "List is full");
			return false;
		}
	}

	public MeshKeepAliveSameTimeoutItem getItem(int index) {
		if (index < _count) {
			return _list[index];
		}
		else {
			BleLog.getInstance().LOGe(TAG, "index out of bounds");
			return null;
		}
	}

}
