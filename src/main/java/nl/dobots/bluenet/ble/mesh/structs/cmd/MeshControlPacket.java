package nl.dobots.bluenet.ble.mesh.structs.cmd;

import nl.dobots.bluenet.ble.base.structs.ControlMsg;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;

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
public class MeshControlPacket extends MeshCommandPacket {

	private ControlMsg _controlMsg;

	public MeshControlPacket(ControlMsg message, int... ids) {
		super(BluenetConfig.MESH_CMD_CONTROL, ids);
		_controlMsg = message;
		setPayload(message.toArray());
	}

	public MeshControlPacket(byte[] bytes) {
		super(bytes);
		_controlMsg = new ControlMsg(getPayload());
	}

	@Override
	protected String payloadToString() {
		return _controlMsg.toString();
	}
}
