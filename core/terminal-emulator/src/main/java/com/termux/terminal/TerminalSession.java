package com.termux.terminal;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A terminal session: a PTY subprocess coupled to a terminal emulator.
 *
 * <p>The subprocess is started by {@link #initializeEmulator}. Three daemon threads
 * manage I/O (reader, writer, waiter). All emulator callbacks run on the main thread.
 *
 * <p>Phase 2 (Rust migration): the {@link RawByteInterceptor} hook feeds raw PTY
 * bytes to the Rust VT parser (alacritty_terminal) in parallel with the Java emulator.
 */
public final class TerminalSession extends TerminalOutput {

    private static final String LOG_TAG = "TerminalSession";
    private static final int MSG_NEW_INPUT = 1;
    private static final int MSG_PROCESS_EXITED = 4;

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;
    TerminalSessionClient mClient;

    /** Optional interceptor for raw PTY output bytes (Phase 2 Rust backend). */
    private volatile RawByteInterceptor mRawByteInterceptor;

    /** Optional listener for terminal resize events (Phase 2 Rust backend). */
    private volatile ResizeListener mResizeListener;

    // ── I/O queues ──────────────────────────────────────────

    /** PTY output → main thread (64KB buffer). */
    final ByteQueue mProcessToTerminalIOQueue = new ByteQueue(64 * 1024);
    /** User input → PTY (4KB buffer). */
    final ByteQueue mTerminalToProcessIOQueue = new ByteQueue(4096);
    /** UTF-8 encoding scratch buffer for writeCodePoint(). */
    private final byte[] mUtf8InputBuffer = new byte[5];

    // ── Process state ───────────────────────────────────────

    /** PID of the shell. 0 = not started, -1 = finished. */
    int mShellPid;
    int mShellExitStatus;
    private int mTerminalFileDescriptor;

    /** Application-assigned name for user identification. */
    public String mSessionName;

    // ── Threading ────────────────────────────────────────────

    /**
     * Static handler to prevent leaking the outer TerminalSession (64KB+/session).
     * Uses WeakReference so the session can be GC'd if the Activity is destroyed.
     */
    final Handler mMainThreadHandler;
    private Thread mReaderThread;
    private Thread mWriterThread;
    private Thread mWaiterThread;

    // ── Constructor args (deferred init via updateSize) ─────

    private final String mShellPath;
    private final String mCwd;
    private final String[] mArgs;
    private final String[] mEnv;
    private final Integer mTranscriptRows;

    // ── Public API ──────────────────────────────────────────

    /** Functional interface for raw PTY byte interception (Phase 2). */
    public interface RawByteInterceptor {
        void onRawBytes(byte[] data, int length);
    }

    /** Functional interface for terminal resize notification (Phase 2). */
    public interface ResizeListener {
        void onResize(int rows, int columns);
    }

    public TerminalSession(String shellPath, String cwd, String[] args, String[] env,
                           Integer transcriptRows, TerminalSessionClient client) {
        this.mShellPath = shellPath;
        this.mCwd = cwd;
        this.mArgs = args;
        this.mEnv = env;
        this.mTranscriptRows = transcriptRows;
        this.mClient = client;
        this.mMainThreadHandler = new SessionHandler(this);
    }

    /** Set an interceptor for raw PTY output bytes. Pass null to remove. */
    public void setRawByteInterceptor(RawByteInterceptor interceptor) {
        mRawByteInterceptor = interceptor;
    }

    /** Set a listener for terminal resize events. Pass null to remove. */
    public void setResizeListener(ResizeListener listener) {
        mResizeListener = listener;
    }

    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;
        if (mEmulator != null) mEmulator.updateTerminalSessionClient(client);
    }

    /** Inform the PTY of the new size and reflow or initialize the emulator. */
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels);
        } else {
            JNI.setPtyWindowSize(mTerminalFileDescriptor, rows, columns, cellWidthPixels, cellHeightPixels);
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
        // Notify Rust engine of resize (Phase 2)
        ResizeListener listener = mResizeListener;
        if (listener != null) {
            listener.onResize(rows, columns);
        }
    }

    /** The terminal title as set through escape sequences, or null. */
    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    public int getPid() {
        return mShellPid;
    }

    public synchronized boolean isRunning() {
        return mShellPid != -1;
    }

    public synchronized int getExitStatus() {
        return mShellExitStatus;
    }

    /** Write data to the shell process. */
    @Override
    public void write(byte[] data, int offset, int count) {
        if (mShellPid > 0) mTerminalToProcessIOQueue.write(data, offset, count);
    }

    /** Write a Unicode code point encoded as UTF-8. */
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }

        int pos = 0;
        if (prependEscape) mUtf8InputBuffer[pos++] = 27;

        if (codePoint <= 0x7F) {
            mUtf8InputBuffer[pos++] = (byte) codePoint;
        } else if (codePoint <= 0x7FF) {
            mUtf8InputBuffer[pos++] = (byte) (0xC0 | (codePoint >> 6));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | (codePoint & 0x3F));
        } else if (codePoint <= 0xFFFF) {
            mUtf8InputBuffer[pos++] = (byte) (0xE0 | (codePoint >> 12));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | (codePoint & 0x3F));
        } else {
            mUtf8InputBuffer[pos++] = (byte) (0xF0 | (codePoint >> 18));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
            mUtf8InputBuffer[pos++] = (byte) (0x80 | (codePoint & 0x3F));
        }
        write(mUtf8InputBuffer, 0, pos);
    }

    public void reset() {
        mEmulator.reset();
        notifyScreenUpdate();
    }

    /** Send SIGKILL to the shell process. */
    public void finishIfRunning() {
        if (isRunning()) {
            try {
                Os.kill(mShellPid, OsConstants.SIGKILL);
            } catch (ErrnoException e) {
                Logger.logWarn(mClient, LOG_TAG, "Failed sending SIGKILL: " + e.getMessage());
            }
        }
    }

    /** Returns the shell's working directory via /proc, or null. */
    public String getCwd() {
        if (mShellPid < 1) return null;
        try {
            String cwdSymlink = String.format("/proc/%s/cwd/", mShellPid);
            String resolved = new File(cwdSymlink).getCanonicalPath();
            String resolvedSlash = resolved.endsWith("/") ? resolved : resolved + '/';
            if (!cwdSymlink.equals(resolvedSlash)) return resolved;
        } catch (IOException | SecurityException e) {
            Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Error getting cwd", e);
        }
        return null;
    }

    // ── Emulator initialization ─────────────────────────────

    /**
     * Create the emulator, fork the shell, and start I/O daemon threads.
     * All three threads are daemon threads so they don't prevent JVM shutdown
     * and don't drain battery if the session is abandoned.
     */
    public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);

        int[] processId = new int[1];
        mTerminalFileDescriptor = JNI.createSubprocess(mShellPath, mCwd, mArgs, mEnv, processId, rows, columns, cellWidthPixels, cellHeightPixels);
        mShellPid = processId[0];
        mClient.setTerminalShellPid(this, mShellPid);

        final FileDescriptor fd = wrapFileDescriptor(mTerminalFileDescriptor, mClient);

        // Reader: PTY → ByteQueue → main thread
        mReaderThread = new Thread("TermReader[pid=" + mShellPid + "]") {
            @Override
            public void run() {
                try (InputStream in = new FileInputStream(fd)) {
                    byte[] buf = new byte[4096];
                    while (true) {
                        int read = in.read(buf);
                        if (read == -1) return;
                        if (!mProcessToTerminalIOQueue.write(buf, 0, read)) return;
                        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
                    }
                } catch (Exception e) {
                    // Shutting down
                }
            }
        };
        mReaderThread.setDaemon(true);
        mReaderThread.start();

        // Writer: ByteQueue → PTY
        mWriterThread = new Thread("TermWriter[pid=" + mShellPid + "]") {
            @Override
            public void run() {
                byte[] buf = new byte[4096];
                try (FileOutputStream out = new FileOutputStream(fd)) {
                    while (true) {
                        int n = mTerminalToProcessIOQueue.read(buf, true);
                        if (n == -1) return;
                        out.write(buf, 0, n);
                    }
                } catch (IOException e) {
                    // Shutting down
                }
            }
        };
        mWriterThread.setDaemon(true);
        mWriterThread.start();

        // Waiter: blocks until shell exits
        mWaiterThread = new Thread("TermWaiter[pid=" + mShellPid + "]") {
            @Override
            public void run() {
                int exitCode = JNI.waitFor(mShellPid);
                mMainThreadHandler.sendMessage(
                    mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, exitCode));
            }
        };
        mWaiterThread.setDaemon(true);
        mWaiterThread.start();
    }

    // ── Internal callbacks ──────────────────────────────────

    protected void notifyScreenUpdate() {
        mClient.onTextChanged(this);
    }

    /** Cleanup resources when the process exits. */
    void cleanupResources(int exitStatus) {
        synchronized (this) {
            mShellPid = -1;
            mShellExitStatus = exitStatus;
        }

        mTerminalToProcessIOQueue.close();
        mProcessToTerminalIOQueue.close();
        JNI.close(mTerminalFileDescriptor);

        // Interrupt I/O threads to ensure they terminate promptly
        if (mReaderThread != null) mReaderThread.interrupt();
        if (mWriterThread != null) mWriterThread.interrupt();
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        mClient.onTitleChanged(this);
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        mClient.onCopyTextToClipboard(this, text);
    }

    @Override
    public void onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this);
    }

    @Override
    public void onBell() {
        mClient.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        mClient.onColorsChanged(this);
    }

    // ── Static handler (prevents memory leak) ───────────────

    /**
     * Static inner class handler. Uses WeakReference to the session to prevent
     * the implicit outer-class reference that causes 64KB+ memory leaks per session.
     * If the session is GC'd, pending messages are silently dropped.
     */
    private static class SessionHandler extends Handler {
        private final WeakReference<TerminalSession> mSessionRef;
        final byte[] mReceiveBuffer = new byte[64 * 1024];

        SessionHandler(TerminalSession session) {
            super(Looper.getMainLooper());
            mSessionRef = new WeakReference<>(session);
        }

        @Override
        public void handleMessage(Message msg) {
            TerminalSession session = mSessionRef.get();
            if (session == null) return;

            int bytesRead = session.mProcessToTerminalIOQueue.read(mReceiveBuffer, false);
            if (bytesRead > 0) {
                // Feed raw bytes to Rust backend (Phase 2) if interceptor is set
                RawByteInterceptor interceptor = session.mRawByteInterceptor;
                if (interceptor != null) {
                    interceptor.onRawBytes(mReceiveBuffer, bytesRead);
                }
                session.mEmulator.append(mReceiveBuffer, bytesRead);
                session.notifyScreenUpdate();
            }

            if (msg.what == MSG_PROCESS_EXITED) {
                int exitCode = (Integer) msg.obj;
                session.cleanupResources(exitCode);

                String exitDescription = "\r\n[Process completed";
                if (exitCode > 0) {
                    exitDescription += " (code " + exitCode + ")";
                } else if (exitCode < 0) {
                    exitDescription += " (signal " + (-exitCode) + ")";
                }
                exitDescription += " - press Enter]";

                byte[] bytes = exitDescription.getBytes(StandardCharsets.UTF_8);
                session.mEmulator.append(bytes, bytes.length);
                session.notifyScreenUpdate();
                session.mClient.onSessionFinished(session);
            }
        }
    }

    // ── Utility ─────────────────────────────────────────────

    private static FileDescriptor wrapFileDescriptor(int fd, TerminalSessionClient client) {
        FileDescriptor result = new FileDescriptor();
        try {
            Field field;
            try {
                field = FileDescriptor.class.getDeclaredField("descriptor");
            } catch (NoSuchFieldException e) {
                field = FileDescriptor.class.getDeclaredField("fd");
            }
            field.setAccessible(true);
            field.set(result, fd);
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
            Logger.logStackTraceWithMessage(client, LOG_TAG, "Error accessing FileDescriptor field", e);
            System.exit(1);
        }
        return result;
    }
}
