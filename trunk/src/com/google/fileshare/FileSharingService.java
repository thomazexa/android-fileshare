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

package com.google.fileshare;

import java.io.IOException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

public class FileSharingService extends Service {
	
	private static final String PREFS_NAME = "FileSharerServicePrefs";

	private static final int DEFAULT_PORT = 9999;

	private static final String TAG = "FileSharerService";

	private WebServer mWebServer;

	private int mPort;

	private final IFileSharingService.Stub mBinder = new IFileSharingService.Stub() {
		public int getPort() {
			return mPort;
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		mPort = settings.getInt("port", DEFAULT_PORT);
		try {
			mWebServer = new WebServer(getContentResolver(), mPort);
			mWebServer.setOnTransferStartedListener(new WebServer.TransferStartedListener() {
				public void started(Uri uri) {
					NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					Notification notification = new Notification(R.drawable.folder, null, System.currentTimeMillis());
					PendingIntent pendingIntent = PendingIntent.getActivity(FileSharingService.this, 0, new Intent(
							FileSharingService.this, FileShare.class), 0);
					notification.setLatestEventInfo(FileSharingService.this, "File Share", "Transfer Started", pendingIntent);
					nm.notify(1, notification);
				}
			});
		} catch (IOException e) {
			Log.e(TAG, "Problem creating server socket " + e.toString());
		}

		Thread t = new Thread() {
			public void run() {
				mWebServer.runWebServer();
			}
		};
		t.start();
		Log.i(TAG, "Started webserver");
	}

	@Override
	public IBinder onBind(Intent intent) {
		if (IFileSharingService.class.getName().equals(intent.getAction())) {
			return mBinder;
		}
		return null;
	}

}
