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
package com.github.luluvise.droid_utils.lib;

import java.io.File;

import javax.annotation.concurrent.NotThreadSafe;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import com.google.common.base.Preconditions;

/**
 * Utility class to delegate handling the request for either a camera or gallery
 * picture from an Activity (or Fragment).
 * 
 * Instantiate by passing required width and height of the generated image, a
 * callback to be notified of the result.
 * 
 * TODO: unit tests
 * 
 * @since 1.0
 * @author Marco Salis
 */
@NotThreadSafe
public class PictureHandler implements Parcelable {

	private static final int REQUEST_TAKE_PICTURE = 101;
	private static final int REQUEST_PICK_PICTURE = 102;

	private static final String PIC_FILE_PREFIX = "pic_";

	private OnPictureRetrievedListener mListener;
	private int mReqWidth;
	private int mReqHeight;

	private Uri mTakenPictureUri;

	public PictureHandler(OnPictureRetrievedListener listener) {
		mListener = listener;
	}

	public PictureHandler(Parcel source) {
		// Reconstruct from the Parcel
		mReqWidth = source.readInt();
		mReqHeight = source.readInt();
		String uri;
		if ((uri = source.readString()) != null) {
			mTakenPictureUri = new Uri.Builder().path(uri).build();
		}
	}

	/**
	 * Needed for the Parcelable functionalities
	 */
	public static final Parcelable.Creator<PictureHandler> CREATOR = new Parcelable.Creator<PictureHandler>() {
		@Override
		public PictureHandler createFromParcel(Parcel source) {
			return new PictureHandler(source);
		}

		@Override
		public PictureHandler[] newArray(int size) {
			return new PictureHandler[size];
		}
	};

	@Override
	public int describeContents() {
		return hashCode();
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(mReqWidth);
		dest.writeInt(mReqHeight);
		dest.writeString((mTakenPictureUri != null) ? mTakenPictureUri.getPath() : null);
	}

	public void setRequiredSize(int reqWidth, int reqHeight) {
		mReqWidth = reqWidth;
		mReqHeight = reqHeight;
	}

	public void setOnPictureRetrievedListener(OnPictureRetrievedListener listener) {
		mListener = listener;
	}

	/**
	 * Allows users to take a picture with the default camera application
	 * 
	 * @param activity
	 *            The Activity to start the camera application
	 */
	public void takeCameraPicture(Activity activity) {
		File photoPath = BitmapUtils.getPublicPicturesDir(activity);
		File photo = new File(photoPath, PIC_FILE_PREFIX + System.currentTimeMillis() + ".jpg");
		mTakenPictureUri = Uri.fromFile(photo);
		Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		i.putExtra(MediaStore.EXTRA_OUTPUT, mTakenPictureUri);
		activity.startActivityForResult(i, REQUEST_TAKE_PICTURE);
	}

	/**
	 * Allows users to pick a picture from the default gallery application
	 * 
	 * @param activity
	 *            The Activity to start the gallery application
	 */
	public void pickGalleryPicture(Activity activity) {
		// start gallery to let the user pick a picture
		Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		activity.startActivityForResult(i, REQUEST_PICK_PICTURE);
	}

	/**
	 * Call this method from the Activity's onActivityResult to get the picture
	 * result.
	 * 
	 * @see {Activity{@link #onActivityResult(Activity, int, int, Intent)}
	 * 
	 * @throws IllegalArgumentException
	 *             if width and height haven't been set
	 */
	public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		// the user has picked a picture from gallery
		case REQUEST_TAKE_PICTURE: {
			if (resultCode == Activity.RESULT_OK && mTakenPictureUri != null) {
				Preconditions.checkArgument(mReqWidth != 0 && mReqHeight != 0);
				final String path = mTakenPictureUri.getPath();
				Bitmap image = BitmapUtils.loadBitmapFromPath(path, mReqWidth, mReqHeight);
				mListener.onPictureRetrieved(image, path);
				// add to media store
				Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
				mediaScanIntent.setData(mTakenPictureUri);
				activity.sendBroadcast(mediaScanIntent);
			}
			break;
		}
		case REQUEST_PICK_PICTURE: {
			if (resultCode == Activity.RESULT_OK && data != null) {
				Preconditions.checkArgument(mReqWidth != 0 && mReqHeight != 0);
				String[] cols = BitmapUtils.getImagesMediaColumns();
				// query content resolver for image data
				Cursor cursor = activity.getContentResolver().query(data.getData(), cols, null,
						null, null);
				if (cursor != null) {
					if (cursor.moveToFirst()) {
						// we've found the image, load Bitmap
						String picturePath = cursor.getString(cursor.getColumnIndex(cols[0]));
						Bitmap image = BitmapUtils.loadBitmapFromPath(picturePath, mReqWidth,
								mReqHeight);
						mListener.onPictureRetrieved(image, picturePath);
					}
					cursor.close();
				}
			}
			break;
		}
		}
	}

	/**
	 * Interface to implement to get the Bitmap result
	 */
	public interface OnPictureRetrievedListener {

		/**
		 * Called when the picture has been retrieved.<br>
		 * This is guaranteed to be called from the UI thread.
		 * 
		 * @param bitmap
		 *            The resized Bitmap, or null if an error occurred
		 * @param path
		 *            The path where the original image is stored
		 */
		public void onPictureRetrieved(Bitmap bitmap, String path);

	}

}
