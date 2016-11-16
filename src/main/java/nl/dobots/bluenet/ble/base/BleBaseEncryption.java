package nl.dobots.bluenet.ble.base;

import android.util.Log;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.base.structs.EncryptionSessionData;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
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
 * Created on 28-9-16
 *
 * @author Bart van Vliet
 */
public class BleBaseEncryption {
	private static final String TAG = BleBaseEncryption.class.getCanonicalName();

	public static final char ACCESS_LEVEL_ADMIN =               0;
	public static final char ACCESS_LEVEL_MEMBER =              1;
	public static final char ACCESS_LEVEL_GUEST =               2;
	public static final char ACCESS_LEVEL_SETUP =               100;
	public static final char ACCESS_LEVEL_NOT_SET =             201;
	@Deprecated
	public static final char ACCESS_LEVEL_HIGHEST_AVAILABLE =   202;
	public static final char ACCESS_LEVEL_ENCRYPTION_DISABLED = 255;


	private static final int AES_BLOCK_SIZE = 16;
	private static final int VALIDATION_KEY_LENGTH = 4;
	private static final int SESSION_NONCE_LENGTH = 5;
	private static final int PACKET_NONCE_LENGTH = 3;
	private static final int ACCESS_LEVEL_LENGTH = 1;

//	private Cipher _cipher = null;
//	private boolean _cipherInitialized = false;

	public BleBaseEncryption() {
//		try {
//			_cipher = Cipher.getInstance("AES/CTR/NoPadding");
//		} catch (GeneralSecurityException e) {
//			BleLog.LOGe(TAG, "Encryption not available");
//			e.printStackTrace();
//		}
	}

//	public boolean setKey(String keyStr) {
//		Key key = new SecretKeySpec(keyStr.getBytes(Charset.forName("UTF-8")), "AES");
//		try {
//			_cipher.init(Cipher.ENCRYPT_MODE, key);
//			_cipherInitialized = true;
//			return true;
//		} catch (InvalidKeyException e) {
//			BleLog.LOGe(TAG, "Encryption not available");
//			e.printStackTrace();
//		}
//		return false;
//	}

