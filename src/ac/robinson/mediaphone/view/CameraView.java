/*
 *  Copyright (C) 2012 Simon Robinson
 * 
 *  This file is part of Com-Me.
 * 
 *  Com-Me is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU Lesser General Public License as 
 *  published by the Free Software Foundation; either version 3 of the 
 *  License, or (at your option) any later version.
 *
 *  Com-Me is distributed in the hope that it will be useful, but WITHOUT 
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General 
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with Com-Me.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package ac.robinson.mediaphone.view;

import java.io.IOException;
import java.util.List;

import ac.robinson.mediaphone.MediaPhone;
import ac.robinson.mediaphone.R;
import ac.robinson.util.DebugUtilities;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

//see: http://developer.android.com/resources/samples/ApiDemos/src/com/example/android/apis/graphics/CameraPreview.html
public class CameraView extends ViewGroup implements SurfaceHolder.Callback {

	private SurfaceView mSurfaceView;
	private SurfaceHolder mHolder;
	private Size mPreviewSize;
	private List<Size> mSupportedPreviewSizes;
	private Camera mCamera;

	private int mDisplayRotation;
	private int mCameraRotation;
	private boolean mTakePicture;
	private boolean mStopPreview;
	private boolean mCanAutoFocus;
	private boolean mIsAutoFocusing;
	private int mAutoFocusInterval;

	private AutoFocusHandler mAutoFocusHandler;
	private AutoFocusCallback mAutoFocusCallback;

	private SoundPool mFocusSoundPlayer;
	private int mFocusSoundId;

	public class CameraImageConfiguration {
		public int imageFormat;
		public int width;
		public int height;
	}

	public CameraView(Context context) {
		super(context);

		setBackgroundColor(Color.BLACK);

		mSurfaceView = new SurfaceView(context);
		addView(mSurfaceView);

		// install callback so we're notified when surface is created/destroyed
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		mIsAutoFocusing = false;
		mTakePicture = false;
		mStopPreview = false;
		mCanAutoFocus = false;

		mAutoFocusHandler = new AutoFocusHandler();
		mAutoFocusCallback = new AutoFocusCallback();
	}

	public void surfaceCreated(SurfaceHolder holder) {
		// surface has been created, acquire camera and tell it where to draw
		if (mCamera != null) {
			try {
				mCamera.setPreviewDisplay(holder);
			} catch (IOException e) {
				if (MediaPhone.DEBUG)
					Log.d(DebugUtilities.getLogTag(this), "surfaceCreated() -> setPreviewDisplay()", e);
			}
		}
		mFocusSoundId = -1;
		mFocusSoundPlayer = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
		mFocusSoundPlayer.setOnLoadCompleteListener(new OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
				mFocusSoundId = sampleId;
			}
		});
		mFocusSoundPlayer.load(getContext(), R.raw.af_success, 1);
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// surface will be destroyed when we return, so stop focus and preview
		mAutoFocusCallback.setHandler(null);
		if (mCamera != null) {
			mCamera.stopPreview();
			try {
				mCamera.setPreviewDisplay(null);
			} catch (IOException e) {
				if (MediaPhone.DEBUG)
					Log.d(DebugUtilities.getLogTag(this), "surfaceDestroyed() -> setPreviewDisplay()", e);
			}
		}
		if (mFocusSoundPlayer != null) {
			mFocusSoundPlayer.unload(mFocusSoundId);
			mFocusSoundPlayer.release();
			mFocusSoundPlayer = null;
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// we now know the surface size - set up the display and begin the preview.
		requestLayout();
		if (mCamera != null) {
			Camera.Parameters parameters = mCamera.getParameters();

			// supported preview sizes checked earlier
			parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

			// TODO: not applicable to new method of preview capture, so not as
			// important any more - now left to the camera to choose the default
			// see: http://stackoverflow.com/questions/6469019/
			// also: http://stackoverflow.com/questions/5859876/
			// List<Size> supportedPictureSizes =
			// parameters.getSupportedPictureSizes();
			// Size chosenSize = supportedPictureSizes.get(0);
			// parameters.setPictureSize(chosenSize.width, chosenSize.height);

			parameters.setRotation(mCameraRotation);

			mCamera.setDisplayOrientation(mDisplayRotation);
			mCamera.setParameters(parameters);

			startCameraPreview();
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// purposely disregard child measurements - we want to centre the camera preview instead of stretching it
		final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
		setMeasuredDimension(width, height);

		if (mSupportedPreviewSizes != null) {
			mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		if (changed && getChildCount() > 0) {
			final View child = getChildAt(0);

			final int width = r - l;
			final int height = b - t;

			int previewWidth = width;
			int previewHeight = height;
			if (mPreviewSize != null) {
				if (mDisplayRotation == 90 || mDisplayRotation == 270) {
					previewWidth = mPreviewSize.height;
					previewHeight = mPreviewSize.width;
				} else {
					previewWidth = mPreviewSize.width;
					previewHeight = mPreviewSize.height;
				}
			}

			// centre the child SurfaceView within the parent.
			if (width * previewHeight > height * previewWidth) {
				final int scaledChildWidth = previewWidth * height / previewHeight;
				child.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
			} else {
				final int scaledChildHeight = previewHeight * width / previewWidth;
				// child.layout(0, (height - scaledChildHeight) / 2,
				// width, (height + scaledChildHeight) / 2);
				child.layout(0, 0, width, scaledChildHeight); // horizontal centering only
			}
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				requestAutoFocus(mAutoFocusHandler);
				return true; // processed
			default:
				return super.onTouchEvent(event);
		}
	}

	/**
	 * 
	 * 
	 * @param camera
	 * @param rotation
	 * @param jpegQuality
	 * @param autoFocusInterval Set to 0 to disable automatic refocusing
	 */
	public void setCamera(Camera camera, int displayRotation, int cameraRotation, int jpegQuality, int autoFocusInterval) {
		mCamera = camera;
		mDisplayRotation = displayRotation;
		mCameraRotation = cameraRotation;
		mAutoFocusInterval = autoFocusInterval;
		if (mCamera != null) {
			Camera.Parameters parameters = mCamera.getParameters();

			mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
			parameters.setJpegThumbnailSize(0, 0); // TODO: no need for a thumbnail?

			// List<Integer> previewFormats = parameters.getSupportedPreviewFormats();
			// if (previewFormats != null) { // the documentation lies
			// if (previewFormats.contains(ImageFormat.YUY2)) {
			// parameters.setPreviewFormat(ImageFormat.YUY2);
			// }
			// }

			List<Integer> imageFormats = parameters.getSupportedPictureFormats();
			if (imageFormats != null) { // the documentation lies
				if (imageFormats.contains(ImageFormat.JPEG)) {
					parameters.setPictureFormat(ImageFormat.JPEG);
					if (jpegQuality > 0) {
						parameters.setJpegQuality(jpegQuality);
					}
				}
			}

			List<String> modes = parameters.getSupportedAntibanding();
			if (modes != null) {
				if (modes.contains(Camera.Parameters.ANTIBANDING_AUTO)) {
					parameters.setAntibanding(Camera.Parameters.ANTIBANDING_AUTO);
				}
			}

			modes = parameters.getSupportedFlashModes();
			if (modes != null) {
				if (modes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
					parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
				}
			}

			modes = parameters.getSupportedWhiteBalance();
			if (modes != null) {
				if (modes.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
					parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
				}
			}

			mCanAutoFocus = false;
			modes = parameters.getSupportedFocusModes();
			if (modes != null) { // the documentation lies
				if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
					mCanAutoFocus = true;
				}
			}
			mCamera.setParameters(parameters);

			requestLayout();
		}
	}

	public void setRotation(int displayRotation, int cameraRotation) {
		mDisplayRotation = displayRotation;
		mCameraRotation = cameraRotation;
		Camera.Parameters parameters = mCamera.getParameters();
		parameters.setRotation(mCameraRotation);
		mCamera.setParameters(parameters);
		requestLayout();
	}

	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
		final double ASPECT_TOLERANCE = 0.05;
		double targetRatio = (double) w / h;
		if (sizes == null)
			return null;

		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		int targetWidth = w;

		// try to find a size that matches aspect ratio and size
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;

			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.width - targetWidth) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.width - targetWidth);
			}
		}

		// can't find one that matches the aspect ratio, so ignore the requirement
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.width - targetWidth) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.width - targetWidth);
				}
			}
		}
		return optimalSize;
	}

	@Override
	public void setVisibility(int visibility) {
		if (visibility == View.VISIBLE) {
			startCameraPreview();
		} else {
			stopCameraPreview();
		}
		super.setVisibility(visibility);
	}

	private void startCameraPreview() {
		mIsAutoFocusing = false;
		mTakePicture = false;
		mStopPreview = false;
		mCamera.startPreview();
		if (mAutoFocusInterval > 0) {
			requestAutoFocus(mAutoFocusHandler);
		}
	}

	private void stopCameraPreview() {
		mStopPreview = true;
	}

	public boolean canAutoFocus() {
		return mCanAutoFocus;
	}

	public CameraImageConfiguration getPreviewConfiguration() {
		CameraImageConfiguration previewConfiguration = new CameraImageConfiguration();

		Camera.Parameters parameters = mCamera.getParameters();
		previewConfiguration.imageFormat = parameters.getPreviewFormat();
		previewConfiguration.width = mPreviewSize.width;
		previewConfiguration.height = mPreviewSize.height;

		return previewConfiguration;
	}

	public CameraImageConfiguration getPictureConfiguration() {
		CameraImageConfiguration pictureConfiguration = new CameraImageConfiguration();

		Camera.Parameters parameters = mCamera.getParameters();
		Size size = parameters.getPictureSize();
		pictureConfiguration.imageFormat = parameters.getPictureFormat();
		pictureConfiguration.width = size.width;
		pictureConfiguration.height = size.height;

		return pictureConfiguration;
	}

	public void takePicture(Camera.ShutterCallback shutterCallback, Camera.PictureCallback pictureCallback,
			Camera.PictureCallback pictureJpegCallback) {
		mTakePicture = true;
		mCamera.takePicture(shutterCallback, pictureCallback, pictureJpegCallback);
	}

	public void capturePreviewFrame(Camera.PreviewCallback previewFrameCallback) {
		mCamera.setOneShotPreviewCallback(previewFrameCallback);
	}

	private void playAutoFocusSound() {
		if (mFocusSoundPlayer != null && mFocusSoundId >= 0) {
			AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
			int streamVolumeCurrent = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
			if (streamVolumeCurrent > 0) {
				float streamVolumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
				float volume = streamVolumeCurrent / streamVolumeMax;
				mFocusSoundPlayer.play(mFocusSoundId, volume, volume, 1, 0, 1);
			}
		}
	}

	private void requestAutoFocus(Handler handler) {
		if (mCamera != null && !mTakePicture && mCanAutoFocus && !mIsAutoFocusing) {
			Camera.Parameters parameters = mCamera.getParameters();
			if (parameters.getFocusMode() != Camera.Parameters.FOCUS_MODE_AUTO) {
				parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
				mCamera.setParameters(parameters);
			}
			mIsAutoFocusing = true;
			mAutoFocusCallback.setHandler(handler);
			mCamera.autoFocus(mAutoFocusCallback);
		}
	}

	public class AutoFocusHandler extends Handler {
		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
				case R.id.msg_auto_focus:
					requestAutoFocus(this);
					break;
				default:
					break;
			}
		}
	}

	// see:
	// http://code.google.com/p/zxing/source/browse/trunk/android/src/com/google/zxing/client/android/camera/AutoFocusCallback.java?r=1698
	private class AutoFocusCallback implements Camera.AutoFocusCallback {

		private Handler mAutoFocusHandler;

		void setHandler(Handler autoFocusHandler) {
			this.mAutoFocusHandler = autoFocusHandler;
		}

		public void onAutoFocus(boolean success, Camera camera) {
			mIsAutoFocusing = false;

			if (success) {
				playAutoFocusSound();
			}

			// always cancel - will crash if we autofocus while taking a picture
			if (mAutoFocusHandler != null) {
				mAutoFocusHandler.removeMessages(R.id.msg_auto_focus);
			}

			if (mStopPreview) {
				mCamera.stopPreview();
				mAutoFocusHandler = null;
				return;
			}

			if (!mTakePicture) {
				// simulate continuous autofocus by sending a focus request every [interval] milliseconds
				if (mAutoFocusInterval > 0) {
					if (mAutoFocusHandler != null) {
						mAutoFocusHandler.sendMessageDelayed(
								mAutoFocusHandler.obtainMessage(R.id.msg_auto_focus, success), mAutoFocusInterval);
						mAutoFocusHandler = null;
					} else {
						if (MediaPhone.DEBUG)
							Log.d(DebugUtilities.getLogTag(this), "Focus callback without handler");
					}
				}
			}
		}
	}
}
