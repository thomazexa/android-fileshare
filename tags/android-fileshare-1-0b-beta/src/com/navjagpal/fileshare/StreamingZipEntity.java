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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import org.apache.http.entity.AbstractHttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * HttpEntity that streams the contents of a shared folder as a ZIP file.
 * 
 * @author nav@gmail.com (Nav Jagpal)
 */
public class StreamingZipEntity extends AbstractHttpEntity {

  private ContentResolver mContentResolver;
  private String mFolderId;
  private boolean mFinished;
  private static final int BUFFER_SIZE = 1024;
  
  public StreamingZipEntity(ContentResolver contentResolver, String folderId) {
    mContentResolver = contentResolver;
    mFolderId = folderId;
    mFinished = false;
  }

  public InputStream getContent() throws IOException, IllegalStateException {
    PipedInputStream in = new PipedInputStream();
    final PipedOutputStream out = new PipedOutputStream(in);
    new Thread(
      new Runnable() {
        public void run() {
          try {
            writeTo(out);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    ).start();
    return in;
  }

  public long getContentLength() {
    return -1;
  }

  public boolean isRepeatable() {
    return false;
  }

  public boolean isStreaming() {
    return mFinished;
  }
  
  @Override
  public void consumeContent() {
    mFinished = true;
  }

  public void writeTo(OutputStream out) throws IOException {
    Cursor c = mContentResolver.query(
        FileSharingProvider.Files.CONTENT_URI,
        new String[] {
            FileSharingProvider.Files.Columns.DISPLAY_NAME,
            FileSharingProvider.Files.Columns._DATA
        },
        FileSharingProvider.Files.Columns.FOLDER_ID + "=?",
        new String[] {mFolderId}, null);
    ZipOutputStream zipOut = new ZipOutputStream(out);
    byte[] buf = new byte[BUFFER_SIZE];
    while (c.moveToNext()) {
      String filename = c.getString(
          c.getColumnIndex(FileSharingProvider.Files.Columns.DISPLAY_NAME));
      String data = c.getString(
          c.getColumnIndex(FileSharingProvider.Files.Columns._DATA));
      zipOut.putNextEntry(new ZipEntry(filename));
      InputStream input = mContentResolver.openInputStream(Uri.parse(data));
      int len;
      while ((len = input.read(buf)) > 0) {
        zipOut.write(buf, 0, len);
      }
      zipOut.closeEntry();
      input.close();
    }
    zipOut.finish();
    mFinished = true;
  }
}
