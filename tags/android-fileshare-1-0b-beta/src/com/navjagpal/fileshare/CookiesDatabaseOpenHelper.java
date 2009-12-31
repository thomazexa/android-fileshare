package com.navjagpal.fileshare;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class CookiesDatabaseOpenHelper extends SQLiteOpenHelper {

  public CookiesDatabaseOpenHelper(Context context) {
    super(context, "cookies.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase database) {
    database.execSQL(
      "CREATE TABLE cookies(name STRING, value STRING, expiry INTEGER)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
    // TODO Auto-generated method stub

  }

}
