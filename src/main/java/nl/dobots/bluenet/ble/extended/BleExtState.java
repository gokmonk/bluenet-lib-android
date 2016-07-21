package nl.dobots.bluenet.ble.extended;

import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;

import nl.dobots.bluenet.ble.base.BleBase;
import nl.dobots.bluenet.ble.base.BleBaseState;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;
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
public class BleExtState {

	public static final String TAG = BleExtState.class.getCanonicalName();

	private BleExt _bleExt;
	private BleBase _bleBase;

	private BleBaseState _bleBaseState;

	private static int globalSubscriberId = 0;
	private static HashMap<Character, SubscriberCallback> _subscriberList = new HashMap<>();

	public BleExtState(BleExt bleExt) {
		_bleExt = bleExt;
		_bleBase = _bleExt.getBleBase();
		_bleBaseState = new BleBaseState(_bleBase);
	}

	public void getSwitchState(final IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get switch state ...");
			_bleBaseState.getSwitchState(_bleExt.getTargetAddress(), callback);
		}
	}

	public void getSwitchState(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getSwitchState(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getSwitchState(new IIntegerCallback() {
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

	public void getAccumulatedEnergy(final IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get accumulated energy ...");
			_bleBaseState.getAccumulatedEnergy(_bleExt.getTargetAddress(), callback);
		}
	}

	public void getAccumulatedEnergy(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getAccumulatedEnergy(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getAccumulatedEnergy(new IIntegerCallback() {
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

	public void getPowerUsage(final IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
			BleLog.LOGd(TAG, "Get power usage ...");
			_bleBaseState.getPowerUsage(_bleExt.getTargetAddress(), callback);
		}
	}

	public void getPowerUsage(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getPowerUsage(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IStatusCallback execCallback) {
					getPowerUsage(new IIntegerCallback() {
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

	public void getTemperature(final IIntegerCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
					BleLog.LOGd(TAG, "Get temperature ...");
					_bleBaseState.getTemperature(_bleExt.getTargetAddress(), callback);
				}
			}
		});
	}

	public void getTemperature(final String address, final IIntegerCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				BleLog.LOGv(TAG, "Get temperature");
//				if (_bleExt.checkConnection(address)) {
//					getTemperature(callback);
//				} else {
					_bleExt.connectAndExecute(address, new IExecuteCallback() {
						@Override
						public void execute(final IStatusCallback execCallback) {
							getTemperature(new IIntegerCallback() {
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
//				}
			}
		});

	}

	////////////////////////////////////////////////////////////////////////////////////////////////
	// Notifications
	////////////////////////////////////////////////////////////////////////////////////////////////


	class SubscriberCallback implements IIntegerCallback {

		ArrayList<Pair<Integer,IIntegerCallback>> _subscribers = new ArrayList<>();

		int _subscriberId;

		public int addSubscriber(IIntegerCallback callback) {
			int id = globalSubscriberId++;
			_subscribers.add(new Pair<>(id, callback));
			return id;
		}

		public boolean removeSubscriber(int id) {
			for (Pair<Integer, IIntegerCallback> p : _subscribers) {
				if (p.first == id) {
					_subscribers.remove(p);
					return true;
				}
			}
			return false;
		}

		@Override
		public void onSuccess(int result) {
			for (Pair<Integer, IIntegerCallback> p : _subscribers) {
				p.second.onSuccess(result);
			}
		}

		@Override
		public void onError(int error) {
			for (Pair<Integer, IIntegerCallback> p : _subscribers) {
				p.second.onError(error);
			}
		}

		public void setSubscriberId(int result) {
			_subscriberId = result;
		}
	}

	private SubscriberCallback check(char type, IIntegerCallback statusCallback, IIntegerCallback callback) {
		if (_bleExt.isConnected(statusCallback) && _bleExt.hasStateCharacteristics(statusCallback)) {
			if (!_subscriberList.containsKey(type)) {
				final SubscriberCallback cb = new SubscriberCallback();
				_subscriberList.put(type, cb);
				return cb;
			} else {
				final SubscriberCallback cb = _subscriberList.get(type);
				statusCallback.onSuccess(cb.addSubscriber(callback));
				return cb;
			}
		}
		return null;
	}

	public boolean stopNotifications(int subscriberId) {
		for (SubscriberCallback subscriberCallback : _subscriberList.values()) {
			if (subscriberCallback.removeSubscriber(subscriberId)) {
				return true;
			}
		}
		return false;
	}

	public void getSwitchStateNotifications(final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		final SubscriberCallback cb = check(BluenetConfig.STATE_SWITCH_STATE, statusCallback, callback);
		if (cb != null) {
			_bleBaseState.getSwitchStateNotifications(_bleExt.getTargetAddress(),
					new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							cb.setSubscriberId(result);
							statusCallback.onSuccess(cb.addSubscriber(callback));
						}

						@Override
						public void onError(int error) {
							_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
						}
					}, cb);
		}
	}

	public void getAccumulatedEnergyNotifications(final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		final SubscriberCallback cb = check(BluenetConfig.STATE_ACCUMULATED_ENERGY, statusCallback, callback);
		if (cb != null) {
			_bleBaseState.getAccumulatedEnergyNotifications(_bleExt.getTargetAddress(),
					new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							cb.setSubscriberId(result);
							statusCallback.onSuccess(cb.addSubscriber(callback));
						}

						@Override
						public void onError(int error) {
							_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
						}
					}, cb);
		}
	}

	public void getPowerUsageNotifications(final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		final SubscriberCallback cb = check(BluenetConfig.STATE_ACCUMULATED_ENERGY, statusCallback, callback);
		if (cb != null) {
			_bleBaseState.getPowerUsageNotifications(_bleExt.getTargetAddress(),
					new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							cb.setSubscriberId(result);
							statusCallback.onSuccess(cb.addSubscriber(callback));
						}

						@Override
						public void onError(int error) {
							_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
						}
					}, cb);
		}
	}

	public void getTemperatureNotifications(final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		final SubscriberCallback cb = check(BluenetConfig.STATE_TEMPERATURE, statusCallback, callback);
		if (cb != null) {
			_bleBaseState.getTemperatureNotifications(_bleExt.getTargetAddress(),
					new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							cb.setSubscriberId(result);
							statusCallback.onSuccess(cb.addSubscriber(callback));
						}

						@Override
						public void onError(int error) {
							_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
						}
					}, cb);
		}
	}

//
//	public void getSwitchStateNotifications(final IIntegerCallback statusCallback,
//											final IIntegerCallback callback) {
//		if (_bleExt.isConnected(statusCallback) && _bleExt.hasStateCharacteristics(statusCallback)) {
//			if (!_subscriberList.containsKey(BluenetConfig.STATE_SWITCH_STATE)) {
//				final SubscriberCallback cb = new SubscriberCallback();
//				_subscriberList.put(BluenetConfig.STATE_SWITCH_STATE, cb);
//
//				_bleBaseState.getSwitchStateNotifications(_bleExt.getTargetAddress(),
//				new IIntegerCallback() {
//					@Override
//					public void onSuccess(int result) {
//						cb.setSubscriberId(result);
//						statusCallback.onSuccess(cb.addSubscriber(callback));
//					}
//
//					@Override
//					public void onError(int error) {
//						_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
//					}
//				}, cb);
//			} else {
//				final SubscriberCallback cb = _subscriberList.get(BluenetConfig.STATE_SWITCH_STATE);
//				statusCallback.onSuccess(cb.addSubscriber(callback));
//			}
//		}
//	}

}
