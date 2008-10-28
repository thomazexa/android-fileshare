/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /Users/nav/Documents/workspace/FileShare/src/com/google/fileshare/IFileSharingService.aidl
 */
package com.google.fileshare;
import java.lang.String;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Binder;
import android.os.Parcel;
public interface IFileSharingService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.google.fileshare.IFileSharingService
{
private static final java.lang.String DESCRIPTOR = "com.google.fileshare.IFileSharingService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an IFileSharingService interface,
 * generating a proxy if needed.
 */
public static com.google.fileshare.IFileSharingService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
com.google.fileshare.IFileSharingService in = (com.google.fileshare.IFileSharingService)obj.queryLocalInterface(DESCRIPTOR);
if ((in!=null)) {
return in;
}
return new com.google.fileshare.IFileSharingService.Stub.Proxy(obj);
}
public android.os.IBinder asBinder()
{
return this;
}
public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_getPort:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getPort();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.google.fileshare.IFileSharingService
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
public int getPort() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getPort, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_getPort = (IBinder.FIRST_CALL_TRANSACTION + 0);
}
public int getPort() throws android.os.RemoteException;
}
