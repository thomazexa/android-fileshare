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

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

public class WebServer {
	
	private static final String TAG = "FileSharer WebServer";
	
	/* Buffer size of 128 */
	private static final int REQUEST_BUFFER_SIZE = 1024 * 128;
	
	/* Buffer size of 1MB for data transfers */
	private static final int TRANSFER_BUFFER_SIZE = 1024 * 1024;
	
	/* Allocate the buffer once and share it with all threads.
	 * This seems sort of crazy, but allocating and freeing memory all the time 
	 * makes the android really laggy. Should consider changing this to having several shared buffers?
	 */
	private final byte[] REQUEST_BUFFER = new byte[REQUEST_BUFFER_SIZE];
	
	private int mPort;
	
	private ServerSocket mServerSocket;
	
	private ContentResolver mContentResolver;
	
	public interface TransferStartedListener {
		public void started(Uri uri);
	};
	
	private TransferStartedListener mTransferStartedListener;
	

	/* Start the webserver on specified port */
	public WebServer(ContentResolver contentResolver, int port) throws IOException {
		mPort = port;
		mServerSocket = new ServerSocket(mPort);
		mServerSocket.setReuseAddress(true);
		mContentResolver = contentResolver;
	}
	
	/* Returns port we're using */
	public int getPort() {
		return mPort;
	}
	
	public void setOnTransferStartedListener(TransferStartedListener listener) {
		mTransferStartedListener = listener;
	}
	
	public void runWebServer() {
		while (true) {
			Log.i(TAG, "Running main webserver thread");
			try {
				final Socket socket = mServerSocket.accept();
				Log.d(TAG, "Socket accepted");
			    Thread t = new Thread() {
					public void run() {
						handleRequest(socket);
					}
				};
				t.start();
			} catch (IOException e) {
				Log.e(TAG, "Problem accepting socket " + e.toString());
			}
		}
	}
	
	public void handleRequest(Socket socket) {
		try {
			OutputStream output = socket.getOutputStream();
			InputStream input = socket.getInputStream();
			
			String req = null;
			synchronized (REQUEST_BUFFER) {
				input.read(REQUEST_BUFFER);
				req = new String(REQUEST_BUFFER);
			}
			
			String firstline = req.toUpperCase();
			if (firstline.startsWith("GET / ")) {
				Log.i(TAG, "Sending shared folder listing");
				writeFolderListing(output);
			} else if (firstline.startsWith("GET /FOLDER")) {
				Log.i(TAG, "Sending list of shared files");
				String folderId = getFolderId(firstline);
				writeFileListing(Uri.withAppendedPath(FileSharingProvider.Folders.CONTENT_URI, folderId), output);
			} else if (firstline.startsWith("GET /FILE")) {
				Log.i(TAG, "Sending file content");
				String fileId = getFileId(firstline);
				writeFileContent(Uri.withAppendedPath(FileSharingProvider.Files.CONTENT_URI, fileId), output);
			}
			output.flush();
			socket.close();
		} catch (IOException e) {
			Log.e(TAG, "Problem with socket " + e.toString());
		}
	}
	
	public void writeFolderListing(OutputStream output) throws IOException {
		/* Get list of folders */
		Cursor c = mContentResolver.query(FileSharingProvider.Folders.CONTENT_URI, null, null, null, null);
		int nameIndex = c.getColumnIndexOrThrow(FileSharingProvider.Folders.Columns.DISPLAY_NAME);
		int idIndex = c.getColumnIndexOrThrow(FileSharingProvider.Folders.Columns._ID);
		while (c.moveToNext()) {
			String name = c.getString(nameIndex);
			int id = c.getInt(idIndex);
			output.write(folderToLink(name, id).getBytes());
			output.write("<br/>".getBytes());
		}	
	}
	
	public String getFolderId(String firstline) {
		Pattern p = Pattern.compile("GET /FOLDER/(\\d+)");
		Matcher m = p.matcher(firstline);
		boolean b = m.find(0);
		if (b) {
			return m.group(1);
		}
		return null;
	}
	
	public String getFileId(String firstline) {
		Pattern p = Pattern.compile("GET /FILE/(\\d+)");
		Matcher m = p.matcher(firstline);
		boolean b = m.find(0);
		if (b) {
			return m.group(1);
		}
		return null;
	}
	
	public void writeFileListing(Uri uri, OutputStream output) throws IOException {
		int folderId = Integer.parseInt(uri.getPathSegments().get(1));
		Uri fileUri = FileSharingProvider.Files.CONTENT_URI;
		String where = FileSharingProvider.Files.Columns.FOLDER_ID + "=" + folderId;
		Cursor c = mContentResolver.query(fileUri, null, where, null, null);
		int nameIndex = c.getColumnIndexOrThrow(FileSharingProvider.Files.Columns.DISPLAY_NAME);
		int idIndex = c.getColumnIndexOrThrow(FileSharingProvider.Files.Columns._ID);
		while (c.moveToNext()) {
			String name = c.getString(nameIndex);
			int id = c.getInt(idIndex);
			output.write(fileToLink(name, id).getBytes());
			output.write("<br/>".getBytes());
		}
	}
	
	private void writeFileContent(final Uri uri, final OutputStream output)
	throws IOException {
		
		if (mTransferStartedListener != null) {
			mTransferStartedListener.started(uri);
		}
	
		Cursor c = mContentResolver.query(uri, null, null, null, null);
		c.moveToFirst();
		int nameIndex = c.getColumnIndexOrThrow(FileSharingProvider.Files.Columns.DISPLAY_NAME);
		String name = c.getString(nameIndex);
		int dataIndex = c.getColumnIndexOrThrow(FileSharingProvider.Files.Columns._DATA);
		Uri data = Uri.parse(c.getString(dataIndex));

		c = mContentResolver.query(data, null, null, null, null);
		c.moveToFirst();
		int sizeIndex = c.getColumnIndexOrThrow(OpenableColumns.SIZE);
		int sizeBytes = c.getInt(sizeIndex);


		InputStream input = mContentResolver.openInputStream(data);

		String contentType = "application/octet-stream";
		if (name.endsWith(".jpg")) {
			contentType= "image/jpg";
		}


		String headers = getHeaders(contentType, sizeBytes);
		output.write(headers.getBytes());
		byte[] byteArray = new byte[TRANSFER_BUFFER_SIZE];
		int bytesRead = input.read(byteArray);
		int bytesWritten = 0;
		while (bytesRead != -1) {
			output.write(byteArray, 0, bytesRead);
			bytesWritten += bytesRead;
			bytesRead = input.read(byteArray);
		}

	}
	
	private String folderToLink(String folderName, int folderId) {
		return "<a href=\"/folder/" + folderId + "\">" + folderName + "</a>";
	}
	
	private String fileToLink(String fileName, int fileId) {
		return "<a href=\"/file/" + fileId + "/" + fileName + "\">" + fileName + "</a>";
	}
	
	private String getHeaders(String contentType, int lengthBytes) {
		String headers = new String(
				"HTTP/1.1 200 OK\r\n" +
		        "Content-Type: " + contentType + "\r\n" +
				"Content-Length: " + lengthBytes + "\r\n\r\n");
		Log.i(TAG, "Headers = " + headers);
		return headers;
	}
	
}
