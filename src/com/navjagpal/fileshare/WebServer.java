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
import org.apache.http.Header;
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

import java.util.Random;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

public class WebServer {

  private static final String TAG = "FileSharer WebServer";

  private int mPort;

  private ServerSocket mServerSocket;

  private ContentResolver mContentResolver;

  private SharedPreferences mSharedPreferences;

  private SQLiteDatabase mCookiesDatabase;

  public interface TransferStartedListener {
    public void started(Uri uri);
  }

  private TransferStartedListener mTransferStartedListener;

  /* How long we allow session cookies to last. */
  private static final int COOKIE_EXPIRY_SECONDS = 3600;

  /* Start the webserver on specified port */
  public WebServer(ContentResolver contentResolver,
      SharedPreferences sharedPreferences, SQLiteDatabase cookiesDatabase,
      int port) throws IOException {
    mPort = port;
    mServerSocket = new ServerSocket(mPort);
    mServerSocket.setReuseAddress(true);
    mContentResolver = contentResolver;
    mSharedPreferences = sharedPreferences;
    mCookiesDatabase = cookiesDatabase;
    deleteOldCookies();
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
      DefaultHttpServerConnection serverConnection = new DefaultHttpServerConnection();
      serverConnection.bind(socket, new BasicHttpParams());
      HttpRequest request = serverConnection.receiveRequestHeader();
      RequestLine requestLine = request.getRequestLine();

      /* First make sure user is logged in if that is required. */
      boolean loggedIn = false;
      if (mSharedPreferences.getBoolean(FileSharingService.PREFS_REQUIRE_LOGIN,
          false)) {
        /* Does the user have a valid cookie? */
        Header cookiesHeader = request.getFirstHeader("Cookie");
        if (cookiesHeader != null) {
          String cookies = cookiesHeader.getValue();
          String cookie = cookies.substring(cookies.indexOf("id=")
              + "id=".length());
          loggedIn = isValidCookie(cookie);
        }
      } else {
        loggedIn = true;
      }

      if (!loggedIn) {
        /* Could be the result of the login form. */
        if (requestLine.getUri().equals("/login")) {
          handleLoginRequest(serverConnection, request, requestLine);
        } else {
          sendLoginForm(serverConnection, requestLine);
        }
      } else if (requestLine.getUri().equals("/")) {
        Log.i(TAG, "Sending shared folder listing");
        sendSharedFolderListing(serverConnection);
      } else if (requestLine.getMethod().equals("GET")
          && requestLine.getUri().startsWith("/folder")) {
        Log.i(TAG, "Sending list of shared files");
        sendSharedFilesList(serverConnection, requestLine);
      } else if (requestLine.getUri().startsWith("/file")) {
        Log.i(TAG, "Sending file content");
        sendFileContent(serverConnection, requestLine);
      } else if (requestLine.getMethod().equals("POST")) {
        Log.i(TAG, "User is uploading file");
        handleUploadRequest(serverConnection, request, requestLine);
      } else {
        Log.i(TAG, "No action for " + requestLine.getUri());
        sendNotFound(serverConnection);
      }
      serverConnection.flush();
      serverConnection.close();
    } catch (IOException e) {
      Log.e(TAG, "Problem with socket " + e.toString());
    } catch (HttpException e) {
      Log.e(TAG, "Problemw with HTTP server " + e.toString());
    }
  }

  private void handleLoginRequest(DefaultHttpServerConnection serverConnection,
      HttpRequest request, RequestLine requestLine) throws HttpException,
      IOException {

    BasicHttpEntityEnclosingRequest enclosingRequest = new BasicHttpEntityEnclosingRequest(
        request.getRequestLine());
    serverConnection.receiveRequestEntity(enclosingRequest);

    InputStream input = enclosingRequest.getEntity().getContent();
    InputStreamReader reader = new InputStreamReader(input);

    StringBuffer form = new StringBuffer();
    while (reader.ready()) {
      form.append((char) reader.read());
    }
    String password = form.substring(form.indexOf("=") + 1);
    if (password.equals(mSharedPreferences.getString(
        FileSharingService.PREFS_PASSWORD, ""))) {
      HttpResponse response = new BasicHttpResponse(new HttpVersion(1, 1), 302,
          "Found");
      response.addHeader("Location", "/");
      response.addHeader("Set-Cookie", "id=" + createCookie());
      response.setEntity(new StringEntity(getHTMLHeader() + "Success!"
          + getHTMLFooter()));
      serverConnection.sendResponseHeader(response);
      serverConnection.sendResponseEntity(response);
    } else {
      HttpResponse response = new BasicHttpResponse(new HttpVersion(1, 1), 401,
          "Unauthorized");
      response.setEntity(new StringEntity(getHTMLHeader()
          + "<p>Login failed.</p>" + getLoginForm() + getHTMLFooter()));
      serverConnection.sendResponseHeader(response);
      serverConnection.sendResponseEntity(response);
    }
  }

  private String createCookie() {
    Random r = new Random();
    String value = Long.toString(Math.abs(r.nextLong()), 36);
    ContentValues values = new ContentValues();
    values.put("name", "id");
    values.put("value", value);
    values.put("expiry", (int) System.currentTimeMillis() / 1000
        + COOKIE_EXPIRY_SECONDS);
    mCookiesDatabase.insert("cookies", "name", values);
    return value;
  }

  private boolean isValidCookie(String cookie) {
    Cursor cursor = mCookiesDatabase.query("cookies", new String[] { "value" },
        "name = ? and value = ? and expiry > ?", new String[] { "id", cookie,
            "" + (int) System.currentTimeMillis() / 1000 }, null, null, null);
    boolean isValid = cursor.getCount() > 0;
    cursor.close();
    return isValid;
  }

  private void deleteOldCookies() {
    mCookiesDatabase.delete("cookies", "expiry < ?", new String[] { ""
        + (int) System.currentTimeMillis() / 1000 });
  }

  private void sendNotFound(DefaultHttpServerConnection serverConnection)
      throws UnsupportedEncodingException, HttpException, IOException {
    HttpResponse response = new BasicHttpResponse(new HttpVersion(1, 1), 404,
        "NOT FOUND");
    response.setEntity(new StringEntity("NOT FOUND"));
    serverConnection.sendResponseHeader(response);
    serverConnection.sendResponseEntity(response);
  }

  private void handleUploadRequest(
      DefaultHttpServerConnection serverConnection, HttpRequest request,
      RequestLine requestLine) throws IOException, HttpException,
      UnsupportedEncodingException {
    HttpResponse response = new BasicHttpResponse(new HttpVersion(1, 1), 200,
        "OK");
    String folderId = getFolderId(requestLine.getUri());
    processUpload(folderId, request, serverConnection);
    String header = getHTMLHeader();
    String form = getUploadForm(folderId);
    String footer = getHTMLFooter();
    String listing = getFileListing(Uri.withAppendedPath(
        FileSharingProvider.Folders.CONTENT_URI, folderId));
    response.setEntity(new StringEntity(header + listing + form + footer));
    serverConnection.sendResponseHeader(response);
    serverConnection.sendResponseEntity(response);
  }

  private void sendFileContent(DefaultHttpServerConnection serverConnection,
      RequestLine requestLine) throws IOException, HttpException {
    HttpResponse response = new BasicHttpResponse(new HttpVersion(1, 1), 200,
        "OK");
    String fileId = getFileId(requestLine.getUri());
    addFileEntity(Uri.withAppendedPath(FileSharingProvider.Files.CONTENT_URI,
        fileId), response);
    serverConnection.sendResponseHeader(response);
    serverConnection.sendResponseEntity(response);
  }

  private void sendSharedFilesList(
      DefaultHttpServerConnection serverConnection, RequestLine requestLine)
      throws UnsupportedEncodingException, HttpException, IOException {
    HttpResponse response = new BasicHttpResponse(new HttpVersion(1, 1), 200,
        "OK");
    String folderId = getFolderId(requestLine.getUri());
    String header = getHTMLHeader();
    String form = getUploadForm(folderId);
    String footer = getHTMLFooter();
    String listing = getFileListing(Uri.withAppendedPath(
        FileSharingProvider.Folders.CONTENT_URI, folderId));
    response.setEntity(new StringEntity(header + listing + form + footer));
    serverConnection.sendResponseHeader(response);
    serverConnection.sendResponseEntity(response);
  }

  private void sendLoginForm(DefaultHttpServerConnection serverConnection,
      RequestLine requestLine) throws UnsupportedEncodingException,
      HttpException, IOException {
    HttpResponse response = new BasicHttpResponse(new HttpVersion(1, 1), 200,
        "OK");
    response.setEntity(new StringEntity(getHTMLHeader()
        + "<p>Password Required</p>" + getLoginForm() + getHTMLFooter()));
    serverConnection.sendResponseHeader(response);
    serverConnection.sendResponseEntity(response);
  }

  private void sendSharedFolderListing(
      DefaultHttpServerConnection serverConnection)
      throws UnsupportedEncodingException, HttpException, IOException {
    HttpResponse response = new BasicHttpResponse(new HttpVersion(1, 1), 200,
        "OK");
    response.setEntity(new StringEntity(getHTMLHeader() + getFolderListing()
        + getHTMLFooter()));
    serverConnection.sendResponseHeader(response);
    serverConnection.sendResponseEntity(response);
  }

  @SuppressWarnings("deprecation")
  public void processUpload(String folderId, HttpRequest request,
      DefaultHttpServerConnection serverConnection) throws IOException,
      HttpException {

    /* Find the boundary and the content length. */
    String contentType = request.getFirstHeader("Content-Type").getValue();
    String boundary = contentType.substring(contentType.indexOf("boundary=")
        + "boundary=".length());
    BasicHttpEntityEnclosingRequest enclosingRequest = new BasicHttpEntityEnclosingRequest(
        request.getRequestLine());
    serverConnection.receiveRequestEntity(enclosingRequest);

    InputStream input = enclosingRequest.getEntity().getContent();
    MultipartStream multipartStream = new MultipartStream(input, boundary
        .getBytes());
    String headers = multipartStream.readHeaders();

    /* Get the filename. */
    StringTokenizer tokens = new StringTokenizer(headers, ";", false);
    String filename = null;
    while (tokens.hasMoreTokens() && filename == null) {
      String token = tokens.nextToken().trim();
      if (token.startsWith("filename=")) {
        filename = URLDecoder.decode(token.substring("filename=\"".length(),
            token.lastIndexOf("\"")), "utf8");
      }
    }

    File uploadDirectory = new File("/sdcard/fileshare/uploads");
    if (!uploadDirectory.exists()) {
      uploadDirectory.mkdirs();
    }

    /* Write the file and add it to the shared folder. */
    File uploadFile = new File(uploadDirectory, filename);
    FileOutputStream output = new FileOutputStream(uploadFile);
    multipartStream.readBodyData(output);
    output.close();

    Uri fileUri = Uri.withAppendedPath(FileProvider.CONTENT_URI, uploadFile
        .getAbsolutePath());
    Uri folderUri = Uri.withAppendedPath(
        FileSharingProvider.Folders.CONTENT_URI, folderId);
    FileSharingProvider.addFileToFolder(mContentResolver, fileUri, folderUri);
  }

  public String getHTMLHeader() {
    return "<html><head><title>File Share</title></head><body>";
  }

  public String getHTMLFooter() {
    return "</body></html>";
  }

  private String getFolderListing() {
    /* Get list of folders */
    Cursor c = mContentResolver.query(FileSharingProvider.Folders.CONTENT_URI,
        null, null, null, null);
    int nameIndex = c
        .getColumnIndexOrThrow(FileSharingProvider.Folders.Columns.DISPLAY_NAME);
    int idIndex = c
        .getColumnIndexOrThrow(FileSharingProvider.Folders.Columns._ID);
    String s = "";
    while (c.moveToNext()) {
      String name = c.getString(nameIndex);
      int id = c.getInt(idIndex);
      s += folderToLink(name, id) + "<br/>";
    }
    c.close();
    return s;
  }

  /**
   * Returns a form that allows users to upload files.
   */
  private String getUploadForm(String folderId) {
    if (mSharedPreferences.getBoolean(FileSharingService.PREFS_ALLOW_UPLOADS,
        false)) {
      return "<form method=\"POST\" action=\"/folder/" + folderId + "\" "
          + "enctype=\"multipart/form-data\"> "
          + "<input type=\"file\" name=\"file\" size=\"40\"/> "
          + "<input type=\"submit\" value=\"Upload\"/>";
    }
    return "";
  }

  private String getFolderId(String firstline) {
    Pattern p = Pattern.compile("/folder/(\\d+)");
    Matcher m = p.matcher(firstline);
    boolean b = m.find(0);
    if (b) {
      return m.group(1);
    }
    return null;
  }

  private String getLoginForm() {
    return "<form method=\"POST\" action=\"/login\""
        + " enctype=\"application/x-www-form-urlencoded\""
        + "/><input type=\"password\" name=\"password\"/>"
        + "<input type=\"submit\" value=\"Login\"/></form>";
  }

  private String getFileId(String firstline) {
    Pattern p = Pattern.compile("/file/(\\d+)");
    Matcher m = p.matcher(firstline);
    boolean b = m.find(0);
    if (b) {
      return m.group(1);
    }
    return null;
  }

  private String getFileListing(Uri uri) {
    int folderId = Integer.parseInt(uri.getPathSegments().get(1));
    Uri fileUri = FileSharingProvider.Files.CONTENT_URI;
    String where = FileSharingProvider.Files.Columns.FOLDER_ID + "=" + folderId;
    Cursor c = mContentResolver.query(fileUri, null, where, null, null);
    int nameIndex = c
        .getColumnIndexOrThrow(FileSharingProvider.Files.Columns.DISPLAY_NAME);
    int idIndex = c
        .getColumnIndexOrThrow(FileSharingProvider.Files.Columns._ID);
    String s = "";
    while (c.moveToNext()) {
      String name = c.getString(nameIndex);
      int id = c.getInt(idIndex);
      s += fileToLink(name, id) + "<br/>";
    }
    c.close();
    return s;
  }

  private void addFileEntity(final Uri uri, HttpResponse response)
      throws IOException {
    if (mTransferStartedListener != null) {
      mTransferStartedListener.started(uri);
    }

    Cursor c = mContentResolver.query(uri, null, null, null, null);
    c.moveToFirst();
    int nameIndex = c
        .getColumnIndexOrThrow(FileSharingProvider.Files.Columns.DISPLAY_NAME);
    String name = c.getString(nameIndex);
    int dataIndex = c
        .getColumnIndexOrThrow(FileSharingProvider.Files.Columns._DATA);
    Uri data = Uri.parse(c.getString(dataIndex));

    c = mContentResolver.query(data, null, null, null, null);
    c.moveToFirst();
    int sizeIndex = c.getColumnIndexOrThrow(OpenableColumns.SIZE);
    int sizeBytes = c.getInt(sizeIndex);
    c.close();

    InputStream input = mContentResolver.openInputStream(data);

    String contentType = "application/octet-stream";
    if (name.endsWith(".jpg")) {
      contentType = "image/jpg";
    }

    response.addHeader("Content-Type", contentType);
    response.addHeader("Content-Length", "" + sizeBytes);
    response.setEntity(new InputStreamEntity(input, sizeBytes));
  }

  private String folderToLink(String folderName, int folderId) {
    return "<a href=\"/folder/" + folderId + "\">" + folderName + "</a>";
  }

  private String fileToLink(String fileName, int fileId) {
    return "<a href=\"/file/" + fileId + "/" + fileName + "\">" + fileName
        + "</a>";
  }
}
