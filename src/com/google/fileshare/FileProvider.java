//   Copyright 2009 Google Inc.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package com.google.fileshare;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;


public class FileProvider extends ContentProvider {

	public static final String AUTHORITY = "com.navjagpal.filesharer.FileProvider";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY  + "/files");
	public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.navjagpal.file";
	public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.navjagpal.file";

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getType(Uri uri) {
		String path = getPath(uri);
		File file = new File(path);
		if (file.isDirectory())
			return CONTENT_TYPE;
		else if (file.isFile())
			return CONTENT_ITEM_TYPE;

		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException("Deletes are not supported.");
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		return getCursorForFiles(getPath(uri));
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		throw new UnsupportedOperationException("Updates are not supported.");
	}

	/* Add a row to the matrix cursor */
	private void addRow(MatrixCursor cursor, File file, int id, String path) {
		String fileName = file.getName();
		int fileSize = (int)file.length();
		cursor.addRow(new Object[] {id, fileName, fileSize, path});
	}

	/* Return a cursor for files in the specified path */
	private Cursor getCursorForFiles(String path) {
		/* _id doesn't really mean anything but the system really wants it */
		String[] columns = { 
				"_id",
				OpenableColumns.DISPLAY_NAME,
				OpenableColumns.SIZE,
				"_data"
		};

		MatrixCursor c = new MatrixCursor(columns);

		File baseDir = new File(path);
		if (baseDir.isDirectory()) {
			File[] files = baseDir.listFiles();
			int id = 0;
			for (File file: files) {		
				addRow(c, file, id, file.getAbsolutePath());
				id++;
			}
		} else if (baseDir.isFile()) {
			addRow(c, baseDir, 0, baseDir.getAbsolutePath());
		}

		return c;
	}

	public ParcelFileDescriptor  openFile(Uri uri, String mode) throws FileNotFoundException, SecurityException  {
		return openFileHelper(uri, mode);
	}

	/* Construct a file path based on the uri */
	public static String getPath(Uri uri) {
		List<String> segments = uri.getPathSegments();

		String path = "";
		for (String segment: segments) {
			if (segment.equals("files"))
				continue;
			path += "/" + segment;
		}
		if (path == "")
			path = "/";

		return path;
	}


}
