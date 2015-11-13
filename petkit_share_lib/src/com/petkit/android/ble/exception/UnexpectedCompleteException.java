/*******************************************************************************
 * Copyright (c) 2013 Nordic Semiconductor. All Rights Reserved.
 * 
 * The information contained herein is property of Nordic Semiconductor ASA.
 * Terms and conditions of usage are described in detail in NORDIC SEMICONDUCTOR STANDARD SOFTWARE LICENSE AGREEMENT.
 * Licensees are granted free, non-transferable use of the information. NO WARRANTY of ANY KIND is provided. 
 * This heading must NOT be removed from the file.
 ******************************************************************************/
package com.petkit.android.ble.exception;

import com.petkit.android.ble.BLEConsts;
import com.petkit.android.utils.LogcatStorageHelper;


public class UnexpectedCompleteException extends Exception {
	private static final long serialVersionUID = -6901728550661937942L;

	private final int mError;

	public UnexpectedCompleteException(final String message, final int state) {
		super(message);

		mError = state;
	}

	public int getErrorNumber() {
		return mError;
	}

	@Override
	public String getMessage() {
		String message = super.getMessage() + " (error " + (mError & ~BLEConsts.ERROR_CONNECTION_MASK) + ")"; 
		LogcatStorageHelper.addLog(message);
		return message;
	}
}
