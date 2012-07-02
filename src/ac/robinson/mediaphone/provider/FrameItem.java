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

package ac.robinson.mediaphone.provider;

import ac.robinson.util.ImageCacheUtilities;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import ac.robinson.mediaphone.MediaPhone;
import ac.robinson.mediaphone.R;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.IOUtilities;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.TypedValue;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

public class FrameItem implements BaseColumns {

	public static final Uri CONTENT_URI = Uri.parse(MediaPhoneProvider.URI_PREFIX + MediaPhoneProvider.URI_AUTHORITY
			+ MediaPhoneProvider.URI_SEPARATOR + MediaPhoneProvider.FRAMES_LOCATION);

	public static final String[] PROJECTION_ALL = new String[] { FrameItem._ID, FrameItem.INTERNAL_ID,
			FrameItem.PARENT_ID, FrameItem.SEQUENCE_ID, FrameItem.DATE_CREATED, FrameItem.DELETED };

	public static final String[] PROJECTION_SEQEUENCE_ID = new String[] { FrameItem.SEQUENCE_ID };

	public static final String[] PROJECTION_INTERNAL_ID = new String[] { FrameItem.INTERNAL_ID };

	// *DO NOT CHANGE*
	// hacky way to add a button in the horiontal scroller - use a frame, showing a different display for this id.
	public static final String KEY_FRAME_ID_START = "67c2f330-78ec-11e1-b0c4-0800200c9a66";
	public static final String KEY_FRAME_ID_END = "7a342e00-abc2-11e0-9f1c-0800200c9a66";

	public static final String INTERNAL_ID = "internal_id";
	public static final String PARENT_ID = "parent_id";
	public static final String SEQUENCE_ID = "sequence_id";
	public static final String DATE_CREATED = "date_created";
	public static final String DELETED = "deleted";

	// so that frames are in the correct order in narratives
	public static final String DEFAULT_SORT_ORDER = SEQUENCE_ID + " ASC";

	private String mInternalId;
	private String mParentId;
	private int mNarrativeSequenceId;
	private long mCreationDate;
	private int mDeleted;

	public FrameItem(String parentId, int narrativeSequenceId) {
		mInternalId = MediaPhoneProvider.getNewInternalId();
		mParentId = parentId;
		mNarrativeSequenceId = narrativeSequenceId;
		mCreationDate = System.currentTimeMillis();
		mDeleted = 0;

		// so that we don't mkdirs when using a temporary frame; does mean that we need to do it manually sometimes
		if (parentId != null) {
			getStorageDirectory().mkdirs();
		}
	}

	public FrameItem() {
		this(null, -1);
	}

	public String getInternalId() {
		return mInternalId;
	}

	public String getParentId() {
		return mParentId;
	}

	public int getNarrativeSequenceId() {
		return mNarrativeSequenceId;
	}

	public void setNarrativeSequenceId(int narrativeSequenceId) {
		mNarrativeSequenceId = narrativeSequenceId;
	}

	public long getCreationDate() {
		return mCreationDate;
	}

	public boolean getDeleted() {
		return mDeleted == 0 ? false : true;
	}

	public void setDeleted(boolean deleted) {
		mDeleted = deleted ? 1 : 0;
	}

	public String getCacheId() {
		return getCacheId(mInternalId);
	}

	public static String getCacheId(String internalId) {
		return internalId;
	}

	public File getStorageDirectory() {
		return getStorageDirectory(mInternalId);
	}

	public static File getStorageDirectory(String frameInternalId) {
		final File filePath = new File(MediaPhone.DIRECTORY_STORAGE, frameInternalId);
		return filePath;
	}

	/**
	 * Get existing text content if it exists
	 * 
	 * @param contentResolver
	 * @param parentInternalId The internal ID of the frame to search within
	 * @return The internal ID of the text content, or null if none exists
	 */
	public static String getTextContentId(ContentResolver contentResolver, String parentInternalId) {
		ArrayList<MediaItem> frameComponents = MediaManager.findMediaByParentId(contentResolver, parentInternalId);
		for (MediaItem media : frameComponents) {
			if (media.getType() == MediaPhoneProvider.TYPE_TEXT) {
				return media.getInternalId();
			}
		}
		return null;
	}

