package nl.dobots.bluenet.ble.base.structs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

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
 * Created on 6-7-17
 *
 * @author Bart van Vliet
 */
public class ScheduleEntryPacket {
	public static final int SCHEDULE_ENTRY_SIZE = 1+1+1+4+2+3;

	public static final int SCHEDULE_REPEAT_MINUTES = 0;
	public static final int SCHEDULE_REPEAT_DAY     = 1;
	public static final int SCHEDULE_REPEAT_ONCE    = 2;

	public static final int SCHEDULE_ACTION_SWITCH = 0;
	public static final int SCHEDULE_ACTION_FADE   = 1;
	public static final int SCHEDULE_ACTION_TOGGLE = 2;

	public int _id;
	public byte _overrideMask;
	public int _repeatType;
	public int _actionType;
	public long _timestamp;

	// Repeat
	public int _minutes;
	public byte _dayOfWeekMask;
	public byte _dayOfWeekNext;

	// Action
	public int _switchVal;
	public int _fadeDuration;

	public ScheduleEntryPacket() {
	}

	public ScheduleEntryPacket(int id, byte overrideMask, int repeatType, int actionType, long timestamp) {
		_id = id;
		_overrideMask = overrideMask;
		_repeatType = repeatType;
		_actionType = actionType;
		_timestamp = timestamp;
	}

	public boolean fromArray(byte[] bytes) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		if (bytes.length < SCHEDULE_ENTRY_SIZE) {
			return false;
		}
		_id = BleUtils.toUint8(bb.get());
		_overrideMask = bb.get();
		byte type = bb.get();
		_repeatType = type & 0x0F;
		_actionType = type & 0xF0;
		_timestamp = BleUtils.toUint32(bb.getInt());

		switch (_repeatType) {
			case SCHEDULE_REPEAT_MINUTES:{
				_minutes = BleUtils.toUint16(bb.getShort());
				break;
			}
			case SCHEDULE_REPEAT_DAY: {
				_dayOfWeekMask = bb.get();
				_dayOfWeekNext = bb.get();
				if (_dayOfWeekNext < 0 || _dayOfWeekNext > 6) {
					return false;
				}
				break;
			}
			case SCHEDULE_REPEAT_ONCE: {
				bb.getShort(); // Not used
				break;
			}
			default:
				return false;
		}

		switch (_actionType) {
			case SCHEDULE_ACTION_SWITCH: {
				_switchVal = BleUtils.toUint8(bb.get());
				bb.getShort(); // Not used
				break;
			}
			case SCHEDULE_ACTION_FADE: {
				_switchVal = BleUtils.toUint8(bb.get());
				_fadeDuration = BleUtils.toUint16(bb.getShort());
				break;
			}
			case SCHEDULE_ACTION_TOGGLE: {
				bb.get(); // Not used
				bb.getShort(); // Not used
				break;
			}
			default:
				return false;
		}
		return true;
	}

	public byte[] toArray() {
		ByteBuffer bb = ByteBuffer.allocate(SCHEDULE_ENTRY_SIZE);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.put((byte)_id);
		bb.put(_overrideMask);
		int type = _repeatType + (_actionType << 4);
		bb.put((byte)type);
		bb.put(BleUtils.uint32ToByteArray(_timestamp));
		switch (_repeatType) {
			case SCHEDULE_REPEAT_MINUTES:{
				bb.putShort((short)_minutes);
				break;
			}
			case SCHEDULE_REPEAT_DAY: {
				bb.put(_dayOfWeekMask);
				bb.put(_dayOfWeekNext);
				break;
			}
			case SCHEDULE_REPEAT_ONCE:
			default: {
				bb.putShort((short)0); // Not used
				break;
			}
		}
		switch (_actionType) {
			case SCHEDULE_ACTION_SWITCH: {
				bb.put((byte)_switchVal);
				bb.putShort((short)0); // Not used
				break;
			}
			case SCHEDULE_ACTION_FADE: {
				bb.put((byte)_switchVal);
				bb.putShort((short)_fadeDuration);
				break;
			}
			case SCHEDULE_ACTION_TOGGLE:
			default: {
				bb.put((byte)0); // Not used
				bb.putShort((short)0); // Not used
				break;
			}
		}
		return bb.array();
	}

	public String toString() {

		String repeatStr = "";
		switch (_repeatType) {
			case SCHEDULE_REPEAT_MINUTES:{
				repeatStr += "every " + _minutes + " minutes";
				break;
			}
			case SCHEDULE_REPEAT_DAY: {
				int daysofWeekMask = BleUtils.toUint8(_dayOfWeekMask);
				repeatStr += "weekdays mask: " + Integer.toBinaryString(daysofWeekMask) + " next day: " + _dayOfWeekNext;
				break;
			}
			case SCHEDULE_REPEAT_ONCE:
			default: {
				repeatStr += "no repeats";
				break;
			}
		}
		String actionStr = "";
		switch (_actionType) {
			case SCHEDULE_ACTION_SWITCH: {
				actionStr += "switchVal: " + _switchVal;
				break;
			}
			case SCHEDULE_ACTION_FADE: {
				actionStr += "switchVal: " + _switchVal + " duration:" + _fadeDuration;
				break;
			}
			case SCHEDULE_ACTION_TOGGLE:
			default: {
				actionStr += "toggle";
				break;
			}
		}

		return String.format(Locale.ENGLISH, "id=%d override=%d repeateType=%d actionType=%d timestamp=%d repeat=%s action=%s" ,
				_id,
				_overrideMask,
				_repeatType,
				_actionType,
				_timestamp,
				repeatStr,
				actionStr
				);
	}
}
