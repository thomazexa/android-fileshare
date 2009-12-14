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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class FileShare extends Activity {
	
	private static final String TAG = "FileSharer";

	/* startActivity request codes */
	private static final int PICK_FILE_REQUEST = 1;
	private static final int PICK_FOLDER_REQUEST = 2;
		
	/* Used to keep track of the currently selected file */
	private Uri mFileToShare;
	    
    private final View.OnClickListener mAddFileListener = new View.OnClickListener() {
    	public void onClick(View v) {
    		Intent pickFileIntent = new Intent();
    		pickFileIntent.setAction(Intent.ACTION_GET_CONTENT);
    		pickFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
    		pickFileIntent.setType("*/*");
    		Intent chooserIntent = Intent.createChooser(
             		pickFileIntent, getText(R.string.choosefile_title));
            startActivityForResult(chooserIntent, PICK_FILE_REQUEST);
    	}
    };
    
    private final View.OnClickListener mManageContentListener = new View.OnClickListener() {
    	public void onClick(View v) {
    		Intent manageIntent = new Intent();
    		manageIntent.setAction(Intent.ACTION_MAIN);
    		manageIntent.setType(FileSharingProvider.Folders.CONTENT_TYPE);
    		startActivity(manageIntent);
    	}
    };
   
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       
        setContentView(R.layout.main);
        
        /* Startup the FileSharingService */
        Intent serviceIntent = new Intent();
        serviceIntent.setAction("com.navjagpal.filesharer.IFileSharingService");
        startService(serviceIntent);
        
        /* Add the add file to shared folder */
        Button addFileButton = (Button) findViewById(R.id.addfile);
        addFileButton.setOnClickListener(mAddFileListener);
        
        /* Manage content button */
        Button manageButton = (Button) findViewById(R.id.manage);
        manageButton.setOnClickListener(mManageContentListener);
        
        /* Setup the status text */
        TextView ipTextView = (TextView) findViewById(R.id.url);
        //ipTextView.setText(getText(R.string.unknown_ipaddress));
        ipTextView.setText("http://" + getIPAddress(this) + ":9999");
  
        /* Help button */
        Button helpButton = (Button) findViewById(R.id.help);
        helpButton.setOnClickListener(new View.OnClickListener() {
        	public void onClick(View v) {
        		Intent helpIntent = new Intent();
        		helpIntent.setClass(FileShare.this, Help.class);
        		startActivity(helpIntent);
        	}
        });   
    }


    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (resultCode != RESULT_OK)
			return;
		
		switch (requestCode) {
		case PICK_FILE_REQUEST:
			/* Store this file somewhere */
			mFileToShare = data.getData();
			
			/* Now pick a folder */
			Intent pickFolder = new Intent();
			pickFolder.setAction(Intent.ACTION_PICK);
			pickFolder.setType(FileSharingProvider.Folders.CONTENT_ITEM_TYPE);
			startActivityForResult(pickFolder, PICK_FOLDER_REQUEST);
			break;
		case PICK_FOLDER_REQUEST:
			addFileToFolder(mFileToShare, data.getData());
			break;
			
		}
	}
		
	private void addFileToFolder(Uri file, Uri folder) {
		FileSharingProvider.addFileToFolder(getContentResolver(), file, folder);	
	}
	
	public static String getIPAddress(Context context) {
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		android.net.wifi.WifiInfo info = wifiManager.getConnectionInfo();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		
		byte[] intByte;
		try {
			dos.writeInt(info.getIpAddress());
			dos.flush();
			intByte = bos.toByteArray();
		} catch (IOException e) {
			Log.e(TAG, "Problem converting IP address");
			return "unknown";
		}
		
		// Reverse int bytes.. damn, this is a hack.
		byte[] addressBytes = new byte[intByte.length];
		for (int i = 0; i < intByte.length; i++) {
			addressBytes[i] = intByte[(intByte.length - 1) - i];
		}
		
		InetAddress address = null;
		try {
			address = InetAddress.getByAddress(addressBytes);
		} catch (UnknownHostException e) {
			Log.e(TAG, "Problem determing IP address");
			return "unknown";
		}
		return address.getHostAddress();
	}
	
}