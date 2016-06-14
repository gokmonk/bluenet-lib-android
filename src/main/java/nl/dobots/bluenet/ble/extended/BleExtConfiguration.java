package nl.dobots.bluenet.ble.extended;

import nl.dobots.bluenet.ble.base.BleBase;
import nl.dobots.bluenet.ble.base.BleBaseConfiguration;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.IBooleanCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IStringCallback;
import nl.dobots.bluenet.utils.BleLog;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 13-6-16
 *
 * Wraps the Ble calls for configuration settings into separate functions. There are
 * two set functions and two get functions for each configuration type:

 * The set functions without address parameter check if the device is connected and verify
 * that the configuration characteristic was discovered. if not the error callback is triggered.
 * The set functions with address will connect first to the device, discover the services, then
 * if execute the set configuration if the configuration characteristic was discovered, and in the
 * end disconnects automatically again.
 *
 * Same for the get functions, the ones without address only verify that we are connected and that
 * the characteristic is available, the ones with address connect and execute, then disconnect.
 *
 * @author Dominik Egger
 */
public class BleExtConfiguration {

	private static final String TAG = BleExtConfiguration.class.getCanonicalName();

	private BleExt _bleExt;
	private BleBase _bleBase;
	private BleBaseConfiguration _baseConfiguration;

	public BleExtConfiguration(BleExt bleExt) {
		_bleExt = bleExt;
		_bleBase = _bleExt.getBleBase();
		_baseConfiguration = new BleBaseConfiguration(_bleBase);
	}

