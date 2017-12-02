package gpsplus.rtkgps;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ConditionVariable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.Nonnull;

public class BluetoothToRtklib {

    private static final boolean DBG = BuildConfig.DEBUG & true;
    static final String TAG = BluetoothToRtklib.class.getSimpleName();

    // Constants that indicate the current connection state
    public static final int STATE_IDLE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_WAITING = 3;
    public static final int STATE_RECONNECTING = 4;

    //  Standard UUID for the Serial Port Profile
    private static final java.util.UUID UUID_SPP = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static final int RECONNECT_TIMEOUT_MS = 2000;

    final LocalSocketThread mLocalSocketThread;

    final BluetoothServiceThread mBluetoothThread;

    final ConditionVariable mIsBluetoothReadyCondvar;

    private Callbacks mCallbacks;

    private static final Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onConnected() {}
        @Override
        public void onStopped() {}
        @Override
        public void onConnectionLost() {}
    };

    public interface Callbacks {

        public void onConnected();

        public void onStopped();

        public void onConnectionLost();

    }

    public BluetoothToRtklib(@Nonnull String bluetoothAddress,
            @Nonnull String localSocketPath) {
        mLocalSocketThread = new LocalSocketThread(localSocketPath);

        mBluetoothThread = new BluetoothServiceThread(bluetoothAddress);
        mIsBluetoothReadyCondvar = new ConditionVariable(false);

        mLocalSocketThread.setBindpoint(localSocketPath+"_"+bluetoothAddress);
        mCallbacks = sDummyCallbacks;
    }

    public void start() {
        mBluetoothThread.start();
        mLocalSocketThread.start();
    }

    public void stop() {
        mBluetoothThread.cancel();
        mLocalSocketThread.cancel();
        mIsBluetoothReadyCondvar.open();
    }

    public void setCallbacks(Callbacks callbacks) {
        if (callbacks == null) throw new IllegalStateException();
        if (mBluetoothThread.isAlive()) throw new IllegalStateException();
        mCallbacks = callbacks;
    }

    private final class LocalSocketThread extends RtklibLocalSocketThread {

        public LocalSocketThread(String socketPath) {
            super(socketPath);
        }

        @Override
        protected boolean isDeviceReady() {
            return mIsBluetoothReadyCondvar.block(1);
        }

        @Override
        protected void waitDevice() {
            if (DBG) Log.v(TAG, "waitBluetooth()");
            mIsBluetoothReadyCondvar.block();
        }

        @Override
        protected boolean onDataReceived(byte[] buffer, int offset, int count) {
            if (count <= 0) return true;

            try {
                mBluetoothThread.write(buffer, 0, count);
            }catch(IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        @Override
        protected void onLocalSocketConnected() {
            mCallbacks.onConnected();
        }
    }

    private class BluetoothServiceThread extends Thread {

        private final BluetoothAdapter mBtAdapter;
        private final BluetoothDevice mBtDevice;
        private BluetoothSocket mSocket;
        private InputStream mInputStream;
        private OutputStream mOutputStream;

        private int mConnectionState;
        private volatile boolean cancelRequested;

        public BluetoothServiceThread(String bluetoothAddress) {
            mInputStream = RtklibLocalSocketThread.DummyInputStream.instance;
            mOutputStream = RtklibLocalSocketThread.DummyOutputStream.instance;
            mConnectionState = STATE_IDLE;
            cancelRequested = false;
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
            mBtDevice = mBtAdapter.getRemoteDevice(bluetoothAddress);
        }

        private synchronized void setState(int state) {
            int oldState = mConnectionState;
            mConnectionState = state;
            if (DBG) Log.d(TAG, "BT setState() " + oldState + " -> " + state);

            if (mConnectionState == STATE_CONNECTED)
                mIsBluetoothReadyCondvar.open();
            else {
                mIsBluetoothReadyCondvar.close();
                mLocalSocketThread.disconnect();
            }
        }

        public void cancel() {
            BluetoothSocket s;
            synchronized(this) {
                cancelRequested = true;
                s = mSocket;

                if (s != null) {
                    try {
                        mCallbacks.onStopped();
                        if (DBG) Log.v(TAG, "BT close");
                        s.close();
                    } catch (IOException e) {
                        Log.e(TAG, "close() of connect socket failed", e);
                    }
                }
                notifyAll();
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer, int offset, int count) throws IOException {
            OutputStream os;
            synchronized(this) {
                if (mConnectionState != STATE_CONNECTED) {
                    Log.e(TAG, "write() error: not connected");
                    return;
                }
                os = mOutputStream;
            }
            //if (DBG) Log.v(TAG, "BT socket write " + count + " bytes");
            os.write(buffer, offset, count);
        }

        private synchronized void throwIfCancelRequested() throws CancelRequestedException {
                if (cancelRequested) throw new CancelRequestedException();
        }

        private void connect() throws IOException {
            BluetoothSocket s;

            if (!mBtAdapter.isEnabled())
                throw(new IOException("Bluetooth disabled"));

            s = mBtDevice.createInsecureRfcommSocketToServiceRecord(UUID_SPP);
            try {
                s.connect();
                synchronized(this) {
                    mSocket = s;
                    mInputStream = s.getInputStream();
                    mOutputStream = s.getOutputStream();
                }
            }catch (IOException e) {
                synchronized(this) {
                    Log.e(TAG, "connect() failed: " +  e.getLocalizedMessage());
                    try {
                        s.close();
                    }catch (IOException e2) {
                        Log.e(TAG, "mSocket.close() failed: " + e2.getLocalizedMessage());
                    }

                    mSocket = null;
                    mInputStream = RtklibLocalSocketThread.DummyInputStream.instance;
                    mOutputStream = RtklibLocalSocketThread.DummyOutputStream.instance;
                }
                throw(e);
            }
        }

        private void connectLoop() throws CancelRequestedException {
            while(true) {
                try {
                    connect();
                    return;
                }catch (IOException e) {
                    synchronized(this) {
                        throwIfCancelRequested();
                        try {
                            setState(STATE_RECONNECTING);
                            wait(RECONNECT_TIMEOUT_MS);
                        } catch(InterruptedException ie) {
                            throwIfCancelRequested();
                        }
                    }
                }
            }
        }

        private void transferDataLoop() throws CancelRequestedException {
            int rcvd;
            final byte buf[] = new byte[4096];

            try {
                while(true) {
                    rcvd =  mInputStream.read(buf, 0, buf.length);
                    if (rcvd >= 0) {
                        try {
                            mLocalSocketThread.write(buf, 0, rcvd);
                        }catch (IOException e) {
                            // TODO
                            e.printStackTrace();
                        }
                    }
                    if (rcvd < 0)
                        throw new IOException("EOF");
                }
            }catch (IOException e) {
                synchronized(this) {
                    try {
                        mSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    mInputStream = RtklibLocalSocketThread.DummyInputStream.instance;
                    mOutputStream = RtklibLocalSocketThread.DummyOutputStream.instance;
                    throwIfCancelRequested();
                }
            }
        }

        @Override
        public void run() {
            Log.i(TAG, "BEGIN BluetoothToLocalSocket-BT");
            setName("BluetoothToLocalSocket-BT");

            try {
                setState(STATE_CONNECTING);
                while (true) {
                    connectLoop();

                    setState(STATE_CONNECTED);
                    transferDataLoop();

                    setState(STATE_RECONNECTING);
                    mCallbacks.onConnectionLost();
                }
            }catch (CancelRequestedException cre) {}
        }

        private class CancelRequestedException extends Exception {
            private static final long serialVersionUID = 1L;
        }
    }


}
