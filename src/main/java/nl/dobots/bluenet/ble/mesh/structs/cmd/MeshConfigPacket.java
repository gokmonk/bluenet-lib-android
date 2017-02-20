package nl.dobots.bluenet.ble.mesh.structs.cmd;

import nl.dobots.bluenet.ble.base.structs.ConfigurationMsg;
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
public class MeshConfigPacket extends MeshCommandPacket {

	private ConfigurationMsg _configMsg;

	public MeshConfigPacket(ConfigurationMsg message, int... ids) {
		super(BluenetConfig.MESH_CMD_CONFIG, ids);
		_configMsg = message;
		setPayload(_configMsg.toArray());
	}

	public MeshConfigPacket(byte[] bytes) {
		super(bytes);
		_configMsg = new ConfigurationMsg(getPayload());
	}

	@Override
	protected String payloadToString() {
		return _configMsg.toString();
	}
}
