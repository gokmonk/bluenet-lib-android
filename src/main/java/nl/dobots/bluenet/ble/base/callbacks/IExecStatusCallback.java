package nl.dobots.bluenet.ble.base.callbacks;

import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 28-7-15
 *
 * Status callback used for connectAndExecute calls. will call the onSuccess depending on the
 * function that is executed
 *
 * @author Dominik Egger
 */
public interface IExecStatusCallback extends IBaseCallback, IStatusCallback, IIntegerCallback, IBooleanCallback, IByteArrayCallback {

	void onExecuteSuccess(boolean disconnect);

}
