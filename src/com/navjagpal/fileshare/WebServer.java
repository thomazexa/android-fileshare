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

import org.apache.commons.fileupload.MultipartStream;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.RequestLine;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.BasicHttpParams;

import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

public class WebServer {

  private static final String TAG = "FileSharer WebServer";

  private int mPort;

  private ServerSocket mServerSocket;

  private ContentResolver mContentResolver;
  
  public interface TransferStartedListener {
    public void started(Uri uri);
  }

  private TransferStartedListener mTransferStartedListener;

  /* Start the webserver on specified port */
  public WebServer(ContentResolver contentResolver, int port)
    throws IOException {
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
          @Override
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

  /* Handles a single request. */
  public void handleRequest(Socket socket) {
    try {
      DefaultHttpServerConnection serverConnection = 
        new DefaultHttpServerConnection();
      serverConnection.bind(socket, new BasicHttpParams());
      HttpRequest request = serverConnection.receiveRequestHeader();
      RequestLine requestLine = request.getRequestLine();
      
      if (requestLine.getUri().equals("/")) {
        Log.i(TAG, "Sending shared folder listing");
        HttpResponse response = new BasicHttpResponse(
            new HttpVersion(1, 1), 200, "OK");
        String listing = writeFolderListing();
        response.setEntity(new StringEntity(listing));
        serverConnection.sendResponseHeader(response);
        serverConnection.sendResponseEntity(response);
      } else if (requestLine.getMethod().equals("GET") &&
          requestLine.getUri().startsWith("/folder")) {
        Log.i(TAG, "Sending list of shared files");
        HttpResponse response = new BasicHttpResponse(
            new HttpVersion(1, 1), 200, "OK");
        String folderId = getFolderId(requestLine.getUri());
        String header = writeHTMLHeader();
        String form = writeUploadForm(folderId);
        String footer = writeHTMLFooter();
        String listing = writeFileListing(
            Uri.withAppendedPath(
            FileSharingProvider.Folders.CONTENT_URI, folderId));
        response.setEntity(new StringEntity(
            header + listing + form + footer));
        serverConnection.sendResponseHeader(response);
        serverConnection.sendResponseEntity(response);
      } else if (requestLine.getUri().startsWith("/file")) {
        Log.i(TAG, "Sending file content");
        HttpResponse response = new BasicHttpResponse(
            new HttpVersion(1, 1), 200, "OK");
        String fileId = getFileId(requestLine.getUri());
        getFileEntity(
            Uri.withAppendedPath(FileSharingProvider.Files.CONTENT_URI,
                fileId),
                response);
        serverConnection.sendResponseHeader(response);
        serverConnection.sendResponseEntity(response); 
      } else if (requestLine.getMethod().equals("POST")) {
        Log.i(TAG, "User is uploading file");
        HttpResponse response = new BasicHttpResponse(
            new HttpVersion(1, 1), 200, "OK");
        String folderId = getFolderId(requestLine.getUri());
        processUpload(folderId, request, serverConnection);
        String header = writeHTMLHeader();
        String form = writeUploadForm(folderId);
        String footer = writeHTMLFooter();
        String listing = writeFileListing(
            Uri.withAppendedPath(
            FileSharingProvider.Folders.CONTENT_URI, folderId));
        response.setEntity(new StringEntity(
            header + listing + form + footer));
        serverConnection.sendResponseHeader(response);
        serverConnection.sendResponseEntity(response);
      } else {
        Log.i(TAG, "No action for " + requestLine.getUri());
        HttpResponse response = new BasicHttpResponse(
            new HttpVersion(1, 1), 404, "NOT FOUND");
        response.setEntity(new StringEntity("NOT FOUND"));
        serverConnection.sendResponseHeader(response);
        serverConnection.sendResponseEntity(response);
      }
      serverConnection.flush();
      serverConnection.close();
    } catch (IOException e) {
      Log.e(TAG, "Problem with socket " + e.toString());
    } catch (HttpException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  @SuppressWarnings("deprecation")
  public void processUpload(
      String folderId,
      HttpRequest request,
      DefaultHttpServerConnection serverConnection)
  throws IOException, HttpException {
    
    String contentType = request.getFirstHeader("Content-Type").getValue();
    String boundary = contentType.substring(
        contentType.indexOf("boundary=") + "boundary=".length());
    long sizeBytes = Long.parseLong(
        request.getFirstHeader("Content-Length").getValue());
    BasicHttpEntityEnclosingRequest enclosingRequest =
      new BasicHttpEntityEnclosingRequest(request.getRequestLine());
      serverConnection.receiveRequestEntity(enclosingRequest);
    
    InputStream input = enclosingRequest.getEntity().getContent();
    Log.i(TAG, "Writing file");
  
    Log.i(TAG, "Boundary = '" + boundary + "'");
    MultipartStream multipartStream = new MultipartStream(
        input, boundary.getBytes());
    String headers = multipartStream.readHeaders();
    Log.i(TAG, "Headers = " + headers);
    
    StringTokenizer tokens = new StringTokenizer(
        headers, "; ", false);
    String filename = null;
    while (tokens.hasMoreTokens() && filename == null) {
      String token = tokens.nextToken();
      Log.i(TAG, "Token = " + token);
      if (token.startsWith("filename=")) {
        filename = URLDecoder.decode(token.substring(
            "filename=\"".length(), token.lastIndexOf("\"")), "utf8");
      }
    }
    Log.i(TAG, "Filename = '" + filename + "'");
    
    File uploadDirectory = new File("/sdcard/fileshare/uploads");
    if (!uploadDirectory.exists()) {
      uploadDirectory.mkdirs();
    }
    
    File uploadFile = new File(uploadDirectory, filename);
    FileOutputStream output = new FileOutputStream(uploadFile);

    multipartStream.readBodyData(output);
 
    output.close(); 
    Log.i(TAG, "Done writing file");
    
    Uri fileUri = Uri.withAppendedPath(
        FileProvider.CONTENT_URI,
        uploadFile.getAbsolutePath());
    Uri folderUri = Uri.withAppendedPath(
        FileSharingProvider.Folders.CONTENT_URI, folderId);
    FileSharingProvider.addFileToFolder(
        mContentResolver, fileUri, folderUri);
  }
  
  public String writeHTMLHeader() {
    return "<html><head><title>File Share</title></head><body>";
  }

  public String writeHTMLFooter() {
    return "</body></html>";
  }

  public String writeFolderListing() {
    /* Get list of folders */
    Cursor c = mContentResolver.query(FileSharingProvider.Folders.CONTENT_URI, null, null, null, null);
    int nameIndex = c.getColumnIndexOrThrow(FileSharingProvider.Folders.Columns.DISPLAY_NAME);
    int idIndex = c.getColumnIndexOrThrow(FileSharingProvider.Folders.Columns._ID);
    String s = "";
    while (c.moveToNext()) {
      String name = c.getString(nameIndex);
      int id = c.getInt(idIndex);
      s += folderToLink(name, id) + "<br/>";
    }	
    return s;
  }

  /**
   * Writes a form that allows users to upload files.
   */
  public String writeUploadForm(String folderId) {
    return
      "<form method=\"POST\" action=\"/folder/" + folderId + "\" " + 
      "enctype=\"multipart/form-data\"> " +
      "<input type=\"file\" name=\"file\" size=\"40\"/> " +
      "<input type=\"submit\" value=\"Upload\"/>";
  }

  public String getFolderId(String firstline) {
    Pattern p = Pattern.compile("/folder/(\\d+)");
    Matcher m = p.matcher(firstline);
    boolean b = m.find(0);
    if (b) {
      return m.group(1);
    }
    return null;
  }

  public String getFileId(String firstline) {
    Pattern p = Pattern.compile("/file/(\\d+)");
    Matcher m = p.matcher(firstline);
    boolean b = m.find(0);
    if (b) {
      return m.group(1);
    }
    return null;
  }

  public String writeFileListing(Uri uri) {
    int folderId = Integer.parseInt(uri.getPathSegments().get(1));
    Uri fileUri = FileSharingProvider.Files.CONTENT_URI;
    String where = FileSharingProvider.Files.Columns.FOLDER_ID + "=" + folderId;
    Cursor c = mContentResolver.query(fileUri, null, where, null, null);
    int nameIndex = c.getColumnIndexOrThrow(FileSharingProvider.Files.Columns.DISPLAY_NAME);
    int idIndex = c.getColumnIndexOrThrow(FileSharingProvider.Files.Columns._ID);
    String s = "";
    while (c.moveToNext()) {
      String name = c.getString(nameIndex);
      int id = c.getInt(idIndex);
      s += fileToLink(name, id) + "<br/>";
    }
    return s;
  }
  
  private void getFileEntity(final Uri uri, HttpResponse response) throws IOException {

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

    response.addHeader("Content-Type", contentType);
    response.addHeader("Content-Length", "" + sizeBytes);
    response.setEntity(new InputStreamEntity(
        input, sizeBytes));
  }

  
  private String folderToLink(String folderName, int folderId) {
    return "<a href=\"/folder/" + folderId + "\">" + folderName + "</a>";
  }

  private String fileToLink(String fileName, int fileId) {
    return "<a href=\"/file/" + fileId + "/" + fileName + "\">" + fileName + "</a>";
  }
}
