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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.database.SQLException;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * Creates Shared Folders
 * 
 * @author Nav Jagpal (nav@gmail.com)
 */
public class SharedFolderCreator extends Activity {

  private static final int DIALOG_ERROR = 0;
  
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    setContentView(R.layout.createfolder);

    /* Simple cancel button */
    Button cancelButton = (Button) findViewById(R.id.cancel_button);
    cancelButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        setResult(RESULT_CANCELED);
        finish();
      }
    });

    /* Grab name from the UI views and insert using the provder */
    Button createButton = (Button) findViewById(R.id.ok_button);
    createButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        /* Get params */
        EditText editText = (EditText) findViewById(R.id.foldername);
        String folderName = editText.getText().toString();

        /* Do the insert */
        ContentValues values = new ContentValues();
        values.put(FileSharingProvider.Folders.Columns.DISPLAY_NAME, folderName);
        try {
        getContentResolver().insert(
            FileSharingProvider.Folders.CONTENT_URI, values);
        } catch (SQLException e) {
          showDialog(DIALOG_ERROR);
          return;
        }
        setResult(RESULT_OK);
        finish();
      }
    });
  }
  
  @Override
  protected Dialog onCreateDialog(int id) {
    Dialog dialog = null;
    switch (id) {
      case DIALOG_ERROR:
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.error_creating_folder)
          .setTitle(R.string.error_title);
        dialog = builder.create();
        break;
    }
    return dialog;
  }

}
