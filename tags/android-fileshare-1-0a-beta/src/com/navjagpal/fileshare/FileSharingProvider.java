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

package com.navjagpal.fileshare;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.OpenableColumns;
import android.util.Log;

public class FileSharingProvider extends ContentProvider {

	private static final String AUTHORITY = "com.navjagpal.filesharer.FileSharingProvider";

	private static final String TAG = "FileSharingProvider";

	private static final String DATABASE_NAME = "file_sharer.db";
	private static final int DATABASE_VERSION = 1;
	private static final String FOLDERS_TABLE_NAME = "folders";
	private static final String FILES_TABLE_NAME = "files";

	/* Internal codes for dealing with different types */
	private static final int FOLDERS = 1;
	private static final int FILE = 2;
	private static final int FOLDER = 3;
	private static final int FILES = 4;

	private static HashMap<String, String> sFoldersProjectionMap;
	private static HashMap<String, String> sFilesProjectionMap;

	private static final UriMatcher sUriMatcher;

	/* Folder related constants */
	public interface Folders {

		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/folders");

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.navjagpal.sharedfolder";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.navjagpal.sharedfolder";

		public interface Columns extends BaseColumns, OpenableColumns {
			public static final String PASSWORD = "password";
		}
	}

	/* File related constants */
	public interface Files {
		public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/files");

		public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.navjagpal.sharedfile";
		public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.navjagpal.sharedfile";

		public interface Columns extends BaseColumns, OpenableColumns {
			public static final String _DATA = "_data";
			public static final String FOLDER_ID = "folder_id";
		}
	}