	/**
	 * Get existing image content if it exists
	 * 
	 * @param contentResolver
	 * @param parentInternalId The internal ID of the frame to search within
	 * @return The internal ID of the image content, or null if none exists
	 */
	public static String getImageContentId(ContentResolver contentResolver, String parentInternalId) {
		ArrayList<MediaItem> frameComponents = MediaManager.findMediaByParentId(contentResolver, parentInternalId);
		for (MediaItem media : frameComponents) {
			switch (media.getType()) {
				case MediaPhoneProvider.TYPE_IMAGE_BACK:
				case MediaPhoneProvider.TYPE_IMAGE_FRONT:
				case MediaPhoneProvider.TYPE_VIDEO:
					return media.getInternalId();
				default:
					break;
			}
		}
		return null;
	}

	/**
	 * Equivalent to loadIcon(resources, contentResolver, null, true);
	 * 
	 * @param resources
	 * @param contentResolver
	 * @return The icon, or null if there is no media content in this frame
	 */
	public Bitmap loadIcon(Resources resources, ContentResolver contentResolver) {
		return loadIcon(resources, contentResolver, null, true);
	}

	/**
	 * 
	 * @param res
	 * @param contentResolver
	 * @param cacheTypeContainer
	 * @param frameIsInDatabase whether the frame has already been added to database
	 * @return The icon, or null if there is no media content in this frame
	 */
	public Bitmap loadIcon(Resources res, ContentResolver contentResolver,
			BitmapUtilities.CacheTypeContainer cacheTypeContainer, boolean frameIsInDatabase) {

		ArrayList<MediaItem> frameComponents = MediaManager.findMediaByParentId(contentResolver, mInternalId);
		if ((frameComponents.size() <= 0)) {
			return null;
		}

		Bitmap frameBitmap = null;
		boolean imageLoaded = false;
		boolean textLoaded = false;
		boolean audioLoaded = false;
		String textString = "";
		int iconWidth = res.getDimensionPixelSize(R.dimen.frame_icon_width);
		int iconHeight = res.getDimensionPixelSize(R.dimen.frame_icon_height);

		// load the image icon and prepare the other media items
		Iterator<MediaItem> iterator = frameComponents.iterator();
		while (iterator.hasNext()) {
			MediaItem currentItem = iterator.next();
			int currentType = currentItem.getType();

			if (!imageLoaded
					&& (currentType == MediaPhoneProvider.TYPE_IMAGE_BACK
							|| currentType == MediaPhoneProvider.TYPE_IMAGE_FRONT || currentType == MediaPhoneProvider.TYPE_VIDEO)) {

				frameBitmap = currentItem.loadIcon(iconWidth, iconHeight);
				if (frameBitmap != null) {
					imageLoaded = true;
				}

			} else if (currentType == MediaPhoneProvider.TYPE_AUDIO) {
				audioLoaded = true;

			} else if (!textLoaded && currentType == MediaPhoneProvider.TYPE_TEXT) {

				textString = IOUtilities.getFileContents(currentItem.getFile().getAbsolutePath()).toString();
				textLoaded = true;
			}
		}

		// make sure we always have an icon, regardless of media
		if (frameBitmap == null) {
			frameBitmap = Bitmap.createBitmap(iconWidth, iconHeight,
					ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
			frameBitmap.eraseColor(res.getColor(R.color.frame_icon_background));
		}
		TypedValue resourceValue = new TypedValue();
		Canvas frameBitmapCanvas = new Canvas(frameBitmap);
		int textColour = (imageLoaded ? res.getColor(R.color.frame_icon_text_with_image) : res
				.getColor(R.color.frame_icon_text_no_image));
		Paint frameBitmapPaint = BitmapUtilities.getPaint(textColour, 1);
		final int bitmapWidth = frameBitmap.getWidth();
		final int bitmapHeight = frameBitmap.getHeight();
		final int borderWidth = res.getDimensionPixelSize(R.dimen.frame_icon_border_width);

		// add the text overlay
		if (textLoaded) {
			frameBitmapPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

			int textSpacing = res.getDimensionPixelSize(R.dimen.frame_icon_text_padding);
			res.getValue(R.attr.frame_icon_text_corner_radius, resourceValue, true);
			float textCornerRadius = resourceValue.getFloat();
			BitmapUtilities.drawScaledText(textString, frameBitmapCanvas, frameBitmapPaint, textColour,
					(imageLoaded ? res.getColor(R.color.frame_icon_text_background) : 0), textSpacing,
					textCornerRadius, imageLoaded, bitmapWidth - textSpacing,
					(imageLoaded ? res.getDimensionPixelSize(R.dimen.frame_icon_maximum_text_height_with_image)
							- textSpacing : bitmapHeight - textSpacing),
					res.getDimensionPixelSize(R.dimen.frame_icon_maximum_text_size),
					res.getInteger(R.integer.frame_icon_maximum_text_characters_per_line));

			// add border if there's no image (looks much tidier)
			if (!imageLoaded) {
				BitmapUtilities.addBorder(frameBitmapCanvas, frameBitmapPaint, borderWidth,
						res.getColor(R.color.frame_icon_border));
			}
		}

		// add the audio overlay
		if (audioLoaded) {
			Rect drawRect = null;
			if (!imageLoaded && !textLoaded) {
				BitmapUtilities.addBorder(frameBitmapCanvas, frameBitmapPaint, borderWidth,
						res.getColor(R.color.frame_icon_border));

				res.getValue(R.attr.frame_icon_scale_factor, resourceValue, true);
				float scaleFactor = resourceValue.getFloat();
				int iconLeft = Math.round((bitmapWidth - (bitmapWidth * scaleFactor)) / 2);
				int iconTop = Math.round((bitmapHeight - (bitmapHeight * scaleFactor)) / 2);
				drawRect = new Rect(iconLeft, iconTop, bitmapWidth - iconLeft, bitmapHeight - iconTop);
			} else {
				res.getValue(R.attr.frame_icon_overlay_scale_factor, resourceValue, true);
				float scaleFactor = resourceValue.getFloat();
				res.getValue(R.attr.frame_icon_overlay_spacing_factor, resourceValue, true);
				float spacingFactor = resourceValue.getFloat();
				int iconSpacingRight = Math.round(bitmapWidth * spacingFactor);
				int iconSpacingBottom = Math.round(bitmapHeight * spacingFactor);
				drawRect = new Rect(bitmapWidth - Math.round(bitmapWidth * scaleFactor) - iconSpacingRight,
						bitmapHeight - Math.round(bitmapHeight * scaleFactor) - iconSpacingBottom, bitmapWidth
								- iconSpacingRight, bitmapHeight - iconSpacingBottom);
			}

			// using SVG so that we don't need resolution-specific icons
			// TODO: may not work with hardware acceleration (fix - see: https://gist.github.com/6ebe5b818652d5ccc27c)
			SVG audioSVG = SVGParser.getSVGFromResource(res, R.raw.overlay_audio);
			frameBitmapCanvas.drawPicture(audioSVG.getPicture(), drawRect);
		}

		boolean isFirstFrame = false;
		if (mNarrativeSequenceId == 0) {
			isFirstFrame = true;
		} else if (frameIsInDatabase) {
			FrameItem firstFrame = FramesManager.findFirstFrameByParentId(contentResolver, mParentId);
			if (firstFrame != null && mInternalId.equals(firstFrame.getInternalId())) {
				isFirstFrame = true;
			}
		}

		// so we can add an indicator to the frame at position 0
		if (isFirstFrame) {
			// must deal with both narratives and templates
			NarrativeItem parentNarrative = NarrativesManager.findNarrativeByInternalId(contentResolver, mParentId);
			if (parentNarrative == null) {
				parentNarrative = NarrativesManager.findTemplateByInternalId(contentResolver, mParentId);
				if (parentNarrative == null) {
					parentNarrative = new NarrativeItem(NarrativesManager.getNextNarrativeExternalId(contentResolver));
				}
			}
			String narrativeSequenceNumber = Integer.toString(parentNarrative.getSequenceId());
			res.getValue(R.attr.frame_icon_indicator_width_factor, resourceValue, true);
			float indicatorWidth = bitmapWidth * resourceValue.getFloat();
			res.getValue(R.attr.frame_icon_indicator_text_maximum_width_factor, resourceValue, true);
			float textWidth = bitmapWidth * resourceValue.getFloat();
			int halfFrameHeight = bitmapHeight / 2;

			frameBitmapPaint.setColor(res.getColor(R.color.frame_icon_indicator));
			frameBitmapPaint.setStrokeWidth(1);
			frameBitmapPaint.setStyle(Paint.Style.FILL);
			frameBitmapPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
			frameBitmapPaint = BitmapUtilities.adjustTextSize(frameBitmapPaint, narrativeSequenceNumber.length(), 1,
					textWidth, bitmapHeight, res.getDimensionPixelSize(R.dimen.frame_icon_indicator_maximum_text_size));

			// the background line
			frameBitmapCanvas.drawRect(new Rect(0, 0, Math.round(indicatorWidth), frameBitmap.getHeight()),
					frameBitmapPaint);

			// the background box
			Rect textBounds = new Rect();
			frameBitmapPaint.getTextBounds(narrativeSequenceNumber, 0, narrativeSequenceNumber.length(), textBounds);
			res.getValue(R.attr.frame_icon_indicator_corner_radius, resourceValue, true);
			float cornerRadius = textBounds.height() * resourceValue.getFloat();
			res.getValue(R.attr.frame_icon_indicator_text_left_spacing_factor, resourceValue, true);
			float textLeft = indicatorWidth * resourceValue.getFloat();
			frameBitmapCanvas.drawRoundRect(
					new RectF(0, halfFrameHeight - textBounds.height(), textLeft + textBounds.width()
							+ (textBounds.height() / 2), halfFrameHeight + textBounds.height()), cornerRadius,
					cornerRadius, frameBitmapPaint);

			// the actual text
			frameBitmapPaint.setColor(res.getColor(R.color.frame_icon_indicator_text));
			frameBitmapCanvas.drawText(narrativeSequenceNumber, textLeft, halfFrameHeight + textBounds.height() / 2,
					frameBitmapPaint);
		}

		frameBitmapCanvas = null;

		// PNG is much better for non-photo icons
		if (!imageLoaded) {
			cacheTypeContainer.type = Bitmap.CompressFormat.PNG;
		}

		return frameBitmap;
	}

	public static Bitmap loadTemporaryIcon(Resources res, boolean addBorder) {
		int iconWidth = res.getDimensionPixelSize(R.dimen.frame_icon_width);
		int iconHeight = res.getDimensionPixelSize(R.dimen.frame_icon_height);
		Bitmap tempBitmap = Bitmap.createBitmap(iconWidth, iconHeight,
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		if (addBorder) {
			int borderWidth = res.getDimensionPixelSize(R.dimen.frame_icon_border_width);
			Canvas tempBitmapCanvas = new Canvas(tempBitmap);
			Paint tempBitmapPaint = BitmapUtilities.getPaint(0, 1);
			tempBitmapCanvas.drawColor(res.getColor(R.color.frame_icon_background));
			BitmapUtilities.addBorder(tempBitmapCanvas, tempBitmapPaint, borderWidth,
					res.getColor(R.color.frame_icon_border));
		} else {
			tempBitmap.eraseColor(res.getColor(R.color.frame_icon_background));
		}
		return tempBitmap;
	}

	public ContentValues getContentValues() {
		final ContentValues values = new ContentValues();
		values.put(INTERNAL_ID, mInternalId);
		values.put(PARENT_ID, mParentId);
		values.put(SEQUENCE_ID, mNarrativeSequenceId);
		values.put(DATE_CREATED, mCreationDate);
		values.put(DELETED, mDeleted);
		return values;
	}

	public static FrameItem fromExisting(FrameItem existing, String newInternalId, String newParentId,
			long newCreationDate) {
		final FrameItem frame = new FrameItem();
		frame.mInternalId = newInternalId;
		frame.mParentId = newParentId;
		frame.mNarrativeSequenceId = existing.mNarrativeSequenceId;
		frame.mCreationDate = newCreationDate;
		frame.mDeleted = existing.mDeleted;
		frame.getStorageDirectory().mkdirs();
		return frame;
	}

	public static FrameItem fromCursor(Cursor c) {
		final FrameItem frame = new FrameItem();
		frame.mInternalId = c.getString(c.getColumnIndexOrThrow(INTERNAL_ID));
		frame.mParentId = c.getString(c.getColumnIndexOrThrow(PARENT_ID));
		frame.mNarrativeSequenceId = c.getInt(c.getColumnIndexOrThrow(SEQUENCE_ID));
		frame.mCreationDate = c.getLong(c.getColumnIndexOrThrow(DATE_CREATED));
		frame.mDeleted = c.getInt(c.getColumnIndexOrThrow(DELETED));
		return frame;
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "[" + mInternalId + "," + mParentId + "," + mNarrativeSequenceId + ","
				+ mCreationDate + "," + mDeleted + "]";
	}
}
