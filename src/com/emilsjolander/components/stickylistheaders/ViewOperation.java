/*
 * Copyright 2012 Emil Sjšlander
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.emilsjolander.components.stickylistheaders;

import android.view.View;

public class ViewOperation {
	
	public ViewOperation(View view, boolean remove) {
		this.view = view;
		this.remove = remove;
	}
	
	View view;
	boolean remove;

}
