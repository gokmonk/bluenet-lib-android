package nl.dobots.bluenet.ble.base.structs;

import android.support.annotation.Nullable;

import nl.dobots.bluenet.ble.base.BleBaseEncryption;
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
 * Created on 29-9-16
 *
 * @author Bart van Vliet
 */
public class EncryptionKeys {
	private String _adminKey = null;
	private String _memberKey = null;
	private String _guestKey = null;
	private byte[] _adminKeyBytes = null;
	private byte[] _memberKeyBytes = null;
	private byte[] _guestKeyBytes = null;

	protected EncryptionKeys() {}

	public EncryptionKeys(@Nullable String adminKey, @Nullable String memberKey, @Nullable String guestKey) {
		_adminKey = adminKey;
		_memberKey = memberKey;
		_guestKey = guestKey;
		_adminKeyBytes = null;
		if (_adminKey != null) {
			_adminKeyBytes = BleUtils.hexStringToBytes(adminKey);
		}
		_memberKeyBytes = null;
		if (_memberKey != null) {
			_memberKeyBytes = BleUtils.hexStringToBytes(memberKey);
		}
		_guestKeyBytes = null;
		if (_guestKey != null) {
			_guestKeyBytes = BleUtils.hexStringToBytes(guestKey);
		}
	}

	public EncryptionKeys(@Nullable byte[] adminKey, @Nullable byte[] memberKey, @Nullable byte[] guestKey) {
		_adminKeyBytes = adminKey;
		_memberKeyBytes = memberKey;
		_guestKeyBytes = guestKey;
		_adminKey = null;
		if (_adminKeyBytes != null) {
			_adminKey = BleUtils.bytesToHexString(_adminKeyBytes);
		}
		_memberKey = null;
		if (_memberKeyBytes != null) {
			_memberKey = BleUtils.bytesToHexString(_memberKeyBytes);
		}
		_guestKey = null;
		if (_guestKeyBytes != null) {
			_guestKey = BleUtils.bytesToHexString(_guestKeyBytes);
		}
	}

	public String getAdminKeyString() {
		return _adminKey;
	}

	public String getMemberKeyString() {
		return _memberKey;
	}

	public String getGuestKeyString() {
		return _guestKey;
	}

	public byte[] getAdminKey() {
		return _adminKeyBytes;
	}

	public byte[] getMemberKey() {
		return _memberKeyBytes;
	}

	public byte[] getGuestKey() {
		return _guestKeyBytes;
	}

	public byte[] getKey(char accessLevel) {
		switch (accessLevel) {
			case BleBaseEncryption.ACCESS_LEVEL_ADMIN:
				return getAdminKey();
			case BleBaseEncryption.ACCESS_LEVEL_MEMBER:
				return getMemberKey();
			case BleBaseEncryption.ACCESS_LEVEL_GUEST:
				return getGuestKey();
			default:
				return null;
		}
	}

	public static byte[] getAdminKey(EncryptionKeys keys) {
		if (keys == null) {
			return null;
		}
		byte [] key = keys.getAdminKey();
		return key;
	}

	public static byte[] getMemberKey(EncryptionKeys keys) {
		if (keys == null) {
			return null;
		}
		byte [] key = keys.getMemberKey();
		return key;
	}

	public static byte[] getGuestKey(EncryptionKeys keys) {
		if (keys == null) {
			return null;
		}
		byte [] key = keys.getGuestKey();
		return key;
	}

	public KeyAccessLevelPair getHighestKey() {
		if (getAdminKey() != null) {
			return new KeyAccessLevelPair(getAdminKey(), BleBaseEncryption.ACCESS_LEVEL_ADMIN);
		}
		if (getMemberKey() != null) {
			return new KeyAccessLevelPair(getMemberKey(), BleBaseEncryption.ACCESS_LEVEL_MEMBER);
		}
		if (getGuestKey() != null) {
			return new KeyAccessLevelPair(getGuestKey(), BleBaseEncryption.ACCESS_LEVEL_GUEST);
		}
		return null;
	}

	public class KeyAccessLevelPair {
		public byte[] key;
		public char accessLevel;

		public KeyAccessLevelPair(byte[] key, char accessLevel) {
			this.key = key;
			this.accessLevel = accessLevel;
		}
	}
}