	/**
	 * This class helps open, create, and upgrade the database file.
	 */
	private static class DatabaseHelper extends SQLiteOpenHelper {

		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + FOLDERS_TABLE_NAME + " ("
					+ Folders.Columns._ID + " INTEGER PRIMARY KEY,"
					+ Folders.Columns.DISPLAY_NAME + " TEXT,"
					+ Folders.Columns.PASSWORD + " TEXT"
					+ ");");
			db.execSQL("CREATE TABLE " + FILES_TABLE_NAME + " ("
					+ Files.Columns._ID + " INTEGER PRIMARY KEY,"
					+ Files.Columns.FOLDER_ID + " INTEGER,"
					+ Files.Columns.DISPLAY_NAME + " TEXT,"
					+ Files.Columns._DATA + " TEXT"
					+ ");");
			db.execSQL("INSERT INTO " + FOLDERS_TABLE_NAME + " VALUES(0, 'Public', null)");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS " + FOLDERS_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS " + FILES_TABLE_NAME);
			onCreate(db);
		}
	}

	private DatabaseHelper mOpenHelper;

	@Override
	public boolean onCreate() {
		mOpenHelper = new DatabaseHelper(getContext());
		return true;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
		case FOLDERS:
			return Folders.CONTENT_TYPE;
		case FOLDER:
			return Folders.CONTENT_ITEM_TYPE;
		case FILES:
			return Files.CONTENT_TYPE;
		case FILE:
			return Files.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// Validate the requested uri
		if (getType(uri).equals(Folders.CONTENT_TYPE)) {

			if (values.containsKey(Folders.Columns.DISPLAY_NAME) == false) {
				throw new IllegalArgumentException("Name required");
			}

			SQLiteDatabase db = mOpenHelper.getWritableDatabase();
			long rowId = db.insert(FOLDERS_TABLE_NAME, Folders.Columns.DISPLAY_NAME, values);
			if (rowId > 0) {
				Uri folderUri = ContentUris.withAppendedId(Folders.CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(folderUri, null);
				return folderUri;
			}

		} else if (getType(uri).equals(Files.CONTENT_TYPE)) {
			if (!values.containsKey(Files.Columns.FOLDER_ID)) {
				throw new IllegalArgumentException("Folder id required");
			}
			if (!values.containsKey(Files.Columns.DISPLAY_NAME)) {
				throw new IllegalArgumentException("File name required");	
			}
			if (!values.containsKey(Files.Columns._DATA)) {
				throw new IllegalArgumentException("Data URI required");
			}

			SQLiteDatabase db = mOpenHelper.getWritableDatabase();
			long rowId = db.insert(FILES_TABLE_NAME, Files.Columns.DISPLAY_NAME, values);
			if (rowId >= 0) {
				Uri fileUri = ContentUris.withAppendedId(Files.CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(fileUri, null);
				Log.i(TAG, "Inserted row " + fileUri.toString());
				return fileUri;
			}
		}

		throw new SQLException("Failed to insert row into " + uri);
	}
	

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int rowsDeleted = 0;
		if (getType(uri).equals(Files.CONTENT_TYPE)) {
			rowsDeleted += db.delete(FILES_TABLE_NAME, selection, null);
		} else if (getType(uri).equals(Folders.CONTENT_TYPE)) {
			rowsDeleted += db.delete(FOLDERS_TABLE_NAME, selection, null);
		} 
		return rowsDeleted;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
			String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		switch (sUriMatcher.match(uri)) {
		case FOLDERS:
			qb.setTables(FOLDERS_TABLE_NAME);
			qb.setProjectionMap(sFoldersProjectionMap);
			break;
		case FOLDER:
			qb.setTables(FOLDERS_TABLE_NAME);
			qb.setProjectionMap(sFoldersProjectionMap);
			qb.appendWhere(Folders.Columns._ID + "=" + uri.getPathSegments().get(1));
			break;
		case FILES:
			qb.setTables(FILES_TABLE_NAME);
			qb.setProjectionMap(sFilesProjectionMap);
			break;
		case FILE:
			qb.setTables(FILES_TABLE_NAME);
			qb.setProjectionMap(sFilesProjectionMap);
			qb.appendWhere(Files.Columns._ID + "=" + uri.getPathSegments().get(1));
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		// Get the database and run the query
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, null);

		// Tell the cursor what uri to watch, so it knows when its source data changes
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * Add a file to a shared folder
	 * 
	 * @param cr 
	 * @param file
	 * @param folder
	 * @return
	 */
	public static final Uri addFileToFolder(ContentResolver cr, Uri file, Uri folder) {
	  Log.i(TAG, "Adding file to folder " + file + " " + folder);
		/* Get file path */
		Cursor c = cr.query(file, null, null, null, null);
		int nameIndex = c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
		c.moveToFirst();
		String name = c.getString(nameIndex);

		/* Get folder id */
		int folderId = Integer.parseInt(folder.getPathSegments().get(1));

		ContentValues values = new ContentValues();
		values.put(Files.Columns._DATA, file.toString());
		values.put(Files.Columns.DISPLAY_NAME, name);
		values.put(Files.Columns.FOLDER_ID, folderId);
		return cr.insert(Files.CONTENT_URI, values);
	}

	/**
	 * Deletes folders
	 * 
	 * @param cr
	 * @param folder
	 * @return
	 */
	public static final boolean deleteFolder(ContentResolver cr, Uri folder) {
		int folderId = Integer.parseInt(folder.getPathSegments().get(1));
		cr.delete(Files.CONTENT_URI, Files.Columns.FOLDER_ID + "=" + folderId, null);
		cr.delete(Folders.CONTENT_URI, Folders.Columns._ID + "=" + folderId, null);
		return true;
	}

	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(AUTHORITY, "folders", FOLDERS);
		sUriMatcher.addURI(AUTHORITY, "folders/#", FOLDER);
		sUriMatcher.addURI(AUTHORITY, "files/#", FILE);
		sUriMatcher.addURI(AUTHORITY, "files", FILES);

		sFoldersProjectionMap = new HashMap<String, String>();
		sFoldersProjectionMap.put(Folders.Columns._ID, Folders.Columns._ID);
		sFoldersProjectionMap.put(Folders.Columns.DISPLAY_NAME, Folders.Columns.DISPLAY_NAME);
		sFoldersProjectionMap.put(Folders.Columns.PASSWORD, Folders.Columns.PASSWORD);

		sFilesProjectionMap = new HashMap<String, String>();
		sFilesProjectionMap.put(Files.Columns._ID, Files.Columns._ID);
		sFilesProjectionMap.put(Files.Columns.FOLDER_ID, Files.Columns.FOLDER_ID);
		sFilesProjectionMap.put(Files.Columns.DISPLAY_NAME, Files.Columns.DISPLAY_NAME);
		sFilesProjectionMap.put(Files.Columns._DATA, Files.Columns._DATA);
	}

}
