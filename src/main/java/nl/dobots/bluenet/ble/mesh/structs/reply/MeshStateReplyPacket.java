package nl.dobots.bluenet.ble.mesh.structs.reply;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nl.dobots.bluenet.ble.base.structs.StateMsg;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
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
public class MeshStateReplyPacket extends MeshCommandReplyPacket {

	// 1B crownstone id
	protected static final int STATE_REPLY_PACKET_HEADER_SIZE = 1;

	private int _crownstoneId;
	private StateMsg _stateMsg;

	public MeshStateReplyPacket() {
		super();
		_crownstoneId = 0;
		_stateMsg = null;
	}

//	public MeshStateReplyPacket(int crownstoneId, StateMsg message, long messageNumber) {
//		super(BluenetConfig.MESH_REPLY_STATE, messageNumber);
//		_crownstoneId = crownstoneId;
//		_stateMsg = message;
//		setNumberOfReplies(1);
////		setPayload(_stateMsg.toArray()); // TODO: add crownstone id
//	}

	@Override
	public boolean fromArray(byte[] bytes) {
		if (!super.fromArray(bytes)) {
			return false;
		}
		byte[] payload = getPayload();
		if (payload.length < STATE_REPLY_PACKET_HEADER_SIZE) {
			return false;
		}
		ByteBuffer bb = ByteBuffer.wrap(payload);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		_crownstoneId = BleUtils.toUint8(bb.get());

		byte[] stateMsg = new byte[bb.remaining()];
		bb.get(stateMsg);
		_stateMsg = new StateMsg();
		if (!_stateMsg.fromArray(stateMsg)) {
			_stateMsg = null;
			return false;
		}
		return true;
	}

	public StateMsg getReplyItem() {
		return _stateMsg;
	}

}