	public static byte[] encryptCtr(byte[] payloadData, byte[] sessionNonce, byte[] validationKey, byte[] key, char accessLevel) {
		if (payloadData == null || payloadData.length < 1) {
			Log.w(TAG, "wrong data length");
			return null;
		}
		if (sessionNonce == null || sessionNonce.length != SESSION_NONCE_LENGTH) {
			Log.w(TAG, "wrong session nonce length");
			return null;
		}
		if (validationKey == null || validationKey.length != VALIDATION_KEY_LENGTH) {
			Log.w(TAG, "wrong validation key length");
			return null;
		}
		if (key == null || key.length != AES_BLOCK_SIZE) {
			Log.w(TAG, "wrong key length");
			return null;
		}
		BleLog.LOGd(TAG, "payloadData: " + BleUtils.bytesToString(payloadData));

		// Packet nonce is randomly generated.
		SecureRandom random = new SecureRandom();
		byte[] packetNonce = new byte[PACKET_NONCE_LENGTH];
		random.nextBytes(packetNonce);
		BleLog.LOGd(TAG, "packetNonce: " + BleUtils.bytesToString(packetNonce));

		// Create iv by concatting session nonce and packet nonce.
		byte[] iv = new byte[AES_BLOCK_SIZE];
		System.arraycopy(packetNonce, 0, iv, 0, PACKET_NONCE_LENGTH);
		System.arraycopy(sessionNonce, 0, iv, PACKET_NONCE_LENGTH, SESSION_NONCE_LENGTH);

		// Allocate to-be-encrypted payload array, fill with payloadData prefixed with validation key.
		int payloadLen = VALIDATION_KEY_LENGTH + (payloadData.length);
		int paddingLen = ((AES_BLOCK_SIZE - (payloadLen % AES_BLOCK_SIZE)) % AES_BLOCK_SIZE);
		byte[] payload = new byte[payloadLen+paddingLen];
		//Arrays.fill(payload, (byte)0); // Already zeroes by default
		System.arraycopy(validationKey, 0, payload, 0, VALIDATION_KEY_LENGTH);
		System.arraycopy(payloadData, 0, payload, VALIDATION_KEY_LENGTH, payloadData.length);
		BleLog.LOGd(TAG, "payload: " + BleUtils.bytesToString(payload));

		// Allocate output array
		byte[] encryptedData = new byte[PACKET_NONCE_LENGTH + ACCESS_LEVEL_LENGTH + payloadLen + paddingLen];
		System.arraycopy(packetNonce, 0, encryptedData, 0, PACKET_NONCE_LENGTH);
		encryptedData[PACKET_NONCE_LENGTH] = (byte)accessLevel;

		// Encrypt payload
		try {
			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
			BleLog.LOGd(TAG, "IV before: " + BleUtils.bytesToString(cipher.getIV()));
			// doFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
			cipher.doFinal(payload, 0, payload.length, encryptedData, PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH);
			BleLog.LOGd(TAG, "IV after: " + BleUtils.bytesToString(cipher.getIV()));
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			return null;
		}
//		try {
//			System.arraycopy(payload, 0, encryptedData, PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH, payload.length);
//			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
//			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
//			for (int ctr=0; ctr<payload.length/16; ctr++) {
//				iv[AES_BLOCK_SIZE-1] = (byte)ctr;
//				BleLog.LOGd(TAG, "IV: " + BleUtils.bytesToString(iv));
//				byte[] encryptedIv = new byte[AES_BLOCK_SIZE];
//				cipher.doFinal(iv, 0, iv.length, encryptedIv, 0);
//				for (int i=0; i<AES_BLOCK_SIZE; i++) {
//					encryptedData[i+PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH] = (byte)(0xff & ((int)encryptedData[i+PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH]) ^ ((int)encryptedIv[i]));
//				}
//			}
//		} catch (GeneralSecurityException e) {
//			e.printStackTrace();
//			return null;
//		}
		BleLog.LOGd(TAG, "encryptedData: " + BleUtils.bytesToString(encryptedData));
		return encryptedData;
	}

	public static byte[] decryptCtr(byte[] encryptedData, byte[] sessionNonce, byte[] validationKey, EncryptionKeys keys) {
		if (encryptedData == null || encryptedData.length < PACKET_NONCE_LENGTH + ACCESS_LEVEL_LENGTH + AES_BLOCK_SIZE) {
			Log.w(TAG, "wrong data length");
			return null;
		}
		if (sessionNonce == null || sessionNonce.length != SESSION_NONCE_LENGTH) {
			Log.w(TAG, "wrong session nonce length");
			return null;
		}
		if (validationKey == null || validationKey.length != VALIDATION_KEY_LENGTH) {
			Log.w(TAG, "wrong validation key length");
			return null;
		}
		if (keys == null) {
			Log.w(TAG, "no keys supplied");
			return null;
		}
		BleLog.LOGd(TAG, "encryptedData: " + BleUtils.bytesToString(encryptedData));

		byte[] decryptedData = new byte[encryptedData.length - PACKET_NONCE_LENGTH - ACCESS_LEVEL_LENGTH];
		if (decryptedData.length % AES_BLOCK_SIZE != 0) {
			Log.w(TAG, "encrypted data length must be multiple of 16");
			return null;
		}

		char accessLevel = (char)BleUtils.toUint8(encryptedData[PACKET_NONCE_LENGTH]);
		BleLog.LOGd(TAG, "accessLevel: " + (int)accessLevel);
		byte[] key = keys.getKey(accessLevel);
		if (key == null || key.length != AES_BLOCK_SIZE) {
			Log.w(TAG, "wrong key length: " + key);
			return null;
		}

		// Create iv by concatting session nonce and packet nonce
		byte[] iv = new byte[AES_BLOCK_SIZE];
		System.arraycopy(encryptedData, 0, iv, 0, PACKET_NONCE_LENGTH);
		System.arraycopy(sessionNonce, 0, iv, PACKET_NONCE_LENGTH, SESSION_NONCE_LENGTH);

		// Decrypt encrypted payload
		try {
			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
			BleLog.LOGd(TAG, "IV: " + Arrays.toString(cipher.getIV()));
			// doFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
			cipher.doFinal(encryptedData, PACKET_NONCE_LENGTH+ACCESS_LEVEL_LENGTH, decryptedData.length, decryptedData, 0);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			return null;
		}

		// Check validation key
		for (int i=0; i<VALIDATION_KEY_LENGTH; i++) {
			if (decryptedData[i] != validationKey[i]) {
				Log.w(TAG, "incorrect validation key");
				return null;
			}
		}

		byte[] payloadData = new byte[decryptedData.length-VALIDATION_KEY_LENGTH];
		System.arraycopy(decryptedData, VALIDATION_KEY_LENGTH, payloadData, 0, payloadData.length);
		return payloadData;
	}

