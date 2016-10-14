package nl.dobots.bluenet.ble.base.structs;

import android.support.annotation.Nullable;

import nl.dobots.bluenet.ble.base.BleBaseEncryption;

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
	private String _adminKey;
	private String _memberKey;
	private String _guestKey;
	private byte[] _adminKeyBytes;
	private byte[] _memberKeyBytes;
	private byte[] _guestKeyBytes;

	public EncryptionKeys(@Nullable String adminKey, @Nullable String memberKey, @Nullable String guestKey) {
		_adminKey = adminKey;
		_memberKey = memberKey;
		_guestKey = guestKey;
		_adminKeyBytes = null;
		if (_adminKey != null) {
			_adminKeyBytes = adminKey.getBytes();
		}
		_memberKeyBytes = null;
		if (_memberKey != null) {
			_memberKeyBytes = memberKey.getBytes();
		}
		_guestKeyBytes = null;
		if (_guestKey != null) {
			_guestKeyBytes = guestKey.getBytes();
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
			return new byte[0];
		}
		byte [] key = keys.getAdminKey();
		if (key == null) {
			return new byte[0];
		}
		else {
			return key;
		}
	}

	public static byte[] getMemberKey(EncryptionKeys keys) {
		if (keys == null) {
			return new byte[0];
		}
		byte [] key = keys.getMemberKey();
		if (key == null) {
			return new byte[0];
		}
		else {
			return key;
		}
	}

	public static byte[] getGuestKey(EncryptionKeys keys) {
		if (keys == null) {
			return new byte[0];
		}
		byte [] key = keys.getGuestKey();
		if (key == null) {
			return new byte[0];
		}
		else {
			return key;
		}
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
