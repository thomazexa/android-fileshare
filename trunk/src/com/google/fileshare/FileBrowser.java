//   Copyright 2008 Google Inc.
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

import java.util.Stack;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;


/**
 * Browser and picker for files on device.
 * 
 * @author Nav Jagpal (nav@gmail.com)
 */
public class FileBrowser extends ListActivity {
		
	/* Keep the paths so we can go back */
	private Stack<String> mPaths = new Stack<String>();
	
	private static final int EMPTY_DIRECTORY_DIALOG = 0;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		/* If no data is supplied, get default from the FileProvider */
		if (getIntent().getData() == null) {
			getIntent().setData(FileProvider.CONTENT_URI);
		}
		
		String currentPath = FileProvider.getPath(getIntent().getData());
		
		/* Special case for "/". Wouldn't need this is we have a class to do path joining for us */
		if (currentPath.equals("/"))
			currentPath = "";
		mPaths.push(currentPath);
		
		/* Populate list with the root content */
		Cursor c = managedQuery(getIntent().getData(), null, null, null, null);
		changeCursor(c);
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		TextView textView = (TextView) v;
		
		/* Path will contain the directory or file the user actually wants to see */
		String path = "";
		String fileName = (String) textView.getText();
		
		/* This may be a ".." signal to go back */
		if (fileName.equals("..")) {
			path = mPaths.pop();
			if (mPaths.size() > 0)
				path = mPaths.pop();
			fileName = "";
		} else {
			/* Normal processing for non ".." names */
			path = mPaths.peek() + "/" + fileName;
		}
		
		/* Special processing for "/" */
		if (path.equals("/"))
			path = "";
	
		/* Create a content uri using the path. Normally this is a numeric id, but the file provider
		 * is different.
		 */
		Uri uri = Uri.withAppendedPath(getIntent().getData(), path);
		if (getContentResolver().getType(uri).equals(FileProvider.CONTENT_ITEM_TYPE)) {
			/* User has selected a file */
			setResult(RESULT_OK, new Intent().setData(uri));
			finish();
		} else {
			/* A directory */
			Cursor c = managedQuery(uri, null, null, null, null);
			
			/* No files, show dialog */
			if (c.getCount() == 0) {
				this.showDialog(EMPTY_DIRECTORY_DIALOG);
			} else {
				/* We have some files. Add current path to stack so we can get back
				 * Add ".." if we're not looking at the root 
				 */
				mPaths.push(path);
				if (mPaths.size() > 1) {
					MatrixCursor matrixCursor = new MatrixCursor(new String[] {"_id", OpenableColumns.DISPLAY_NAME});
					matrixCursor.addRow(new Object[] {-1, ".."});
					c = new MergeCursor(new Cursor[] {matrixCursor, c});
				}
				changeCursor(c);
			}
		}
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case EMPTY_DIRECTORY_DIALOG:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setCancelable(true).setMessage(R.string.empty_directory);
			return builder.create();
		}
		return null;
	}
	
	private void changeCursor(Cursor cursor) {
		ListAdapter adapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_list_item_1, cursor,
				new String[] { OpenableColumns.DISPLAY_NAME },
				new int[] { android.R.id.text1 });
		setListAdapter(adapter);
	}

}
