package nl.dobots.bluenet.ble.mesh.structs.multiswitch;

import java.nio.ByteBuffer;

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
public interface MeshMultiSwitchPayload {

	/** Serialize the payload
	 *
	 * @return serialized byte array when successful, null on failure
	 */
	byte[] toArray();

	/** Serialize the payload
	 *
	 * @param bb byte buffer output
	 * @return true when successful
	 */
	boolean toArray(ByteBuffer bb);

	/** Deserialize the payload
	 *
	 * @param bb byte buffer with the data
	 * @return true when successful
	 */
	boolean fromArray(ByteBuffer bb);

	/** Get the size in bytes.
	 *
	 * @return size of the payload in bytes.
	 */
	int getSize();
}
