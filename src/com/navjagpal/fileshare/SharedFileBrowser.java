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
import android.widget.SimpleCursorAdapter;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class SharedFileBrowser extends ListActivity {

  private static final String TAG = "FileShareFilePicker";
  private static final int MENU_DELETE = 0;
  private static final int MENU_SHARE_URL = 1;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);


    int folderId = Integer.parseInt(getIntent().getData().getPathSegments().get(1));
    String where = FileSharingProvider.Files.Columns.FOLDER_ID + "=" + folderId;
    Cursor c = managedQuery(FileSharingProvider.Files.CONTENT_URI, null, where, null, null);
    changeCursor(c);

    // Inform the list we provide context menus for items
    getListView().setOnCreateContextMenuListener(this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);

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

      // Add delete option if we have something selected, and the delete option
      // isn't already there.
      if (menu.findItem(MENU_DELETE) == null && getSelectedItemId() >= 0) {
        menu.add(0, MENU_DELETE, 0, R.string.delete);
      } else {
        menu.removeItem(MENU_DELETE);
      }

      // ... is followed by whatever other actions are available...
      Intent intent = new Intent(null, uri);
      intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
      menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, null, null, intent, 0,
          null);

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
    int nameIndex = cursor.getColumnIndexOrThrow(FileSharingProvider.Files.Columns.DISPLAY_NAME);
    menu.setHeaderTitle(cursor.getString(nameIndex));

    // Add a menu item to delete the folder
    menu.add(0, MENU_DELETE, 0, R.string.delete);

    // Add a menu item to share a link to the folder.
    menu.add(0, MENU_SHARE_URL, 0, R.string.share_url);
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
        deleteSharedFile(info.id);
        return true;
      }
      case MENU_SHARE_URL: {
        Intent shareURLIntent = new Intent();
        shareURLIntent.setAction(Intent.ACTION_SEND);
        shareURLIntent.setType("text/plain");
        shareURLIntent.putExtra(Intent.EXTRA_TEXT, getShareURL(info.id));
        Intent chooserIntent = Intent.createChooser(
            shareURLIntent, getText(R.string.shareurl_title));
        startActivity(chooserIntent);
        return true;
      }
    }
    return false;
  }

  public String getShareURL(long fileId) {
    String where = FileSharingProvider.Files.Columns._ID + "=" + fileId;
    Cursor c = getContentResolver().query(
        FileSharingProvider.Files.CONTENT_URI,
        new String[] {FileSharingProvider.Files.Columns.DISPLAY_NAME},
        where, null, null);
    c.moveToFirst();
    String encodedName = "";

    // This really shouldn't happen, but if it does, we can just skip
    // the name since the web server depends on the id, not the name.
    try {
      encodedName = URLEncoder.encode(c.getString(0), "UTF8");
    } catch (UnsupportedEncodingException e) {
      Log.e(TAG, "Problem encoding display name " + c.getString(0));
    }

    return "http://" + FileShare.getIPAddress(this) + ":9999" +
    "/file/" + fileId + "/" + encodedName;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case MENU_DELETE:
        deleteSharedFile(getSelectedItemId());
        break;
    }

    return false;
  }

  private void changeCursor(Cursor cursor) {
    ListAdapter adapter = new SimpleCursorAdapter(this,
        android.R.layout.simple_list_item_1, cursor,
        new String[] { FileSharingProvider.Files.Columns.DISPLAY_NAME },
        new int[] { android.R.id.text1 });
    setListAdapter(adapter);
  }

  private void deleteSharedFile(long fileId) {
    String where = FileSharingProvider.Files.Columns._ID + "=" + fileId;
    getContentResolver().delete(FileSharingProvider.Files.CONTENT_URI, where, null);
    getContentResolver().notifyChange(FileSharingProvider.Files.CONTENT_URI, null);
  }

}
