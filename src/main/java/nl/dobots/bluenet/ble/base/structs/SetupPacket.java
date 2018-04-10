package nl.dobots.bluenet.ble.base.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

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
 * Created on 10-04-18
 *
 * @author Bart van Vliet
 */

public class SetupPacket {
	private static final String TAG = SetupPacket.class.getCanonicalName();
	public static final int PACKET_SIZE = 1+1+16+16+16+4+16+2+2;

	private int _type;
	private int _id;
	private byte[] _adminKeyBytes = null;
	private byte[] _memberKeyBytes = null;
	private byte[] _guestKeyBytes = null;
	private int _meshAccessAddress;
	private byte[] _iBeaconUuid = null;
	private int _iBeaconMajor;
	private int _iBeaconMinor;

	public SetupPacket(int type, int id, String adminKey, String memberKey, String guestKey, int meshAccessAddress, String iBeaconUuid, int iBeaconMajor, int iBeaconMinor) {
		_type = type;
		_id = id;
		_adminKeyBytes = BleUtils.hexStringToBytes(adminKey);
		_memberKeyBytes = BleUtils.hexStringToBytes(memberKey);
		_guestKeyBytes = BleUtils.hexStringToBytes(guestKey);
		_meshAccessAddress = meshAccessAddress;
		_iBeaconUuid = BleUtils.uuidToBytes(iBeaconUuid);
		_iBeaconMajor = iBeaconMajor;
		_iBeaconMinor = iBeaconMinor;
	}

	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bytes.length < PACKET_SIZE) {
			return false;
		}

		// No need for this to be implemented
		return false;
	}

	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(PACKET_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte)_type);
		bb.put((byte)_id);
		bb.put(_adminKeyBytes);
		bb.put(_memberKeyBytes);
		bb.put(_guestKeyBytes);
		bb.put(BleUtils.uint32ToByteArray(_meshAccessAddress));
		bb.put(_iBeaconUuid);
		bb.put(BleUtils.shortToByteArray(_iBeaconMajor));
		bb.put(BleUtils.shortToByteArray(_iBeaconMinor));
		return bb.array();
	}

	public String toString() {
		return String.format(Locale.ENGLISH, "type=%d id=%d accessAddress=%d ibeacon=[%s %d %d]", _type, _id, _meshAccessAddress, BleUtils.bytesToString(_iBeaconUuid), _iBeaconMajor, _iBeaconMinor);
	}
}



