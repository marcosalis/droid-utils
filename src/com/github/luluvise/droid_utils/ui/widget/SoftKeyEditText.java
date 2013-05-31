/*
 * Copyright 2013 Luluvise Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.luluvise.droid_utils.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

import com.google.common.annotations.Beta;

/**
 * Extension of the {@link EditText} class that allows setting a callback to be
 * notified when the soft keyboard is hidden, in order to update the UI of a
 * layout.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
public class SoftKeyEditText extends EditText {

	private OnSoftKeyboardHiddenListener mSoftKeyboardListener;

	public SoftKeyEditText(Context context) {
		super(context);
	}

	public SoftKeyEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public SoftKeyEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public void setOnSoftKeyboardHiddenListener(OnSoftKeyboardHiddenListener listener) {
		mSoftKeyboardListener = listener;
	}

	@Override
	public boolean dispatchKeyEventPreIme(KeyEvent event) {
		// immediately call back to the super class
		boolean dispatched = super.dispatchKeyEventPreIme(event);
		// intercept BACK button press
		if (KeyEvent.KEYCODE_BACK == event.getKeyCode() && event.getAction() == KeyEvent.ACTION_DOWN) {
			// notify callback if existing
			if (mSoftKeyboardListener != null) {
				mSoftKeyboardListener.onSoftKeyboardHidden();
			}
		}
		return dispatched;
	}

	/**
	 * Callback interface for soft keyboard state changes
	 */
	@Beta
	public static interface OnSoftKeyboardHiddenListener {

		public void onSoftKeyboardHidden();

	}

}