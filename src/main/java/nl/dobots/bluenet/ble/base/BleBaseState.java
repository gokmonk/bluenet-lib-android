package nl.dobots.bluenet.ble.base;

import nl.dobots.bluenet.ble.base.callbacks.IConfigurationCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStateCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.ConfigurationMsg;
import nl.dobots.bluenet.ble.base.structs.StateMsg;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
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
 * @author Dominik Egger
 */
public class BleBaseState {

	public static final String TAG = BleBaseState.class.getCanonicalName();

	private BleBase _bleBase;

	public BleBaseState(BleBase bleBase) {
		_bleBase = bleBase;
	}

	public void stopNotifications(String address, int subscriberId, final IStatusCallback callback) {
		_bleBase.unsubscribeState(address, subscriberId, callback);
	}

	private void parseSwitchState(StateMsg state, IIntegerCallback callback) {
		if (state.getType() == BluenetConfig.STATE_SWITCH_STATE) {
			if (state.getLength() != 1) {
				BleLog.LOGe(TAG, "Wrong length parameter: %s", state.getLength());
				callback.onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
			} else {
				int switchState = state.getUint8Value();
				BleLog.LOGd(TAG, "switch state: %d", switchState);
				callback.onSuccess(switchState);
			}
		}
	}

	public void getSwitchState(String address, final IIntegerCallback callback) {
		_bleBase.getState(address, BluenetConfig.STATE_SWITCH_STATE, new IStateCallback() {
			@Override
			public void onSuccess(StateMsg state) {
				parseSwitchState(state, callback);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void getSwitchStateNotifications(String address, final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		_bleBase.getStateNotifications(address, BluenetConfig.STATE_SWITCH_STATE, statusCallback,
				new IStateCallback() {
					@Override
					public void onSuccess(StateMsg state) {
						parseSwitchState(state, callback);
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

	private void parseAccumulatedEnergy(StateMsg state, IIntegerCallback callback) {
		if (state.getType() == BluenetConfig.STATE_ACCUMULATED_ENERGY) {
			if (state.getLength() != 4) {
				BleLog.LOGe(TAG, "Wrong length parameter: %s", state.getLength());
				callback.onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
			} else {
				int accumulatedEnergy = state.getIntValue();
				BleLog.LOGd(TAG, "accumulated energy: %d", accumulatedEnergy);
				callback.onSuccess(accumulatedEnergy);
			}
		}
	}

	public void getAccumulatedEnergy(String address, final IIntegerCallback callback) {
		_bleBase.getState(address, BluenetConfig.STATE_ACCUMULATED_ENERGY, new IStateCallback() {
			@Override
			public void onSuccess(StateMsg state) {
				parseAccumulatedEnergy(state, callback);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void getAccumulatedEnergyNotifications(String address, final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		_bleBase.getStateNotifications(address, BluenetConfig.STATE_ACCUMULATED_ENERGY, statusCallback,
				new IStateCallback() {
					@Override
					public void onSuccess(StateMsg state) {
						parseAccumulatedEnergy(state, callback);
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

	private void parsePowerUsage(StateMsg state, IIntegerCallback callback) {
		if (state.getType() == BluenetConfig.STATE_POWER_USAGE) {
			if (state.getLength() != 4) {
				BleLog.LOGe(TAG, "Wrong length parameter: %s", state.getLength());
				callback.onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
			} else {
				int powerUsage = state.getIntValue();
				BleLog.LOGd(TAG, "power usage: %d", powerUsage);
				callback.onSuccess(powerUsage);
			}
		}
	}

	public void getPowerUsage(String address, final IIntegerCallback callback) {
		_bleBase.getState(address, BluenetConfig.STATE_POWER_USAGE, new IStateCallback() {
			@Override
			public void onSuccess(StateMsg state) {
				parsePowerUsage(state, callback);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void getPowerUsageNotifications(String address, final IIntegerCallback statusCallback,
												  final IIntegerCallback callback) {
		_bleBase.getStateNotifications(address, BluenetConfig.STATE_POWER_USAGE, statusCallback,
				new IStateCallback() {
					@Override
					public void onSuccess(StateMsg state) {
						parsePowerUsage(state, callback);
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

	private void parseTemperature(StateMsg state, IIntegerCallback callback) {
		if (state.getType() == BluenetConfig.STATE_TEMPERATURE) {
			if (state.getLength() != 4) {
				BleLog.LOGe(TAG, "Wrong length parameter: %s", state.getLength());
				callback.onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
			} else {
				int temperature = state.getIntValue();
				BleLog.LOGd(TAG, "power usage: %d", temperature);
				callback.onSuccess(temperature);
			}
		}
	}

	public void getTemperature(String address, final IIntegerCallback callback) {
		_bleBase.getState(address, BluenetConfig.STATE_TEMPERATURE, new IStateCallback() {
			@Override
			public void onSuccess(StateMsg state) {
				parseTemperature(state, callback);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void getTemperatureNotifications(String address, final IIntegerCallback statusCallback,
										   final IIntegerCallback callback) {
		_bleBase.getStateNotifications(address, BluenetConfig.STATE_TEMPERATURE, statusCallback,
				new IStateCallback() {
					@Override
					public void onSuccess(StateMsg state) {
						parseTemperature(state, callback);
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

}
