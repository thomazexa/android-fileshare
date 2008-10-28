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

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Deletes Shared Folders
 * 
 * @author Nav Jagpal (nav@gmail.com)
 */
public class SharedFolderDeleter extends Activity {

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		setContentView(R.layout.deletefolder);

		/* Populate UI elements with folder information */
		Uri folderUri = getIntent().getData();
		Cursor c = managedQuery(folderUri, null, null, null, null);
		c.moveToFirst();

		/* Get the name and the id */
		int nameIndex = c.getColumnIndexOrThrow(FileSharingProvider.Folders.Columns.DISPLAY_NAME);
		int folderIdIndex = c.getColumnIndexOrThrow(FileSharingProvider.Folders.Columns._ID);
		String name = c.getString(nameIndex);
		int folderId = c.getInt(folderIdIndex);

		/* Find out how many files we'll be removing from the folder */
		String where = FileSharingProvider.Files.Columns.FOLDER_ID + "=" + folderId;
		c = managedQuery(FileSharingProvider.Files.CONTENT_URI, null, where, null, null);
		int num_files = c.getCount();

		TextView nameText = (TextView) findViewById(R.id.foldername);
		nameText.setText(name);

		TextView sizeText = (TextView) findViewById(R.id.foldersize);
		sizeText.setText("" + num_files);

		/* Simple cancel button */
		Button cancelButton = (Button) findViewById(R.id.cancel_button);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});

		/* Delete button */
		Button deleteButton = (Button) findViewById(R.id.ok_button);
		deleteButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				FileSharingProvider.deleteFolder(getContentResolver(), getIntent().getData());
				setResult(RESULT_OK);
				finish();
			}
		});
	}
}
