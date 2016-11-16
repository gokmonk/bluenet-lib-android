package nl.dobots.bluenet.ble.extended;

import nl.dobots.bluenet.ble.base.BleBase;
import nl.dobots.bluenet.ble.base.BleBaseConfiguration;
import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IProgressCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.CommandMsg;
import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.base.structs.SetupEncryptionKey;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IStringCallback;
import nl.dobots.bluenet.utils.BleLog;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 15-11-16
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class CrownstoneSetup {

	private static final String TAG = CrownstoneSetup.class.getCanonicalName();

	private BleBase _bleBase;
	private BleExt _bleExt;

	private String _targetAddress;
	private BleBaseConfiguration _bleBaseConfiguration;

	private int _currentStep;

	private int _crownstoneId;
	private String _adminKey;
	private String _memberKey;
	private String _guestKey;
	private int _meshAccessAddress;
	private String _iBeaconUuid;
	private int _iBeaconMajor;
	private int _iBeaconMinor;
	private IProgressCallback _progressCallback;
	private IStatusCallback _statusCallback;

	public CrownstoneSetup(BleExt bleExt) {
		_bleExt = bleExt;
		_bleBase = bleExt.getBleBase();
		_bleBaseConfiguration = new BleBaseConfiguration(_bleBase);
	}

	IStatusCallback _defaultCallback =  new IStatusCallback() {
		@Override
		public void onSuccess() {
			_progressCallback.onProgress(_currentStep, null);
			setupStep(_currentStep+1);
		}

		@Override
		public void onError(int error) {
			setupError(error);
		}
	};

	private void setupError(int error) {
		BleLog.LOGe(TAG, "setup failed at step %d with error %d", _currentStep, error);
		_progressCallback.onProgress(0, null);
		_statusCallback.onError(BleErrors.ERROR_SETUP_FAILED);
	}

	private void setupStep(final int step) {
		_currentStep = step;
		switch(step) {
			case 0:
			case 1: {
				// hack, make sure step is set to 1 in case function was called with step 0
				_currentStep = 1;
				_bleBase.enableEncryption(false);
				_bleBase.readSessionKey(_targetAddress, new IByteArrayCallback() {
					@Override
					public void onSuccess(byte[] result) {
						_progressCallback.onProgress(1, null);
						SetupEncryptionKey encryptionKey = new SetupEncryptionKey(result);
						_bleBase.setEncryptionKeys(encryptionKey);
						_bleBase.enableEncryption(true);

//						setupStep(step+1);
						// go directly to step 3
						setupStep(3);
					}

					@Override
					public void onError(int error) {
						setupError(error);
					}
				});
				break;
			}
			case 2: {
				// nothing to do here, session nonce was already read out on connect
			}
			case 3: {
				// nothing to do here, set session nonce and enable encryption are handled in callbacks
			}
			case 4: {
				// hack, make sure step is set to 4 in case function was called with step 2 or 3
				_currentStep = 4;
				_bleBaseConfiguration.setCrownstoneId(_targetAddress, _crownstoneId, _defaultCallback);
				break;
			}
			case 5: {
				_bleBaseConfiguration.setAdminKey(_targetAddress, _adminKey, _defaultCallback);
				break;
			}
			case 6: {
				_bleBaseConfiguration.setMemberKey(_targetAddress, _memberKey, _defaultCallback);
				break;
			}
			case 7: {
				_bleBaseConfiguration.setGuestKey(_targetAddress, _guestKey, _defaultCallback);
				break;
			}
			case 8: {
				_bleBaseConfiguration.setMeshAccessAddress(_targetAddress, _meshAccessAddress, _defaultCallback);
				break;
			}
			case 9: {
				_bleBaseConfiguration.setBeaconProximityUuid(_targetAddress, _iBeaconUuid, _defaultCallback);
				break;
			}
			case 10: {
				_bleBaseConfiguration.setBeaconMajor(_targetAddress, _iBeaconMajor, _defaultCallback);
				break;
			}
			case 11: {
				_bleBaseConfiguration.setBeaconMinor(_targetAddress, _iBeaconMinor, _defaultCallback);
				break;
			}
			case 12: {
				finalizeSetup(_defaultCallback);
				break;
			}
			case 13: {
				_bleExt.disconnectAndClose(true, _defaultCallback);
				break;
			}
			case 14: {
				_bleBase.setEncryptionKeys(new EncryptionKeys(_adminKey, _memberKey, _guestKey));
				_statusCallback.onSuccess();
				break;
			}
			default: {
				BleLog.LOGe(TAG, "wrong setup step!");
				_statusCallback.onError(BleErrors.ERROR_WRONG_STATE);
			}
		}
	}

	private void finalizeSetup(IStatusCallback callback) {
		_bleBase.sendCommand(_targetAddress, new CommandMsg(BluenetConfig.CMD_VALIDATE_SETUP), callback);
	}

	public void executeSetup(int crownstoneId, String adminKey, String memberKey, String guestKey,
							 int meshAccessAddress, String iBeaconUuid, int iBeaconMajor,
							 int iBeaconMinor, IProgressCallback progressCallback, IStatusCallback statusCallback) {

		if (_bleExt.isConnected(statusCallback)) {
			BleLog.LOGd(TAG, "executeSetup");

			_targetAddress = _bleExt.getTargetAddress();
			_crownstoneId = crownstoneId;
			_adminKey = adminKey;
			_memberKey = memberKey;
			_guestKey = guestKey;
			_meshAccessAddress = meshAccessAddress;
			_iBeaconUuid = iBeaconUuid;
			_iBeaconMajor = iBeaconMajor;
			_iBeaconMinor = iBeaconMinor;
			_progressCallback = progressCallback;
			_statusCallback = statusCallback;

			setupStep(1);
		}

	}

	public void executeSetup(final String address, final int crownstoneId, final String adminKey, final String memberKey,
							 final String guestKey, final int meshAccessAddress, final String iBeaconUuid, final int iBeaconMajor,
							 final int iBeaconMinor, final IProgressCallback progressCallback, final IStatusCallback statusCallback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				BleLog.LOGd(TAG, "connect and executeSetup");
				_bleExt.connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IStatusCallback execCallback) {
						executeSetup(crownstoneId, adminKey, memberKey, guestKey, meshAccessAddress, iBeaconUuid,
								iBeaconMajor, iBeaconMinor, progressCallback, statusCallback);
					}
				}, new IStatusCallback() {
					@Override
					public void onSuccess() { /* don't care */ }

					@Override
					public void onError(int error) {
						statusCallback.onError(error);
					}
				});
			}
		});
	}


}
