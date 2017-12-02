package gpsplus.rtkgps;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.annotation.Nonnegative;

public abstract class RtklibLocalSocketThread extends Thread {

    public static final int STATE_IDLE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_WAITING = 3;
    public static final int STATE_RECONNECTING = 4;

    public static final int RECONNECT_TIMEOUT_MS = 2000;

    private static final boolean DBG = BuildConfig.DEBUG & true;
    static final String TAG = RtklibLocalSocketThread.class.getSimpleName();


    private final LocalSocketAddress mSocketPath;
    private String mBindpoint;

    private int mReconnectTimeout;

    private volatile int mConnectionState;
    private volatile boolean cancelRequested;
    private LocalSocket mSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStream;


    public RtklibLocalSocketThread(String socketPath) {
        mSocketPath = new LocalSocketAddress(socketPath, Namespace.FILESYSTEM);
        Log.i(TAG, "Socket Path " + mSocketPath + " " + socketPath);
        mInputStream = DummyInputStream.instance;
        mOutputStream = DummyOutputStream.instance;
        mReconnectTimeout = RECONNECT_TIMEOUT_MS;
        mBindpoint = "";
    }

    public void setReconnectTimeout(@Nonnegative int ms) {
        mReconnectTimeout = ms;
    }

    public int getReconnectTimeout() {
        return mReconnectTimeout;
    }

    public void setBindpoint(String point) {
        mBindpoint = point;
    }

    public String getBindpoint() {
        return mBindpoint;
    }

    public void cancel() {
        if (DBG) Log.v(TAG, "cancel()");
        synchronized(this) {
            cancelRequested = true;
            disconnect();
            notifyAll();
        }
    }

    public synchronized void disconnect() {

        if (DBG) Log.v(TAG, "Local disconnect() connected: " + (mConnectionState == STATE_CONNECTED));

        if (mConnectionState != STATE_CONNECTED) {
            return;
        }

        if (mSocket == null)
            return;

        try {
            mSocket.shutdownInput();
        }catch(IOException e) {
            Log.e(TAG, "Local shutdownInput() of mSocket failed", e);
        }

        try {
            mSocket.shutdownOutput();
        }catch(IOException e) {
            Log.e(TAG, "Local shutdownOutput() of mSocket failed", e);
        }

        try {
            mInputStream.close();
        }catch(IOException e) {
            Log.e(TAG, "Local close() of mInputStream failed", e);
        }

        try {
            mOutputStream.close();
        }catch(IOException e) {
            Log.e(TAG, "Local close() of mOutputStream failed", e);
        }

        try {
            mSocket.close();
        }catch(IOException e) {
            Log.e(TAG, "Local close() of mSocket failed", e);
        }

        mInputStream = DummyInputStream.instance;
        mOutputStream = DummyOutputStream.instance;

    }

    /**
     * Write to the connected OutStream.
     * @param buffer  The bytes to write
     */
    public void write(byte[] buffer, int offset, int count) throws IOException {
        OutputStream os;
        synchronized(this) {
            if (mConnectionState != STATE_CONNECTED) {
                Log.e(TAG, "Local write() error: not connected");
                return;
            }
            os = mOutputStream;
        }
        //if (DBG) Log.v(TAG, "Local socket write " + count + " bytes");
        os.write(buffer, offset, count);
    }


    private synchronized void setState(int state) {
        int oldState = mConnectionState;
        mConnectionState = state;
        if (DBG) Log.v(TAG, "Local setState() " + oldState + " -> " + state);
    }

    private boolean connectLoop() {
            LocalSocket s = new LocalSocket();

        if (!TextUtils.isEmpty(mBindpoint)) {
            try {
                s.bind(new LocalSocketAddress(mBindpoint));

            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

       while(!cancelRequested) {
            try {
                s.connect(mSocketPath);
                synchronized(this) {
                    mSocket = s;
                    mInputStream = s.getInputStream();
                    mOutputStream = s.getOutputStream();
                }
                return true;
            }catch (IOException e) {
                synchronized(this) {
                    if (cancelRequested) {
                        return false;
                    }
                    if (!isDeviceReady()) {
                        return true;
                    }
                    setState(STATE_RECONNECTING);
                    try {
                        wait(mReconnectTimeout);
                    } catch(InterruptedException ie) {
                        return false;
                    }
                }
            }
        }
        return false;

    }

    private boolean transferDataLoop() {
        int rcvd;
        final byte buf[] = new byte[4096];

        try {
            while (!cancelRequested) {
                rcvd =  mInputStream.read(buf, 0, buf.length);
                if (!onDataReceived(buf, 0, rcvd)) {
                    synchronized(this) {
                        disconnect();
                        return !cancelRequested;
                    }
                }
                if (rcvd < 0) {
                    if (DBG) Log.v(TAG, "EOF reached");
                    return !cancelRequested;
                }
            }
            return false;
        }catch (IOException e) {
            synchronized(this) {
                disconnect();
                return !cancelRequested;
            }
        }
    }

    protected abstract boolean isDeviceReady();

    protected abstract void waitDevice();

    protected abstract boolean onDataReceived(byte[] buffer, int offset, int count);

    protected abstract void onLocalSocketConnected();

    @Override
    public void run() {
        Log.i(TAG, "BEGIN BluetoothToLocalSocket-Socket");
        setName("BluetoothToLocalSocket-LocalSocket");

        setState(STATE_WAITING);
        waitDevice();

        setState(STATE_CONNECTING);

        while (!cancelRequested) {

            if (!connectLoop())
                return;

            setState(STATE_CONNECTED);
            onLocalSocketConnected();

            if (!transferDataLoop())
                return;

            setState(STATE_WAITING);
            waitDevice();

            setState(STATE_RECONNECTING);
        }
    }

    public static class DummyInputStream extends InputStream {

        public static final DummyInputStream instance = new DummyInputStream();

        private DummyInputStream() {}

        @Override
        public int read() throws IOException {
            return -1;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return -1;
        }
    }

    public static class DummyOutputStream extends OutputStream  {

        public static final DummyOutputStream instance = new DummyOutputStream();

        private DummyOutputStream() {}

        @Override
        public void write(int arg0) throws IOException {
        }

        @Override
        public void write(byte[] buffer, int offset, int count)
                throws IOException {
        }
    }


}