	public static byte[] decryptEcb(byte[] payloadData, byte[] key) {
		return decryptEcb(payloadData, 0 , key);
	}

	public static byte[] decryptEcb(byte[] payloadData, int inputOffset, byte[] key) {
		if (inputOffset < 0) {
			return null;
		}
		if (payloadData == null || (payloadData.length-inputOffset) < AES_BLOCK_SIZE) {
			Log.w(TAG, "payload data too short");
			return null;
		}
		if ((payloadData.length-inputOffset) % 16 != 0) {
			Log.w(TAG, "wrong payload data length");
			return null;
		}
		if (key == null || key.length != AES_BLOCK_SIZE) {
			Log.w(TAG, "wrong key length");
			return null;
		}

		byte[] decryptedData = new byte[payloadData.length-inputOffset];
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
			cipher.doFinal(payloadData, inputOffset, payloadData.length-inputOffset, decryptedData, 0);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
			return null;
		}
		return decryptedData;
	}

	public static EncryptionSessionData getSessionData(byte[] decryptedData) {
		return getSessionData(decryptedData, true);
	}

	public static EncryptionSessionData getSessionData(byte[] decryptedData, boolean wasEncrypted) {
		if (decryptedData == null) {
			return null;
		}
		// When the data was encrypted, the first 4 bytes should be CAFEBABE, to check if encryption succeeded.
		if (wasEncrypted) {
			if (decryptedData.length < VALIDATION_KEY_LENGTH + SESSION_NONCE_LENGTH) {
				return null;
			}
			// Bytes 0-3 (validation key) should be CAFEBABE
			if (BleUtils.byteArrayToInt(decryptedData) != BluenetConfig.CAFEBABE) {
				return null;
			}
			return _getSessionData(decryptedData, VALIDATION_KEY_LENGTH);
		}
		else {
			if (decryptedData.length < SESSION_NONCE_LENGTH) {
				return null;
			}
			return _getSessionData(decryptedData, 0);
		}
	}

	private static EncryptionSessionData _getSessionData(byte[] data, int offset) {
		byte[] sessionNonce = new byte[SESSION_NONCE_LENGTH];
		byte[] validationKey = new byte[VALIDATION_KEY_LENGTH];

		// Copy bytes 4-8 to sessionNonce, copy bytes 4-7 to validationKey
		System.arraycopy(data, offset, sessionNonce, 0, SESSION_NONCE_LENGTH);
		System.arraycopy(data, offset, validationKey, 0, VALIDATION_KEY_LENGTH);

		EncryptionSessionData sessionData = new EncryptionSessionData();
		sessionData.sessionNonce = sessionNonce;
		sessionData.validationKey = validationKey;

		return sessionData;
	}
}
