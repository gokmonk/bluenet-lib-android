package nl.dobots.bluenet.ble.base.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nl.dobots.bluenet.utils.BleUtils;

/**
 * Copyright (c) 2018 Bart van Vliet <bart@dobots.nl>. All rights reserved.
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
 * Created on 12-04-18
 *
 * @author Bart van Vliet
 */

public class ControlResultPacket extends StreamMsg {
	private int _errorCode;
	private byte[] _data;

	public ControlResultPacket() {
		super();
		_errorCode = -1;
	}

	public int getErrorCode() {
		return _errorCode;
	}

	public byte[] getData() {
		return _data;
	}

	@Override
	public boolean fromArray(byte[] bytes) {
		if (!super.fromArray(bytes)) {
			return false;
		}
		if (getLength() < 2) {
			// Payload should at least have 2 bytes for error code.
			return false;
		}
		ByteBuffer bb = ByteBuffer.wrap(getPayload());
		bb.order(ByteOrder.LITTLE_ENDIAN);
		_errorCode = BleUtils.toUint16(bb.getShort());
		_data = new byte[getLength() - 2];
		bb.get(_data);
		return true;
	}
}
