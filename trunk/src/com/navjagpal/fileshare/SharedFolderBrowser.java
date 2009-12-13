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

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;


public class SharedFolderBrowser extends ListActivity {

	private static final String TAG = "FileShareBrowser";

	private static final int MENU_DELETE = 0;
	private static final int MENU_CREATE = 1;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		/* If no data is supplied, get default from the FileProvider */
		if (getIntent().getData() == null) {
			getIntent().setData(FileSharingProvider.Folders.CONTENT_URI);
		}

		Cursor c = managedQuery(getIntent().getData(), null, null, null, null);
		changeCursor(c);
		
		// Inform the list we provide context menus for items
        getListView().setOnCreateContextMenuListener(this);
	}


	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		

		if (getIntent().getAction().equals(Intent.ACTION_PICK))  {
			Uri uri = Uri.withAppendedPath(FileSharingProvider.Folders.CONTENT_URI, "" + id);
			Log.d(TAG, "Uri for folder = " + uri.toString());
			setResult(RESULT_OK, new Intent().setData(uri));
			finish();
		} else {
			/* show files in the folder */
			Uri uri = Uri.withAppendedPath(FileSharingProvider.Folders.CONTENT_URI, "" + id);
			Intent intent = new Intent();
			intent.setData(uri);
			intent.setAction(Intent.ACTION_VIEW);
			startActivity(intent);
		}

		
	}

	private void changeCursor(Cursor cursor) {
		ListAdapter adapter = new SimpleCursorAdapter(this,
				android.R.layout.simple_list_item_1, cursor,
				new String[] { FileSharingProvider.Folders.Columns.DISPLAY_NAME },
				new int[] { android.R.id.text1 });
		setListAdapter(adapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		/* Always give option to create */
		menu.add(0, MENU_CREATE, 0, getText(R.string.create));
        
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		final boolean haveItems = getListAdapter().getCount() > 0;

		// If there are any notes in the list (which implies that one of
		// them is selected), then we need to generate the actions that
		// can be performed on the current selection.  This will be a combination
		// of our own specific actions along with any extensions that can be
		// found.
		if (haveItems) {
			// This is the selected item.
			Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

			// Build menu...  always starts with the DELETE action...
			Intent[] specifics = new Intent[1];
			specifics[0] = new Intent(Intent.ACTION_DELETE, uri);
			MenuItem[] items = new MenuItem[1];

			// ... is followed by whatever other actions are available...
			Intent intent = new Intent(null, uri);
			intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
			menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, null, specifics, intent, 0,
					items);
			
		} else {
			menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
		}

		return true;
	}

	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuInfo", e);
			return;
		}

		Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
		if (cursor == null) {
			Log.i(TAG, "Item not available");
			// For some reason the requested item isn't available, do nothing
			return;
		}

		// Setup the menu header
		int nameIndex = cursor.getColumnIndexOrThrow(FileSharingProvider.Folders.Columns.DISPLAY_NAME);
		menu.setHeaderTitle(cursor.getString(nameIndex));

		// Add a menu item to delete the folder
		menu.add(0, MENU_DELETE, 0, R.string.delete);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuInfo", e);
			return false;
		}

		switch (item.getItemId()) {
		case MENU_DELETE: {
			// Delete the note that the context menu is for
			Intent deleteIntent = new Intent();
			deleteIntent.setAction(Intent.ACTION_DELETE);
			deleteIntent.setData(Uri.withAppendedPath(FileSharingProvider.Folders.CONTENT_URI, "" + info.id));
			startActivity(deleteIntent);
			return true;
		}
		}
		return false;
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_DELETE:
			Intent deleteIntent = new Intent();
			deleteIntent.setAction(Intent.ACTION_DELETE);
			deleteIntent.setData(Uri.withAppendedPath(FileSharingProvider.Folders.CONTENT_URI, "" + getSelectedItemId()));
			startActivity(deleteIntent);
			break;
		case MENU_CREATE:
			Intent createIntent = new Intent();
			createIntent.setAction(Intent.ACTION_INSERT);
			createIntent.setType(FileSharingProvider.Folders.CONTENT_TYPE);
			startActivity(createIntent);
			break;
		}

		return false;
	}


}