	/**
	 * Write the given device name to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new device name
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setDeviceName(String value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set DeviceName to %s", value);
			_baseConfiguration.setDeviceName(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given device name to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new device name
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setDeviceName(String address, final String value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setDeviceName(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setDeviceName(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current device name from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the device name on success, or an error otherwise
	 */
	public void getDeviceName(IStringCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get DeviceName ...");
			_baseConfiguration.getDeviceName(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current device name from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the device name on success, or an error otherwise
	 */
	public void getDeviceName(String address, final IStringCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getDeviceName(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getDeviceName(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the crownstone id value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getCrownstoneId(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting CrownstoneId value ...");
			_baseConfiguration.getCrownstoneId(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the crownstone id value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getCrownstoneId(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getCrownstoneId(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getCrownstoneId(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given crownstone id to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setCrownstoneId(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set CrownstoneId to %d", value);
			_baseConfiguration.setCrownstoneId(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given crownstone id to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setCrownstoneId(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setCrownstoneId(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setCrownstoneId(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current beacon major from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMajor(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Reading BeaconMajor value ...");
			_baseConfiguration.getBeaconMajor(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current beacon major from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMajor(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getBeaconMajor(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getBeaconMajor(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given beacon major to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new beacon major
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMajor(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set BeaconMajor to %d", value);
			_baseConfiguration.setBeaconMajor(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given beacon major to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new beacon major
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMajor(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setBeaconMajor(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setBeaconMajor(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current beacon minor from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMinor(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get BeaconMinor ...");
			_baseConfiguration.getBeaconMinor(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current beacon minor from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMinor(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getBeaconMinor(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getBeaconMinor(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given beacon minor to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new beacon minor
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMinor(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set BeaconMinor to %d", value);
			_baseConfiguration.setBeaconMinor(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given beacon minor to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new beacon minor
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMinor(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setBeaconMinor(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setBeaconMinor(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current beacon proximity uuid from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconProximityUuid(IStringCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get BeaconProximityUuid ...");
			_baseConfiguration.getBeaconProximityUuid(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current beacon proximity uuid from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconProximityUuid(String address, final IStringCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getBeaconProximityUuid(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getBeaconProximityUuid(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given beacon proximity UUID to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new beacon proximity UUID
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconProximityUuid(String value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set BeaconProximityUuid to %s", value);
			_baseConfiguration.setBeaconProximityUuid(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given beacon proximity UUID to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new beacon proximity UUID
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconProximityUuid(String address, final String value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setBeaconProximityUuid(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setBeaconProximityUuid(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current beacon calibrated rssi (rssi value at 1 m) from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconCalibratedRssi(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get BeaconCalibratedRssi ...");
			_baseConfiguration.getBeaconCalibratedRssi(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current beacon calibrated rssi (rssi value at 1 m) from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconCalibratedRssi(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getBeaconCalibratedRssi(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getBeaconCalibratedRssi(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given beacon calibrated rssi to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new beacon calibrated rssi
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconCalibratedRssi(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set BeaconCalibratedRssi to %d", value);
			_baseConfiguration.setBeaconCalibratedRssi(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given beacon calibrated rssi to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new beacon calibrated rssi
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconCalibratedRssi(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setBeaconCalibratedRssi(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setBeaconCalibratedRssi(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current tx power from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getTxPower(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get TxPower ...");
			_baseConfiguration.getTxPower(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current tx power from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getTxPower(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getTxPower(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getTxPower(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given tx power to the configuration. This can be one of the following values:
	 *  -30, -20, -16, -12, -8, -4, 0, or 4
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new tx power
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setTxPower(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set TxPower to %d", value);
			_baseConfiguration.setTxPower(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given tx power to the configuration. This can be one of the following values:
	 *  -30, -20, -16, -12, -8, -4, 0, or 4
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new tx power
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setTxPower(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setTxPower(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setTxPower(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current advertisement interval from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getAdvertisementInterval(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get AdvertisementInterval ...");
			_baseConfiguration.getAdvertisementInterval(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current advertisement interval from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getAdvertisementInterval(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getAdvertisementInterval(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getAdvertisementInterval(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given advertisement interval to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new advertisement interval
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setAdvertisementInterval(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set AdvertisementInterval to %d", value);
			_baseConfiguration.setAdvertisementInterval(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given advertisement interval to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new advertisement interval
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setAdvertisementInterval(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setAdvertisementInterval(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setAdvertisementInterval(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the current limit value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getCurrentLimit(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Reading CurrentLimit value ...");
			_baseConfiguration.getCurrentLimit(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the current limit value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getCurrentLimit(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getCurrentLimit(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getCurrentLimit(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given current limit to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setCurrentLimit(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set CurrentLimit to %d", value);
			_baseConfiguration.setCurrentLimit(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given current limit to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setCurrentLimit(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setCurrentLimit(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setCurrentLimit(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the passkey value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPasskey(IStringCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Reading Passkey value ...");
			_baseConfiguration.getPasskey(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the passkey value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPasskey(String address, final IStringCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getPasskey(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getPasskey(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given passkey to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPasskey(String value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set Passkey to %s", value);
			_baseConfiguration.setPasskey(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given passkey to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPasskey(String address, final String value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setPasskey(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setPasskey(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}




	/**
	 * Function to read the nearby timeout value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getNearbyTimeout(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting NearbyTimeout value ...");
			_baseConfiguration.getNearbyTimeout(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the nearby timeout value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getNearbyTimeout(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getNearbyTimeout(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getNearbyTimeout(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given nearby timeout to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setNearbyTimeout(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set NearbyTimeout to %d", value);
			_baseConfiguration.setNearbyTimeout(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given nearby timeout to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setNearbyTimeout(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setNearbyTimeout(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setNearbyTimeout(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the scan duration value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanDuration(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting ScanDuration value ...");
			_baseConfiguration.getScanDuration(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the scan duration value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanDuration(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getScanDuration(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getScanDuration(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given scan duration to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanDuration(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set ScanDuration to %d", value);
			_baseConfiguration.setScanDuration(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given scan duration to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanDuration(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setScanDuration(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setScanDuration(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the scan delay value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanSendDelay(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting ScanSendDelay value ...");
			_baseConfiguration.getScanSendDelay(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the scan send delay value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanSendDelay(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getScanSendDelay(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getScanSendDelay(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given scan send delay to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanSendDelay(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set ScanSendDelay to %d", value);
			_baseConfiguration.setScanSendDelay(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given scan delay to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanSendDelay(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setScanSendDelay(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setScanSendDelay(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the scan break duration value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanBreakDuration(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting ScanBreakDuration value ...");
			_baseConfiguration.getScanBreakDuration(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the scan break duration value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanBreakDuration(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getScanBreakDuration(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getScanBreakDuration(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given scan break duration to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanBreakDuration(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set ScanBreakDuration to %d", value);
			_baseConfiguration.setScanBreakDuration(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given scan break duration to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanBreakDuration(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setScanBreakDuration(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setScanBreakDuration(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the scan filter value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanFilter(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting ScanFilter value ...");
			_baseConfiguration.getScanFilter(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the scan filter value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanFilter(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getScanFilter(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getScanFilter(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given scan filter to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanFilter(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set ScanFilter to %d", value);
			_baseConfiguration.setScanFilter(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given scan filter to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanFilter(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setScanFilter(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setScanFilter(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the scan filter fraction value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanFilterFraction(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting ScanFilterFraction value ...");
			_baseConfiguration.getScanFilterFraction(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the scan filter fraction value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getScanFilterFraction(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getScanFilterFraction(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getScanFilterFraction(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given scan filter fraction to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanFilterFraction(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set ScanFilterFraction to %d", value);
			_baseConfiguration.setScanFilterFraction(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given scan filter fraction to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanFilterFraction(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setScanFilterFraction(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setScanFilterFraction(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}


	/**
	 * Read the current device type from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	@Deprecated
	public void getDeviceType(IStringCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get DeviceType ...");
			_baseConfiguration.getDeviceType(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current device type from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	@Deprecated
	public void getDeviceType(String address, final IStringCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getDeviceType(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getDeviceType(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given device type to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new device type
	 * @param callback the callback which will be informed about success or failure
	 */
	@Deprecated
	public void setDeviceType(String value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set DeviceType to %s", value);
			_baseConfiguration.setDeviceType(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given device type to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new device type
	 * @param callback the callback which will be informed about success or failure
	 */
	@Deprecated
	public void setDeviceType(String address, final String value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setDeviceType(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setDeviceType(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current floor from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	@Deprecated
	public void getFloor(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get Floor ...");
			_baseConfiguration.getFloor(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current floor from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	@Deprecated
	public void getFloor(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getFloor(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getFloor(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given floor to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new floor
	 * @param callback the callback which will be informed about success or failure
	 */
	@Deprecated
	public void setFloor(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set Floor to %s", value);
			_baseConfiguration.setFloor(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given floor to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new floor
	 * @param callback the callback which will be informed about success or failure
	 */
	@Deprecated
	public void setFloor(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setFloor(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setFloor(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current room from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	@Deprecated
	public void getRoom(IStringCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get Room ...");
			_baseConfiguration.getRoom(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current room from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	@Deprecated
	public void getRoom(String address, final IStringCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getRoom(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getRoom(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given room to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new room
	 * @param callback the callback which will be informed about success or failure
	 */
	@Deprecated
	public void setRoom(String value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set Room to %s", value);
			_baseConfiguration.setRoom(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given room to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new room
	 * @param callback the callback which will be informed about success or failure
	 */
	@Deprecated
	public void setRoom(String address, final String value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setRoom(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setRoom(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given wifi value to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new wifi value
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setWifi(String value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set Wifi to %s", value);
			_baseConfiguration.setWifi(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given wifi value to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new wifi value
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setWifi(String address, final String value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setWifi(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setWifi(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current ip from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getIp(IStringCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get Ip ...");
			// todo: continue here
			_baseConfiguration.getIp(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current ip from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getIp(String address, final IStringCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getIp(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getIp(new IStringCallback() {
						@Override
						public void onSuccess(String result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current minimum environment temperature from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMinEnvTemp(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get minimum environment temperature...");
			_baseConfiguration.getMinEnvTemp(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current minimum environment temperature from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMinEnvTemp(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getMinEnvTemp(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getMinEnvTemp(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given minimum environment temperature to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new minimum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMinEnvTemp(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set minimum environment temperature to %d", value);
			_baseConfiguration.setMinEnvTemp(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given minimum environment temperature to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new minimum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMinEnvTemp(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setMinEnvTemp(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setMinEnvTemp(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Read the current maximum environment temperature from the devices configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMaxEnvTemp(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get maximum environment temperature...");
			_baseConfiguration.getMaxEnvTemp(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Read the current maximum environment temperature from the devices configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMaxEnvTemp(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getMaxEnvTemp(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getMaxEnvTemp(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Write the given maximum environment temperature to the configuration
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the new maximum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMaxEnvTemp(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set maximum environment temperature to %d", value);
			_baseConfiguration.setMaxEnvTemp(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Write the given maximum environment temperature to the configuration
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param value the new maximum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMaxEnvTemp(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(address)) {
			setMaxEnvTemp(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setMaxEnvTemp(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { /* don't care */ }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the boot delay value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getBootDelay(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting BootDelay value ...");
			_baseConfiguration.getBootDelay(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the boot delay value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getBootDelay(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getBootDelay(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getBootDelay(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given boot delay to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBootDelay(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set BootDelay to %d", value);
			_baseConfiguration.setBootDelay(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given boot delay to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBootDelay(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setBootDelay(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setBootDelay(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the max chip temperature value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getMaxChipTemperature(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting MaxChipTemperature value ...");
			_baseConfiguration.getMaxChipTemperature(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the max chip temperature value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getMaxChipTemperature(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getMaxChipTemperature(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getMaxChipTemperature(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given max chip temperature to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMaxChipTemperature(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set MaxChipTemperature to %d", value);
			_baseConfiguration.setMaxChipTemperature(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given max chip temperature to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMaxChipTemperature(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setMaxChipTemperature(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setMaxChipTemperature(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the adc sample rate value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getAdcSampleRate(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting AdcSampleRate value ...");
			_baseConfiguration.getAdcSampleRate(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the adc sample rate value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getAdcSampleRate(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getAdcSampleRate(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getAdcSampleRate(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given adc sample rate to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setAdcSampleRate(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set AdcSampleRate to %d", value);
			_baseConfiguration.setAdcSampleRate(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given adc sample rate to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setAdcSampleRate(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setAdcSampleRate(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setAdcSampleRate(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the power sample burst interval value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPowerSampleBurstInterval(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting PowerSampleBurstInterval value ...");
			_baseConfiguration.getPowerSampleBurstInterval(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the power sample burst interval value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPowerSampleBurstInterval(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getPowerSampleBurstInterval(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getPowerSampleBurstInterval(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given power sample burst interval to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPowerSampleBurstInterval(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set PowerSampleBurstInterval to %d", value);
			_baseConfiguration.setPowerSampleBurstInterval(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given power sample burst interval to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPowerSampleBurstInterval(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setPowerSampleBurstInterval(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setPowerSampleBurstInterval(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the power sample continuous interval value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPowerSampleContInterval(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting PowerSampleContInterval value ...");
			_baseConfiguration.getPowerSampleContInterval(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the power sample continuous interval value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPowerSampleContInterval(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getPowerSampleContInterval(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getPowerSampleContInterval(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given power sample continuous interval to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPowerSampleContInterval(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set PowerSampleContInterval to %d", value);
			_baseConfiguration.setPowerSampleContInterval(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given power sample continuous interval to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPowerSampleContInterval(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setPowerSampleContInterval(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setPowerSampleContInterval(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the power sample continuous number of samples value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPowerSampleContNumSamples(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting PowerSampleContNumSamples value ...");
			_baseConfiguration.getPowerSampleContNumSamples(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the power sample continous number of samples value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPowerSampleContNumSamples(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getPowerSampleContNumSamples(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getPowerSampleContNumSamples(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given power sample continuous number of samples to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPowerSampleContNumSamples(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set PowerSampleContNumSamples to %d", value);
			_baseConfiguration.setPowerSampleContNumSamples(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given power sample continuous number of samples to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPowerSampleContNumSamples(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setPowerSampleContNumSamples(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setPowerSampleContNumSamples(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to read the pwm frequency value from the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPwmFrequency(IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting PwmFrequency value ...");
			_baseConfiguration.getPwmFrequency(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the pwm frequency value from the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void getPwmFrequency(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getPwmFrequency(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getPwmFrequency(new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Function to write the given pwm frequency to the device.
	 *
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 * @param value the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPwmFrequency(int value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set PwmFrequency to %d", value);
			_baseConfiguration.setPwmFrequency(_bleExt.getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to write the given pwm frequency to the device.
	 *
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 * @param address the MAC address of the device
	 * @param value the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPwmFrequency(String address, final int value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setPwmFrequency(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setPwmFrequency(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////
	// read only
	////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Check if mesh is enabled
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMeshEnabled(IBooleanCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting MeshEnabled value ...");
			_baseConfiguration.getMeshEnabled(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Check if mesh is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMeshEnabled(String address, final IBooleanCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getMeshEnabled(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getMeshEnabled(new IBooleanCallback() {
						@Override
						public void onSuccess(boolean result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Check if encryption is enabled
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getEncryptionEnabled(IBooleanCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting EncryptionEnabled value ...");
			_baseConfiguration.getEncryptionEnabled(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Check if encryption is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getEncryptionEnabled(String address, final IBooleanCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getEncryptionEnabled(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getEncryptionEnabled(new IBooleanCallback() {
						@Override
						public void onSuccess(boolean result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Check if iBeacon is enabled
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getIBeaconEnabled(IBooleanCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting IBeaconEnabled value ...");
			_baseConfiguration.getIBeaconEnabled(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Check if iBeacon is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getIBeaconEnabled(String address, final IBooleanCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getIBeaconEnabled(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getIBeaconEnabled(new IBooleanCallback() {
						@Override
						public void onSuccess(boolean result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Check if scanner is enabled
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getScannerEnabled(IBooleanCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting ScannerEnabled value ...");
			_baseConfiguration.getScannerEnabled(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Check if scanner is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getScannerEnabled(String address, final IBooleanCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getScannerEnabled(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getScannerEnabled(new IBooleanCallback() {
						@Override
						public void onSuccess(boolean result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Check if continuous power sampler is enabled
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getContPowerSamplerEnabled(IBooleanCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting ContPowerSamplerEnabled value ...");
			_baseConfiguration.getContPowerSamplerEnabled(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Check if continuous power sampler is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getContPowerSamplerEnabled(String address, final IBooleanCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getContPowerSamplerEnabled(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getContPowerSamplerEnabled(new IBooleanCallback() {
						@Override
						public void onSuccess(boolean result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() {
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/**
	 * Check if tracker is enabled
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getTrackerEnabled(IBooleanCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting TrackerEnabled value ...");
			_baseConfiguration.getTrackerEnabled(_bleExt.getTargetAddress(), callback);
		}
	}

	/**
	 * Check if tracker is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getTrackerEnabled(String address, final IBooleanCallback callback) {
		if (_bleExt.checkConnection(null)) {
			getTrackerEnabled(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getTrackerEnabled(new IBooleanCallback() {
						@Override
						public void onSuccess(boolean result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	/////////////////////////////////////////////////////////////////////////////////////

/*
	public void getXXX(ICallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "geting XXX value ...");
			_baseConfiguration.getXXX(_bleExt.getTargetAddress(), callback);
		}
	}

	public void getXXX(String address, final ICallback callback) {
		if (_bleExt.checkConnection(null)) {
			getXXX(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getXXX(new ICallback() {
						@Override
						public void onSuccess(zzz result) {
							callback.onSuccess(result);
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

	public void setXXX(zzz value, IStatusCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasConfigurationCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Set XXX to %uuu", value);
			_baseConfiguration.setXXX(_bleExt.getTargetAddress(), value, callback);
		}
	}

	public void setXXX(String address, final zzz value, final IStatusCallback callback) {
		if (_bleExt.checkConnection(null)) {
			setXXX(value, callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					setXXX(value, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
							execCallback.onSuccess();
						}

						@Override
						public void onError(int error) {
							execCallback.onError(error);
						}
					});
				}
			}, new IStatusCallback() {
				@Override
				public void onSuccess() { }

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
		}
	}

*/


}
