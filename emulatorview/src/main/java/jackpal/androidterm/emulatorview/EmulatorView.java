/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm.emulatorview;

import jackpal.androidterm.emulatorview.compat.AndroidCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat;
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompatFactory;
import jackpal.androidterm.emulatorview.compat.KeycodeConstants;

import java.io.IOException;
import java.util.Hashtable;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Scroller;

/**
 * A view on a {@link TermSession}.  Displays the terminal emulator's screen,
 * provides access to its scrollback buffer, and passes input through to the
 * terminal emulator.
 * <p>
 * If this view is inflated from an XML layout, you need to call {@link
 * #attachSession attachSession} and {@link #setDensity setDensity} before using
 * the view.  If creating this view from code, use the {@link
 * #EmulatorView(Context, TermSession, DisplayMetrics)} constructor, which will
 * take care of this for you.
 */
public class EmulatorView extends View implements GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener {
    private final static String TAG = "EmulatorView";
    private final static boolean LOG_KEY_EVENTS = false;
    private final static boolean LOG_IME = false;

    /**
     * We defer some initialization until we have been layed out in the view
     * hierarchy. The boolean tracks when we know what our size is.
     */
    private boolean mKnownSize;

    // Set if initialization was deferred because a TermSession wasn't attached
    private boolean mDeferInit = false;

    private int mVisibleWidth;
    private int mVisibleHeight;

    private TermSession mTermSession;

    /**
     * Total width of each character, in pixels
     */
    private float mCharacterWidth;

    /**
     * Total height of each character, in pixels
     */
    private int mCharacterHeight;

    /**
     * Top-of-screen margin
     */
    private int mTopOfScreenMargin;

    /**
     * Used to render text
     */
    private TextRenderer mTextRenderer;

    /**
     * Text size. Zero means 4 x 8 font.
     */
    private int mTextSize = 10;
    private int mTextLeading = 0;
    private String mTextFont;

    static private int mCursorBlink = 0;

    /**
     * Color scheme (default foreground/background colors).
     */
    private ColorScheme mColorScheme = BaseTextRenderer.defaultColorScheme;

    private Paint mForegroundPaint;

    private Paint mBackgroundPaint;

    private boolean mUseCookedIme;
    private boolean mUseDirectCookedIme;

    /**
     * Our terminal emulator.
     */
    private TerminalEmulator mEmulator;

    /**
     * The number of rows of text to display.
     */
    private int mRows;

    /**
     * The number of columns of text to display.
     */
    private int mColumns;

    /**
     * The number of columns that are visible on the display.
     */

    private int mVisibleColumns;

    /*
     * The number of rows that are visible on the view
     */
    private int mVisibleRows;

    /**
     * The top row of text to display. Ranges from -activeTranscriptRows to 0
     */
    private int mTopRow;

    private int mLeftColumn;

    private static final int CURSOR_BLINK_PERIOD = 1000;

    private boolean mCursorVisible = true;

    private boolean mIsSelectingText = false;

    private boolean mBackKeySendsCharacter = false;
    private int mControlKeyCode;
    private int mFnKeyCode;
    private boolean mIsControlKeySent = false;
    private boolean mIsAltKeySent = false;
    private boolean mIsFnKeySent = false;

    private boolean mMouseTracking;

    private float mDensity;

    private float mScaledDensity;
    private static final int SELECT_TEXT_OFFSET_Y = -12;
    private int mSelXAnchor = -1;
    private int mSelYAnchor = -1;
    private int mSelX1 = -1;
    private int mSelY1 = -1;
    private int mSelX2 = -1;
    private int mSelY2 = -1;

    /**
     * Routing alt and meta keyCodes away from the IME allows Alt key processing to work on
     * the Asus Transformer TF101.
     * It doesn't seem to harm anything else, but it also doesn't seem to be
     * required on other platforms.
     *
     * This test should be refined as we learn more.
     */
    private final static boolean sTrapAltAndMeta = Build.MODEL.contains("Transformer TF101");
    private static InputConnection mInputConnection;

    private Runnable mBlinkCursor = new Runnable() {
        public void run() {
            if (mCursorBlink != 0) {
                if (!mImeBuffer.equals("")) {
                    mCursorVisible = true;
                } else {
                    mCursorVisible = !mCursorVisible;
                }
                mHandler.removeCallbacks(this);
                mHandler.postDelayed(this, CURSOR_BLINK_PERIOD);
            } else {
                mCursorVisible = true;
            }
            // Perhaps just invalidate the character with the cursor.
            invalidate();
        }
    };

    private GestureDetector mGestureDetector;
    private GestureDetector.OnGestureListener mExtGestureListener;
    private GestureDetector.OnDoubleTapListener mDoubleTapListener;
    private Scroller mScroller;
    private Runnable mFlingRunner = new Runnable() {
        public void run() {
            if (mScroller.isFinished()) {
                return;
            }
            // Check whether mouse tracking was turned on during fling.
            if (isMouseTrackingActive()) {
                return;
            }

            boolean more = mScroller.computeScrollOffset();
            int newTopRow = mScroller.getCurrY();
            if (newTopRow != mTopRow) {
                mTopRow = newTopRow;
                invalidate();
            }

            if (more) {
                post(this);
            }

        }
    };

    /**
     *
     * A hash table of underlying URLs to implement clickable links.
     */
    private Hashtable<Integer,URLSpan[]> mLinkLayer = new Hashtable<Integer,URLSpan[]>();

    /**
     * Sends mouse wheel codes to terminal in response to fling.
     */
    private class MouseTrackingFlingRunner implements Runnable {
        private Scroller mScroller;
        private int mLastY;
        private MotionEvent mMotionEvent;

        public void fling(MotionEvent e, float velocityX, float velocityY) {
            float SCALE = 0.15f;
            mScroller.fling(0, 0,
                    -(int) (velocityX * SCALE), -(int) (velocityY * SCALE),
                    0, 0, -100, 100);
            mLastY = 0;
            mMotionEvent = e;
            post(this);
        }

        public void run() {
            if (mScroller.isFinished()) {
                return;
            }
            // Check whether mouse tracking was turned off during fling.
            if (!isMouseTrackingActive()) {
                return;
            }

            boolean more = mScroller.computeScrollOffset();
            int newY = mScroller.getCurrY();
            for (; mLastY < newY; mLastY++) {
                sendMouseEventCode(mMotionEvent, 65);
            }
            for (; mLastY > newY; mLastY--) {
                sendMouseEventCode(mMotionEvent, 64);
            }

            if (more) {
                post(this);
            }
        }
    };
    private MouseTrackingFlingRunner mMouseTrackingFlingRunner = new MouseTrackingFlingRunner();

    private float mScrollRemainder;
    private TermKeyListener mKeyListener;

    private String mImeBuffer = "";
    private SpannableString mImeSpannableString = null;
    private boolean mRestartInput = false;

    /**
     * Our message handler class. Implements a periodic callback.
     */
    private final Handler mHandler = new Handler();

    /**
     * Called by the TermSession when the contents of the view need updating
     */
    private UpdateCallback mUpdateNotify = new UpdateCallback() {
        public void onUpdate() {
            doEscCtrl();
            if ( mIsSelectingText ) {
                int rowShift = mEmulator.getScrollCounter();
                mSelY1 -= rowShift;
                mSelY2 -= rowShift;
                mSelYAnchor -= rowShift;
            }
            mEmulator.clearScrollCounter();
            ensureCursorVisible();
            invalidate();
        }
    };

    /**
     * Create an <code>EmulatorView</code> for a {@link TermSession}.
     *
     * @param context The {@link Context} for the view.
     * @param session The {@link TermSession} this view will be displaying.
     * @param metrics The {@link DisplayMetrics} of the screen on which the view
     *                will be displayed.
     */
    public EmulatorView(Context context, TermSession session, DisplayMetrics metrics) {
        super(context);
        attachSession(session);
        setDensity(metrics);
        commonConstructor(context);
    }

    /**
     * Constructor called when inflating this view from XML.
     * <p>
     * You should call {@link #attachSession attachSession} and {@link
     * #setDensity setDensity} before using an <code>EmulatorView</code> created
     * using this constructor.
     */
    public EmulatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        commonConstructor(context);
    }

    /**
     * Constructor called when inflating this view from XML with a
     * default style set.
     * <p>
     * You should call {@link #attachSession attachSession} and {@link
     * #setDensity setDensity} before using an <code>EmulatorView</code> created
     * using this constructor.
     */
    public EmulatorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        commonConstructor(context);
    }

    private void commonConstructor(Context context) {
        // TODO: See if we want to use the API level 11 constructor to get new flywheel feature.
        mScroller = new Scroller(context);
        mMouseTrackingFlingRunner.mScroller = new Scroller(context);
        setHwAcceleration(mHardwareAcceleration);
        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(getResources().getConfiguration());
    }

    /**
     * Attach a {@link TermSession} to this view.
     *
     * @param session The {@link TermSession} this view will be displaying.
     */
    public void attachSession(TermSession session) {
        mTextRenderer = null;
        mForegroundPaint = new Paint();
        mBackgroundPaint = new Paint();
        mTopRow = 0;
        mLeftColumn = 0;
        mGestureDetector = new GestureDetector(this.getContext(), this);
        // mGestureDetector.setIsLongpressEnabled(false);
        setVerticalScrollBarEnabled(true);
        setFocusable(true);
        setFocusableInTouchMode(true);

        mTermSession = session;

        mKeyListener = new TermKeyListener(session);
        session.setKeyListener(mKeyListener);
        mKeyListener.setThumbCtrl(getDevBoolean(this.getContext(), "ThumbCtrl", false));
        mKeyListener.setSwapESC2HZ(getDevBoolean(this.getContext(), "SwapESC2HZ", false));
        mKeyListener.setJpYenRo(getDevBoolean(this.getContext(), "JpYenRo", false));

        // Do init now if it was deferred until a TermSession was attached
        if (mDeferInit) {
            mDeferInit = false;
            mKnownSize = true;
            initialize();
        }
    }

    /**
     * Update the screen density for the screen on which the view is displayed.
     *
     * @param metrics The {@link DisplayMetrics} of the screen.
     */
    public void setDensity(DisplayMetrics metrics) {
        if (mDensity == 0) {
            // First time we've known the screen density, so update font size
            mTextSize = (int) Math.floor(mTextSize * metrics.density);
        }
        mDensity = metrics.density;
        mScaledDensity = metrics.scaledDensity;
    }

    /**
     * Inform the view that it is now visible on screen.
     */
    public void onResume() {
        updateSize(false);
        if (mCursorBlink != 0) {
            mHandler.postDelayed(mBlinkCursor, CURSOR_BLINK_PERIOD);
        }
        if (mKeyListener != null) {
            mKeyListener.onResume();
        }
        restartInput();
    }

    /**
     * Inform the view that it is no longer visible on the screen.
     */
    public void onPause() {
        if (mCursorBlink != 0) {
            mHandler.removeCallbacks(mBlinkCursor);
        }
        if (mKeyListener != null) {
            mKeyListener.onPause();
        }
    }

    /**
     * Set this <code>EmulatorView</code>'s color scheme.
     *
     * @param scheme The {@link ColorScheme} to use (use null for the default
     *               scheme).
     * @see TermSession#setColorScheme
     * @see ColorScheme
     */
    public void setColorScheme(ColorScheme scheme) {
        if (scheme == null) {
            mColorScheme = BaseTextRenderer.defaultColorScheme;
        } else {
            mColorScheme = scheme;
        }
        mColorScheme.setDefaultCursorColors();
        updateText();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    private static int mIMEInputType = EditorInfo.TYPE_CLASS_TEXT;
    private static int mIMEInputTypeDefault = 53;
    private static boolean mIgnoreXoff = false;

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (mEmulator != null) setIME(mEmulator);
        if (mIMEInputType == EditorInfo.TYPE_CLASS_TEXT || mIMEInputType == EditorInfo.IME_NULL) {
            outAttrs.inputType = mIMEInputType;
        } else {
            outAttrs.inputType = mIMEInputType | (mUseDirectCookedIme ? EditorInfo.TYPE_CLASS_TEXT : EditorInfo.TYPE_NULL);
        }
        TermKeyListener.setUseCookedIme((outAttrs.inputType & EditorInfo.TYPE_CLASS_TEXT) > 0);
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;
        mInputConnection = new BaseInputConnection(this, true) {
            /**
             * Used to handle composing text requests
             */
            private int mCursor;
            private int mComposingTextStart;
            private int mComposingTextEnd;
            private int mSelectedTextStart;
            private int mSelectedTextEnd;

            private void sendText(CharSequence text) {
                int n = text.length();
                char c;
                try {
                    if (n == 1 && text.charAt(0) == '\n') {
                        text = "\r";
                    }
                    for(int i = 0; i < n; i++) {
                        c = text.charAt(i);
                        if (Character.isHighSurrogate(c)) {
                            int codePoint;
                            if (++i < n) {
                                codePoint = Character.toCodePoint(c, text.charAt(i));
                            } else {
                                // Unicode Replacement Glyph, aka white question mark in black diamond.
                                codePoint = '\ufffd';
                            }
                            mapAndSend(codePoint);
                        } else {
                            mapAndSend(c);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "error writing ", e);
                }
            }

            private  int charToKeyCode(int c) {
                if (c >= 'a' && c <= 'z') {
                    return (c - 'a' + KeycodeConstants.KEYCODE_A);
                } else if (c >= 'A' && c <= 'Z') {
                    return (c - 'A' + KeycodeConstants.KEYCODE_A);
                } else if (c >= '0' && c <= '9') {
                    return (c - '0' + KeycodeConstants.KEYCODE_0);
                } else if (c == ' ') {
                     return KeycodeConstants.KEYCODE_SPACE;
                } else if (c == '/') {
                     return KeycodeConstants.KEYCODE_SLASH;
                } else if (c == '\\') {
                     return KeycodeConstants.KEYCODE_BACKSLASH;
                } else if (c == ',') {
                    return KeycodeConstants.KEYCODE_COMMA;
                } else if (c == '.') {
                    return KeycodeConstants.KEYCODE_PERIOD;
                } else if (c == '<') {
                    return KeycodeConstants.KEYCODE_SHIFT_LEFT;
                } else if (c == '>') {
                    return KeycodeConstants.KEYCODE_SHIFT_RIGHT;
                }
                return c;
            }

            private void mapAndSend(int c) throws IOException {
                int key = charToKeyCode(c);
                if (mIsAltKeySent && c != key) {
                    int meta  = KeyEvent.META_ALT_ON;
                    if (c >= 'A' && c <= 'Z') {
                        meta += KeyEvent.META_SHIFT_LEFT_ON;
                    }
                    long eventTime = SystemClock.uptimeMillis();
                    KeyEvent event = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, key, 1, meta);
                    dispatchKeyEvent(event);
                } else {
                    int result = mKeyListener.mapControlChar(c);
                    if (mIgnoreXoff && result == 19) {
                        mTermSession.write(17);
                    } else if (result < TermKeyListener.KEYCODE_OFFSET) {
                        mTermSession.write(result);
                    } else {
                        mKeyListener.handleKeyCode(result - TermKeyListener.KEYCODE_OFFSET, null, getKeypadApplicationMode());
                    }
                }
                clearSpecialKeyStatus();
            }

            @Override
            public boolean beginBatchEdit() {
                if (LOG_IME) {
                    Log.w(TAG, "beginBatchEdit");
                }
                return super.beginBatchEdit();
            }

            @Override
            public boolean clearMetaKeyStates(int arg0) {
                if (LOG_IME) {
                    Log.w(TAG, "clearMetaKeyStates " + arg0);
                }
                return true;
            }

            @Override
            public boolean commitCompletion(CompletionInfo arg0) {
                if (LOG_IME) {
                    Log.w(TAG, "commitCompletion " + arg0);
                }
                return super.commitCompletion(arg0);
            }

            @Override
            public boolean endBatchEdit() {
                if (LOG_IME) {
                    Log.w(TAG, "endBatchEdit");
                }
                boolean result = super.endBatchEdit();
                if (mRestartInput) {
                    restartInput();
                    mRestartInput = false;
                }
                return true;
            }

            @Override
            public boolean finishComposingText() {
                if (LOG_IME) {
                    Log.w(TAG, "finishComposingText");
                }
                sendText(mImeBuffer);
                setImeBuffer("");
                mImeSpannableString = null;
                mComposingTextStart = 0;
                mComposingTextEnd = 0;
                mCursor = 0;
                return true;
            }

            @Override
            public int getCursorCapsMode(int reqModes) {
                if (LOG_IME) {
                    Log.w(TAG, "getCursorCapsMode(" + reqModes + ")");
                }
                int mode = 0;
                if ((reqModes & TextUtils.CAP_MODE_CHARACTERS) != 0) {
                    mode |= TextUtils.CAP_MODE_CHARACTERS;
                }
                return mode;
            }

            @Override
            public ExtractedText getExtractedText(ExtractedTextRequest arg0, int arg1) {
                if (LOG_IME) {
                    Log.w(TAG, "getExtractedText" + arg0 + "," + arg1);
                }
                ExtractedText et = new ExtractedText();
                et.text = mImeBuffer;
                return et;
            }

            @Override
            public CharSequence getTextAfterCursor(int n, int flags) {
                if (LOG_IME) {
                    Log.w(TAG, "getTextAfterCursor(" + n + "," + flags + ")");
                }
                int len = Math.min(n, mImeBuffer.length() - mCursor);
                if (len <= 0 || mCursor < 0 || mCursor >= mImeBuffer.length()) {
                    return "";
                }
                return mImeBuffer.substring(mCursor, mCursor + len);
            }

            @Override
            public CharSequence getTextBeforeCursor(int n, int flags) {
                if (LOG_IME) {
                    Log.w(TAG, "getTextBeforeCursor(" + n + "," + flags + ")");
                }
                int len = Math.min(n, mCursor);
                if (len <= 0 || mCursor < 0 || mCursor >= mImeBuffer.length()) {
                    return "";
                }
                return mImeBuffer.substring(mCursor-len, mCursor);
            }

            @Override
            public boolean performContextMenuAction(int arg0) {
                if (LOG_IME) {
                    Log.w(TAG, "performContextMenuAction" + arg0);
                }
                return true;
            }

            @Override
            public boolean performPrivateCommand(String arg0, Bundle arg1) {
                if (LOG_IME) {
                    Log.w(TAG, "performPrivateCommand" + arg0 + "," + arg1);
                }
                return true;
            }

            @Override
            public boolean reportFullscreenMode(boolean arg0) {
                if (LOG_IME) {
                    Log.w(TAG, "reportFullscreenMode" + arg0);
                }
                return true;
            }

            @Override
            public boolean commitCorrection (CorrectionInfo correctionInfo) {
                if (LOG_IME) {
                    Log.w(TAG, "commitCorrection");
                }
                return false;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (LOG_IME) {
                    Log.w(TAG, "commitText(\"" + text + "\", " + newCursorPosition + ")");
                }
                clearComposingText();
                sendText(text);
                setImeBuffer("");
                mCursor = 0;
                if ((mIme == IME_ID_SWIFT) && text.toString().matches("[-']")) {
                    restartInput();
                }
                return true;
            }

            private void clearComposingText() {
                int len = mImeBuffer.length();
                if (mComposingTextStart > len || mComposingTextEnd > len) {
                    mComposingTextEnd = mComposingTextStart = 0;
                    return;
                }
                setImeBuffer(mImeBuffer.substring(0, mComposingTextStart) +
                    mImeBuffer.substring(mComposingTextEnd));
                if (mCursor < mComposingTextStart) {
                    // do nothing
                } else if (mCursor < mComposingTextEnd) {
                    mCursor = mComposingTextStart;
                } else {
                    mCursor -= mComposingTextEnd - mComposingTextStart;
                }
                mComposingTextEnd = mComposingTextStart = 0;
                mImeSpannableString = null;
            }

            @Override
            public boolean deleteSurroundingText(int leftLength, int rightLength) {
                if (LOG_IME) {
                    Log.w(TAG, "deleteSurroundingText(" + leftLength +
                            "," + rightLength + ")");
                }
                if (leftLength > 0) {
                    for (int i = 0; i < leftLength; i++) {
                        sendKeyEvent(
                            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                    }
                } else if ((leftLength == 0) && (rightLength == 0)) {
                    // Delete key held down / repeating
                    sendKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                }
                // TODO: handle forward deletes.
                return true;
            }

            @Override
            public boolean performEditorAction(int actionCode) {
                if (LOG_IME) {
                    Log.w(TAG, "performEditorAction(" + actionCode + ")");
                }
                if (actionCode == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    // The "return" key has been pressed on the IME.
                    String text = mImeBuffer;
                    if ("".equals(text)) {
                        text = "\r";
                        if (mIme == IME_ID_SWIFT) setImeBuffer("");
                    } else {
                        clearComposingText();
                    }
                    sendText(text);
                }
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (LOG_IME) {
                    Log.w(TAG, "sendKeyEvent(" + event + ")");
                }
                // Some keys are sent here rather than to commitText.
                // In particular, del and the digit keys are sent here.
                // (And I have reports that the HTC Magic also sends Return here.)
                // As a bit of defensive programming, handle every key.
                dispatchKeyEvent(event);
                return true;
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                if (LOG_IME) {
                    Log.w(TAG, "setComposingText(\"" + text + "\", " + newCursorPosition + ")");
                }
                int len = mImeBuffer.length();
                if (mComposingTextStart > len || mComposingTextEnd > len) {
                    return false;
                }
                setImeBuffer(mImeBuffer.substring(0, mComposingTextStart) +
                    text + mImeBuffer.substring(mComposingTextEnd));
                mComposingTextEnd = mComposingTextStart + text.length();
                mCursor = newCursorPosition > 0 ? mComposingTextEnd + newCursorPosition - 1
                        : mComposingTextStart - newCursorPosition;
                if (mIme != IME_ID_GOOGLE_JA && text.length() == 0) {
                    mImeSpannableString = null;
                } else if (text instanceof SpannableString) {
                    mImeSpannableString = (SpannableString)text;
                }
                if (mIme == IME_ID_GOOGLE_JA && mIMEGoogleInput) {
                    if (text.length() > 0) {
                        clearComposingText();
                        sendText(text);
                        restartInput();
                    }
                }
                invalidate();
                return true;
            }

            @Override
            public boolean setSelection(int start, int end) {
                if (LOG_IME) {
                    Log.w(TAG, "setSelection" + start + "," + end);
                }
                int length = mImeBuffer.length();
                if (start == end && start > 0 && start < length) {
                    mSelectedTextStart = mSelectedTextEnd = 0;
                    mCursor = start;
                } else if (start < end && start > 0 && end < length) {
                    mSelectedTextStart = start;
                    mSelectedTextEnd = end;
                    mCursor = start;
                }
                return true;
            }

            @Override
            public boolean setComposingRegion(int start, int end) {
                if (LOG_IME) {
                    Log.w(TAG, "setComposingRegion " + start + "," + end);
                }
                if (mIme == IME_ID_GBOARD) {
                    restartInput();
                    return true;
                }
                int s = start < end ? start : end;
                int e = start > end ? start : end;
                if (s < e && start > 0 && e < mImeBuffer.length()) {
                    clearComposingText();
                    mComposingTextStart = s;
                    mComposingTextEnd = e;
                }
                return true;
            }

            @Override
            public CharSequence getSelectedText(int flags) {
                if (LOG_IME) {
                    Log.w(TAG, "getSelectedText " + flags);
                }
                int len = mImeBuffer.length();
                if (mSelectedTextEnd >= len || mSelectedTextStart > mSelectedTextEnd) {
                    return "";
                }
                return mImeBuffer.substring(mSelectedTextStart, mSelectedTextEnd+1);
            }

        };
        return mInputConnection;
    }

    private void setImeBuffer(String buffer) {
        if (!buffer.equals(mImeBuffer)) {
            invalidate();
        }
        mImeBuffer = buffer;
    }

    /**
     * Get the terminal emulator's keypad application mode.
     */
    public boolean getKeypadApplicationMode() {
        return mEmulator.getKeypadApplicationMode();
    }

    /**
     * Set a {@link android.view.GestureDetector.OnGestureListener
     * GestureDetector.OnGestureListener} to receive gestures performed on this
     * view.  Can be used to implement additional
     * functionality via touch gestures or override built-in gestures.
     *
     * @param listener The {@link
     *                 android.view.GestureDetector.OnGestureListener
     *                 GestureDetector.OnGestureListener} which will receive
     *                 gestures.
     */
    public void setExtGestureListener(GestureDetector.OnGestureListener listener) {
        mExtGestureListener = listener;
    }

    public void setDoubleTapListener(GestureDetector.OnDoubleTapListener listener) {
        mDoubleTapListener = listener;
    }

    /**
     * Compute the vertical range that the vertical scrollbar represents.
     */
    @Override
    protected int computeVerticalScrollRange() {
        return mEmulator.getScreen().getActiveRows();
    }

    /**
     * Compute the vertical extent of the horizontal scrollbar's thumb within
     * the vertical range. This value is used to compute the length of the thumb
     * within the scrollbar's track.
     */
    @Override
    protected int computeVerticalScrollExtent() {
        return mRows;
    }

    /**
     * Compute the vertical offset of the vertical scrollbar's thumb within the
     * horizontal range. This value is used to compute the position of the thumb
     * within the scrollbar's track.
     */
    @Override
    protected int computeVerticalScrollOffset() {
        return mEmulator.getScreen().getActiveRows() + mTopRow - mRows;
    }

    /**
     * Call this to initialize the view.
     */
    private void initialize() {
        TermSession session = mTermSession;

        updateText();

        mEmulator = session.getEmulator();
        setHwAcceleration(mHardwareAcceleration);
        session.setUpdateCallback(mUpdateNotify);

        mIMECtrlBeginBatchEditDisable = getDevBoolean(this.getContext(), "BatchEditDisable", true);
        mIMECtrlBeginBatchEditDisableHwKbdChk = getDevBoolean(this.getContext(), "BatchEditDisableHwKbdChk", false);

        setIME(mEmulator);
        requestFocusFromTouch();
    }

    public void setAmbiWidth(int width) {
        UnicodeTranscript.setAmbiWidth(width);
    }

    @SuppressLint("NewApi")
    private boolean mHardwareAcceleration = true;
    public void setHwAcceleration(boolean mode) {
        if (mHardwareAcceleration == mode) return;
        mHardwareAcceleration = mode;
        if (AndroidCompat.SDK < 11) return;
        if (mode) {
            this.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else {
            this.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    /**
     * Get the {@link TermSession} corresponding to this view.
     *
     * @return The {@link TermSession} object for this view.
     */
    public TermSession getTermSession() {
        return mTermSession;
    }

    /**
     * Get the width of the visible portion of this view.
     *
     * @return The width of the visible portion of this view, in pixels.
     */
    public int getVisibleWidth() {
        return mVisibleWidth;
    }

    /**
     * Get the height of the visible portion of this view.
     *
     * @return The height of the visible portion of this view, in pixels.
     */
    public int getVisibleHeight() {
        return mVisibleHeight;
    }

    /**
     * Gets the visible number of rows for the view, useful when updating Ptysize with the correct number of rows/columns
     * @return The rows for the visible number of rows, this is calculate in updateSize(int w, int h), please call
     * updateSize(true) if the view changed, to get the correct calculation before calling this.
     */
    public int getVisibleRows()
    {
      return mVisibleRows;
    }

    /**
     * Gets the visible number of columns for the view, again useful to get when updating PTYsize
     * @return the columns for the visisble view, please call updateSize(true) to re-calculate this if the view has changed
     */
    public int getVisibleColumns()
    {
      return mVisibleColumns;
    }


    /**
     * Page the terminal view (scroll it up or down by <code>delta</code>
     * screenfuls).
     *
     * @param delta The number of screens to scroll. Positive means scroll down,
     *        negative means scroll up.
     */
    public void page(int delta) {
        mTopRow =
                Math.min(0, Math.max(-(mEmulator.getScreen()
                        .getActiveTranscriptRows()), mTopRow + mRows * delta));
        invalidate();
    }

    /**
     * Page the terminal view horizontally.
     *
     * @param deltaColumns the number of columns to scroll. Positive scrolls to
     *        the right.
     */
    public void pageHorizontal(int deltaColumns) {
        mLeftColumn =
                Math.max(0, Math.min(mLeftColumn + deltaColumns, mColumns
                        - mVisibleColumns));
        invalidate();
    }

    /**
     * Sets the text size, which in turn sets the number of rows and columns.
     *
     * @param fontSize the new font size, in density-independent pixels.
     */
    public void setTextSize(float fontSize) {
        if (fontSize == 0) {
            fontSize = getTextSize((Activity)this.getContext());
        }
        mTextSize = (int) Math.floor(fontSize * mDensity);
        updateText();
    }

    static public float getTextSize(Activity activity) {
        final float splashWidth = 30.4f;
        final float fontSizeMax = 20.0f;
        float fontSize = 14.0f;
        Point point = new Point();
        Display display = (activity.getWindowManager().getDefaultDisplay());
        display.getSize(point);
        Resources resources = activity.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float fs = point.x / metrics.density / splashWidth;
        fs = (float)Math.floor(fs) + (((fs - Math.floor(fs)) > 0.5f) ? 0.5f : 0f);
        if (fs > fontSize) fontSize = fs;
        if (fontSize > fontSizeMax) fontSize = fontSizeMax;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("fontsize", (String.valueOf(fontSize)));
        editor.apply();
        return fontSize;
    }

    public void setTextLeading(int fontLeading) {
        mTextLeading = fontLeading;
        updateText();
    }

    public void setTextFont(String fontFile) {
        mTextFont = fontFile;
        updateText();
    }

    /**
     * Sets the IME mode ("cooked" or "raw").
     *
     * @param useCookedIME Whether the IME should be used in cooked mode.
     */
    public void setUseCookedIME(boolean useCookedIME) {
        mUseCookedIme = useCookedIME;
    }

    public void setUseDirectCookedIME(boolean useCookedIME) {
        mUseDirectCookedIme = useCookedIME;
    }
    /**
     * Returns true if mouse events are being sent as escape sequences to the terminal.
     */
    public boolean isMouseTrackingActive() {
        return mEmulator.getMouseTrackingMode() != 0 && mMouseTracking;
    }

    /**
     * Send a single mouse event code to the terminal.
     */
    private void sendMouseEventCode(MotionEvent e, int button_code) {
        int x = (int)(e.getX() / mCharacterWidth) + 1;
        int y = (int)((e.getY()-mTopOfScreenMargin) / mCharacterHeight) + 1;
        // Clip to screen, and clip to the limits of 8-bit data.
        boolean out_of_bounds =
            x < 1 || y < 1 ||
            x > mColumns || y > mRows ||
            x > 255-32 || y > 255-32;
        //Log.d(TAG, "mouse button "+x+","+y+","+button_code+",oob="+out_of_bounds);
        if(button_code < 0 || button_code > 255-32) {
            Log.e(TAG, "mouse button_code out of range: "+button_code);
            return;
        }
        if(!out_of_bounds) {
            byte[] data = {
                '\033', '[', 'M',
                (byte)(32 + button_code),
                (byte)(32 + x),
                (byte)(32 + y) };
            mTermSession.write(data, 0, data.length);
        }
    }

    // Begin GestureDetector.OnGestureListener methods

    public boolean onSingleTapUp(MotionEvent e) {
        return true;
    }

    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (mExtGestureListener != null && mExtGestureListener.onSingleTapUp(e)) {
            return true;
        }

        if (isMouseTrackingActive()) {
            sendMouseEventCode(e, 0); // BTN1 press
            sendMouseEventCode(e, 3); // release
        }

        requestFocusFromTouch();
        return false;
    }

    public boolean onDoubleTap(MotionEvent e) {
        Log.w(TAG, "onDoubleTap");
        if (mDoubleTapListener != null && mDoubleTapListener.onDoubleTap(e)) {
            return true;
        }
        return false;
    }

    public boolean onDoubleTapEvent(MotionEvent e) {
        if (mDoubleTapListener != null && mDoubleTapListener.onDoubleTapEvent(e)) {
            return true;
        }
        Log.w(TAG, "onDoubleTapEvent");
        return false;
    }

    public void onLongPress(MotionEvent e) {
        // XXX hook into external gesture listener
        showContextMenu();
    }

    public int getCharacterHeight() {
        return mCharacterHeight;
    }

    public int getCharacterWidth() {
        return mCharacterHeight;
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2,
            float distanceX, float distanceY) {
        if (mExtGestureListener != null && mExtGestureListener.onScroll(e1, e2, distanceX, distanceY)) {
            return true;
        }
        if (Math.abs(distanceX) > Math.abs(distanceY)) return true;

        distanceY += mScrollRemainder;
        int deltaRows = (int) (distanceY / mCharacterHeight);
        mScrollRemainder = distanceY - deltaRows * mCharacterHeight;

        if (isMouseTrackingActive()) {
            // Send mouse wheel events to terminal.
            for (; deltaRows>0; deltaRows--) {
                sendMouseEventCode(e1, 65);
            }
            for (; deltaRows<0; deltaRows++) {
                sendMouseEventCode(e1, 64);
            }
            return true;
        }

        mTopRow =
            Math.min(0, Math.max(-(mEmulator.getScreen()
                    .getActiveTranscriptRows()), mTopRow + deltaRows));
        invalidate();

        return true;
    }

    public boolean onJumpTapDown(MotionEvent e1, MotionEvent e2) {
       // Scroll to bottom
       mTopRow = 0;
       invalidate();
       return true;
    }

    public boolean onJumpTapUp(MotionEvent e1, MotionEvent e2) {
        // Scroll to top
        mTopRow = -mEmulator.getScreen().getActiveTranscriptRows();
        invalidate();
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
        if (mExtGestureListener != null && mExtGestureListener.onFling(e1, e2, velocityX, velocityY)) {
            return true;
        }
        if (Math.abs(velocityX) > Math.abs(velocityY)) return true;

        mScrollRemainder = 0.0f;
        if (isMouseTrackingActive()) {
            mMouseTrackingFlingRunner.fling(e1, velocityX, velocityY);
        } else {
            float SCALE = 0.25f;
            mScroller.fling(0, mTopRow,
                    -(int) (velocityX * SCALE), -(int) (velocityY * SCALE),
                    0, 0,
                    -mEmulator.getScreen().getActiveTranscriptRows(), 0);
            // onScroll(e1, e2, 0.1f * velocityX, -0.1f * velocityY);
            post(mFlingRunner);
        }
        return true;
    }

    public void onShowPress(MotionEvent e) {
        if (mExtGestureListener != null) {
            mExtGestureListener.onShowPress(e);
        }
    }

    public boolean onDown(MotionEvent e) {
        if (mExtGestureListener != null && mExtGestureListener.onDown(e)) {
            return true;
        }
        mScrollRemainder = 0.0f;
        return true;
    }

    // End GestureDetector.OnGestureListener methods

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mIsSelectingText) {
            return onTouchEventWhileSelectingText(ev);
        } else {
            return mGestureDetector.onTouchEvent(ev);
        }
    }

    private boolean onTouchEventWhileSelectingText(MotionEvent ev) {
        int action = ev.getAction();
        int cx = (int)(ev.getX() / mCharacterWidth);
        int cy = Math.max(0,
                (int)((ev.getY() + SELECT_TEXT_OFFSET_Y * mScaledDensity)
                        / mCharacterHeight) + mTopRow);
        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mSelXAnchor = cx;
            mSelYAnchor = cy;
            mSelX1 = cx;
            mSelY1 = cy;
            mSelX2 = mSelX1;
            mSelY2 = mSelY1;
            break;
        case MotionEvent.ACTION_MOVE:
        case MotionEvent.ACTION_UP:
            int minx = Math.min(mSelXAnchor, cx);
            int maxx = Math.max(mSelXAnchor, cx);
            int miny = Math.min(mSelYAnchor, cy);
            int maxy = Math.max(mSelYAnchor, cy);
            mSelX1 = minx;
            mSelY1 = miny;
            mSelX2 = maxx;
            mSelY2 = maxy;
            if (action == MotionEvent.ACTION_UP) {
                ClipboardManagerCompat clip = ClipboardManagerCompatFactory
                        .getManager(getContext().getApplicationContext());
                clip.setText(getSelectedText().trim());
                toggleSelectingText();
            }
            invalidate();
            break;
        default:
            toggleSelectingText();
            invalidate();
            break;
        }
        return true;
    }

    /**
     * Called when a key is pressed in the view.
     *
     * @param keyCode The keycode of the key which was pressed.
     * @param event A {@link KeyEvent} describing the event.
     * @return Whether the event was handled.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LOG_KEY_EVENTS) {
            Log.w(TAG, "onKeyDown " + keyCode);
        }
        if (mIme == IME_ID_SWIFT && !mHaveFullHwKeyboard && keyCode == KeyEvent.KEYCODE_DEL) {
            mTermSession.write("\u007f");
            restartInput();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            mInputConnection.performEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED);
            restartInput();
            return true;
        } else if (handleControlKey(keyCode, true)) {
            return true;
        } else if (handleFnKey(keyCode, true)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            if (! isInterceptedSystemKey(keyCode) ) {
                // Don't intercept the system keys
                return super.onKeyDown(keyCode, event);
            }
        }


        // Translate the keyCode into an ASCII character.

        try {
            int oldCombiningAccent = mKeyListener.getCombiningAccent();
            int oldCursorMode = mKeyListener.getCursorMode();
            mKeyListener.keyDown(keyCode, event, getKeypadApplicationMode(),
                    TermKeyListener.isEventFromToggleDevice(event));
            if (mKeyListener.getCombiningAccent() != oldCombiningAccent
                    || mKeyListener.getCursorMode() != oldCursorMode) {
                invalidate();
            }
        } catch (IOException e) {
            // Ignore I/O exceptions
        }
        return true;
    }

    /** Do we want to intercept this system key? */
    private boolean isInterceptedSystemKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_BACK && mBackKeySendsCharacter;
    }

    /**
     * Called when a key is released in the view.
     *
     * @param keyCode The keycode of the key which was released.
     * @param event A {@link KeyEvent} describing the event.
     * @return Whether the event was handled.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (LOG_KEY_EVENTS) {
            Log.w(TAG, "onKeyUp " + keyCode);
        }
        if (mIme == IME_ID_SWIFT && mHaveFullHwKeyboard &&keyCode == KeyEvent.KEYCODE_DEL) {
            return true;
        }
        if (handleControlKey(keyCode, false)) {
            return true;
        } else if (handleFnKey(keyCode, false)) {
            return true;
        } else if (isSystemKey(keyCode, event)) {
            // Don't intercept the system keys
            if ( ! isInterceptedSystemKey(keyCode) ) {
                return super.onKeyUp(keyCode, event);
            }
        }

        mKeyListener.keyUp(keyCode, event);
        clearSpecialKeyStatus();
        if (keyCode == KeycodeConstants.KEYCODE_ESCAPE && mViCooperativeMode) {
            setImeShortcutsAction(mIMEInputTypeDefault);
        }
        return true;
    }

    private static boolean mIMECtrlBeginBatchEditDisable = true;
    private static boolean mIMECtrlBeginBatchEditDisableHwKbdChk = false;
    private static boolean mAltGrave = true;
    private static boolean mAltEsc = false;
    private static boolean mAltSpace = false;
    private static boolean mCtrlSpace = false;
    private static boolean mShiftSpace = false;
    private static boolean mZenkakuHankaku = false;
    private static boolean mGrave = false;
    private static boolean mSwitchCharset = false;
    private static boolean mCtrlJ = false;
    private static boolean mHaveFullHwKeyboard = false;
    private static int mIMEShortcutsAction = 0;

    public void setHaveFullHwKeyboard(boolean mode) {
        mHaveFullHwKeyboard = mode;
    }

    public void onConfigurationChangedToEmulatorView(Configuration newConfig) {
        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(newConfig);
        // FIXME: SwiftKey's physicla keyboard handling is too buggy.
        if (mIme == IME_ID_SWIFT && mHaveFullHwKeyboard) {
            setIMEInputType(EditorInfo.TYPE_CLASS_TEXT);
            if (mIMEShortcutsAction >= 50 && mIMEShortcutsAction <= 60) doImeShortcutsAction();
        }
    }

    private boolean checkHaveFullHwKeyboard(Configuration c) {
        return (c.keyboard == Configuration.KEYBOARD_QWERTY) &&
            (c.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO);
    }

    public void sendFnKeyCode() {
        // FIXME:
        mIsFnKeySent = true;
        mKeyListener.handleFnKey(true);
        mKeyListener.handleFnKey(false);
        invalidate();
    }

    public void sendAltKeyCode() {
        // FIXME:
        mIsAltKeySent = true;
        mKeyListener.handleAltKey(true);
        mKeyListener.handleAltKey(false);
        invalidate();
    }

    public void sendControlKeyCode() {
        // FIXME:
        mIsControlKeySent = true;
        mKeyListener.handleControlKey(true);
        mKeyListener.handleControlKey(false);
        invalidate();
    }

    public int getControlKeyState() {
        return mKeyListener.getControlKeyState();
    }

    public void setControlKeyState(int state) {
        mKeyListener.setControlKeyState(state);
        invalidate();
    }

    private void pasteClipboard() {
        ClipboardManagerCompat clip = ClipboardManagerCompatFactory.getManager(this.getContext());
        if (clip.hasText() == false) {
            return;
        }
        CharSequence paste = clip.getText();
        mTermSession.write(paste.toString());
    }

    public void setIMECtrlBeginBatchEditDisable(boolean mode) {
        mIMECtrlBeginBatchEditDisable = mode;
        if (getDevBoolean(this.getContext(), "BatchEditDisable", false)) mIMECtrlBeginBatchEditDisable = true;
    }

    public void setIMECtrlBeginBatchEditDisableHwKbdChk(boolean mode) {
        mIMECtrlBeginBatchEditDisableHwKbdChk = mode;
    }

    private boolean IMECtrlBeginBatchEditDisable() {
        boolean flag = mIMECtrlBeginBatchEditDisableHwKbdChk ? mHaveFullHwKeyboard : true;
        return flag && mIMECtrlBeginBatchEditDisable;
    }

    @SuppressLint("NewApi")
    private void doEscCtrl() {
        while (true) {
            int ctrl = mEmulator.getEscCtrlMode();
            if (ctrl == -1) return;
            if ((mHaveFullHwKeyboard == false) && (ctrl <= 2)) {
                continue;
            }
            switch (ctrl) {
            case 0:
            case 70:
                doHideSoftKeyboard();
                break;
            case 1:
            case 71:
                doShowSoftKeyboard();
                break;
            case 2:
            case 72:
                doToggleSoftKeyboard();
                break;
            case 3:
                ((Activity)this.getContext()).onKeyUp(0xffff0003, null);
                break;
            case 4:
                ((Activity)this.getContext()).onKeyUp(0xffff0004, null);
                break;
            case 5:
                ((Activity)this.getContext()).onKeyUp(0xffff0005, null);
                break;
            case 6:
                ((Activity)this.getContext()).onKeyUp(0xffff0006, null);
                break;
            case 7:
                break;
            case 8:
                break;
            case 9:
                break;
            case 30:
                ((Activity)this.getContext()).onKeyUp(0xffff0030, null);
                break;
            case 33:
                ((Activity)this.getContext()).onKeyUp(0xffff0033, null);
                break;
            case 333:
                ((Activity)this.getContext()).onKeyUp(0xffff0333, null);
                break;
            case 50:
                setIMEInputType(EditorInfo.TYPE_CLASS_TEXT);
                break;
            case 51:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                break;
            case 52:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_URI);
                break;
            case 53:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD, true);
                break;
            case 54:
                setIMEInputType(EditorInfo.TYPE_NULL);
                break;
            case 55:
                doImeShortcutsAction();
                break;
            case 56:
                ((Activity)this.getContext()).onKeyUp(0xffff0056, null);
                break;
            case 57:
                ((Activity)this.getContext()).onKeyUp(0xffff0057, null);
                break;
            case 58:
                ((Activity)this.getContext()).onKeyUp(0xffff0058, null);
                break;
            case 61:
                ((Activity)this.getContext()).onKeyUp(0xffff0061, null);
                break;
            case 62:
                ((Activity)this.getContext()).onKeyUp(0xffff0062, null);
                break;
            case 63:
                ((Activity)this.getContext()).onKeyUp(0xffff0063, null);
                break;
            case 1061:
                if (mIme == IME_ID_GBOARD && mHaveFullHwKeyboard) {
                    ((Activity)this.getContext()).onKeyUp(0xffff0061, null);
                }
                break;
            case 500:
                setIMEInputType(EditorInfo.TYPE_NUMBER_VARIATION_NORMAL);
                break;
            case 501:
                setIMEInputType(EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
                break;
            case 502:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                break;
            case 503:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT);
                break;
            case 504:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_FILTER);
                break;
            case 505:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_LONG_MESSAGE);
                break;
            case 506:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_NORMAL);
                break;
            case 507:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
                break;
            case 508:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME);
                break;
            case 509:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_PHONETIC);
                break;
            case 510:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
                break;
            case 511:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
                break;
            case 512:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_URI);
                break;
            case 513:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                break;
            case 514:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);
                break;
            case 515:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
                break;
            case 516:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD);
                break;
            case 10:
                pasteClipboard();
                break;
            case 11:
                mIMECtrlBeginBatchEditDisable = !getDevBoolean(this.getContext(), "BatchEditDisable", false);
                setDevBoolean(this.getContext(), "BatchEditDisable", mIMECtrlBeginBatchEditDisable);
                break;
            case 12:
                mIMECtrlBeginBatchEditDisableHwKbdChk = !getDevBoolean(this.getContext(), "BatchEditDisableHwKbdChk", false);
                setDevBoolean(this.getContext(), "BatchEditDisableHwKbdChk", mIMECtrlBeginBatchEditDisableHwKbdChk);
                break;
            case 13:
                break;
            case 14:
                break;
            case 15:
                break;
            case 99:
                testFunc();
                break;
            case 100:
                boolean tc = !getDevBoolean(this.getContext(), "ThumbCtrl", false);
                setDevBoolean(this.getContext(), "ThumbCtrl", tc);
                if (mKeyListener != null) mKeyListener.setThumbCtrl(tc);
                break;
            case 101:
                boolean sez = !getDevBoolean(this.getContext(), "SwapESC2HZ", false);
                setDevBoolean(this.getContext(), "SwapESC2HZ", sez);
                if (mKeyListener != null) mKeyListener.setSwapESC2HZ(sez);
                break;
            case 102:
                boolean yr = !getDevBoolean(this.getContext(), "JpYenRo", false);
                setDevBoolean(this.getContext(), "JpYenRo", yr);
                if (mKeyListener != null) mKeyListener.setJpYenRo(yr);
                break;
            case 990:
                ((Activity)this.getContext()).onKeyUp(0xffff0990, null);
                break;
            case 998:
            case 999:
                int key = ctrl == 998 ? 0xffff0998 : 0xffff0999;
                ((Activity)this.getContext()).onKeyUp(key, null);
                break;
            case 1000:
                ((Activity)this.getContext()).onKeyUp(0xffff0000, null);
                break;
            case 1001:
                ((Activity)this.getContext()).onKeyUp(0xffff1001, null);
                break;
            case 1010:
                ((Activity)this.getContext()).onKeyUp(0xffff1010, null);
                break;
            case 1011:
                ((Activity)this.getContext()).onKeyUp(0xffff1011, null);
                break;
            case 1002:
                ((Activity)this.getContext()).onKeyUp(0xffff1002, null);
                break;
            case 1003:
                ((Activity)this.getContext()).onKeyUp(KeycodeConstants.KEYCODE_MENU, null);
                break;
            case 1006:
                ((Activity)this.getContext()).onKeyUp(0xffff1006, null);
                break;
            case 1007:
                ((Activity)this.getContext()).onKeyUp(0xffff1007, null);
                break;
            case 1008:
                ((Activity)this.getContext()).onKeyUp(0xffff1008, null);
                break;
            case 1009:
                ((Activity)this.getContext()).onKeyUp(0xffff1009, null);
                break;
            case 9998:
                ((Activity)this.getContext()).onKeyUp(0xffff9998, null);
                break;
            default:
                break;
            }
        }
    }

    private void testFunc() {
    }

    // com.android.inputmethod.latin/.LatinIME
    private final static String IME_ANDROID = ".*com.android.inputmethod.latin.*";
    // com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
    private final static String IME_GBOARD = "com.google.android.inputmethod.latin/.*";
    // com.justsystems.atokmobile.tv.service/.AtokInputMethodService
    private final static String IME_ATOK = "com.justsystems.atokmobile..*";
    // com.google.android.inputmethod.japanese/.MozcService
    private final static String IME_GOOGLE_JA = ".*/.MozcService.*";
    private final static String IME_SWIFT = "com.touchtype.swiftkey.*";
    private static String IME_GOOGLE_JA_CLONE = "";
    private final int IME_ID_ERROR     = 0;
    private final int IME_ID_NONE      = 1;
    private final int IME_ID_NORMAL    = 2;
    private final int IME_ID_ATOK      = 3;
    private final int IME_ID_GOOGLE_JA = 4;
    private final int IME_ID_GBOARD    = 5;
    private final int IME_ID_WNNLAB    = 6;
    private final int IME_ID_SWIFT     = 7;
    // jp.co.omronsoft.openwnn/.OpenWnnJAJP
    // jp.co.omronsoft.wnnlab/.standardcommon.IWnnLanguageSwitcher
    // jp.co.omronsoft.iwnnime.ml/.standardcommon.IWnnLanguageSwitcher
    // private final static String IME_WNN = "jp.co.omronsoft..*wnn.*";
    private final static String IME_WNNLAB = "jp.co.omronsoft.wnnlab.*";
    private static int mIme = -1;
    private int setIME(TerminalEmulator view) {
        if (view == null) return IME_ID_ERROR;
        String defime = Settings.Secure.getString(this.getContext().getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        int ime = IME_ID_NORMAL;
        if (defime.matches(IME_GBOARD)) {
            ime = IME_ID_GBOARD;
        } else if (defime.matches(IME_SWIFT)) {
            ime = IME_ID_SWIFT;
        } else if (defime.matches(IME_GOOGLE_JA)) {
            ime = IME_ID_GOOGLE_JA;
        }
        if (mIme != ime) {
            TranscriptScreen transcriptScreen = view.getScreen();
            if (transcriptScreen != null) {
                transcriptScreen.setIME(ime);
                mIme = ime;
            }
        }
        return ime;
    }

    public String setDevString(Context context, String key, String value) {
        SharedPreferences pref = context.getSharedPreferences("dev", Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        editor.putString(key, value);
        editor.apply();
        return value;
    }

    public String getDevString(Context context, String key, String defValue) {
        SharedPreferences pref = context.getSharedPreferences("dev", Context.MODE_PRIVATE);
        return pref.getString(key, defValue);
    }

    private static boolean mIMEGoogleInput = false;
    private void setIMEInputType(int attr, boolean google) {
        mIMEGoogleInput = google;
        if (mIMEInputType == attr) return;
        mIMEInputType = attr;
        restartInput();
    }

    static public void setIMEInputTypeDefault(int attr) {
        mIMEInputTypeDefault = attr;
    }

    private void setIMEInputType(int attr) {
        setIMEInputType(attr, false);
    }

    private void restartInput() {
        Activity activity = (Activity)this.getContext();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.restartInput(this);
    }

    public void restartInputGoogleIme() {
        if (mIme != IME_ID_GOOGLE_JA || mIMEGoogleInput) return;
        restartInput();
    }

    private void doShowSoftKeyboard() {
        Activity activity = (Activity)this.getContext();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(this, InputMethodManager.SHOW_FORCED);
    }

    private void doHideSoftKeyboard() {
        Activity activity = (Activity)this.getContext();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    private void doToggleSoftKeyboard() {
        Activity activity = (Activity)this.getContext();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
    }

    private void doInputMethodPicker() {
        Activity activity = (Activity)this.getContext();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == 0) return true;
        if (sTrapAltAndMeta) {
            boolean altEsc = mKeyListener.getAltSendsEsc();
            boolean altOn = (event.getMetaState() & KeyEvent.META_ALT_ON) != 0;
            boolean metaOn = (event.getMetaState() & KeyEvent.META_META_ON) != 0;
            boolean altPressed = (keyCode == KeyEvent.KEYCODE_ALT_LEFT)
                    || (keyCode == KeyEvent.KEYCODE_ALT_RIGHT);
            boolean altActive = mKeyListener.isAltActive();
            if (altEsc && (altOn || altPressed || altActive || metaOn)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    return onKeyDown(keyCode, event);
                } else {
                    return onKeyUp(keyCode, event);
                }
            }
        }

        if (preIMEShortcuts(keyCode, event)) {
            return true;
        }

        if (handleHardwareControlKey(keyCode, event)) {
            return true;
        }

        if (mKeyListener.isCtrlActive()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return onKeyDown(keyCode, event);
            } else {
                return onKeyUp(keyCode, event);
            }
        }

        if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.getAction() == KeyEvent.ACTION_UP)) {
            ((Activity)this.getContext()).onKeyUp(0xffffffc0, null);
        }
        return super.onKeyPreIme(keyCode, event);
    };

    private boolean preIMEShortcuts(int keyCode, KeyEvent event) {
        int stat = getPreIMEShortcutsStatus(keyCode, event);
        if (stat == PREIME_SHORTCUT_ACTION) {
            doImeShortcutsAction();
        } else if (stat == PREIME_SHORTCUT_ACTION2) {
            doImeShortcutsAction(1261);
        } else if (stat == PREIME_SHORTCUT_ACTION_MENU) {
            ((Activity)this.getContext()).onKeyUp(stat, null);
        }
        return  (stat > PREIME_SHORTCUT_ACTION_NULL);
    }

    public final static int PREIME_SHORTCUT_ACTION_NULL = 0;
    public final static int PREIME_SHORTCUT_ACTION_DONE = 1;
    public final static int PREIME_SHORTCUT_ACTION      = 2;
    public final static int PREIME_SHORTCUT_ACTION2     = 3;
    public final static int PREIME_SHORTCUT_ACTION_MENU = KeyEvent.KEYCODE_MENU;
    static public int getPreIMEShortcutsStatus(int keyCode, KeyEvent event) {
        int keyAction = event.getAction();
        boolean ctrlOn = (event.getMetaState() & KeyEvent.META_CTRL_ON) != 0;
        boolean altOn = (event.getMetaState() & KeyEvent.META_ALT_ON) != 0;
        boolean metaOn = (event.getMetaState() & (KeyEvent.META_META_ON)) != 0;
        boolean shiftOn = (event.getMetaState() & (KeyEvent.META_SHIFT_ON)) != 0;
        boolean metashiftOn = metaOn | shiftOn;
        boolean altPressed = (keyCode == KeyEvent.KEYCODE_ALT_LEFT) || (keyCode == KeyEvent.KEYCODE_ALT_RIGHT);

        // KeyEvent.KEYCODE_ZENKAKU_HANKAKU 211
        boolean ag = mAltGrave && ((keyCode == 211) || (keyCode == KeycodeConstants.KEYCODE_GRAVE)) && (altOn && !ctrlOn && !metashiftOn);
        boolean aesc = (keyCode == KeycodeConstants.KEYCODE_ESCAPE) && (altOn && !ctrlOn && !metashiftOn);
        boolean ae = mAltEsc && aesc;
        boolean as = mAltSpace && (keyCode == KeycodeConstants.KEYCODE_SPACE) && (altOn && !ctrlOn && !metashiftOn);
        boolean cesc = (keyCode == KeycodeConstants.KEYCODE_ESCAPE) && (!altOn && ctrlOn && !metashiftOn);
        boolean cs = mCtrlSpace && (keyCode == KeycodeConstants.KEYCODE_SPACE) && (!altOn && ctrlOn && !metashiftOn);
        boolean ss = mShiftSpace && (keyCode == KeycodeConstants.KEYCODE_SPACE) && (!altOn && !ctrlOn && shiftOn && !metaOn);
        boolean zh = mZenkakuHankaku && (keyCode == 211) && (!ctrlOn && !altOn && !metashiftOn);  // KeyEvent.KEYCODE_ZENKAKU_HANKAKU;
        boolean grave = mGrave && (keyCode == KeycodeConstants.KEYCODE_GRAVE) && (!ctrlOn && !altOn && !metashiftOn);
        boolean sc = mSwitchCharset && (keyCode == KeycodeConstants.KEYCODE_SWITCH_CHARSET);
        boolean cj = mCtrlJ && keyCode == (KeycodeConstants.KEYCODE_J) && (!altOn && ctrlOn && !metashiftOn);
        if (cesc) {
            if (keyAction == KeyEvent.ACTION_DOWN) return PREIME_SHORTCUT_ACTION_MENU;
            return PREIME_SHORTCUT_ACTION_DONE;
        }
        if (!mAltEsc && aesc) {
            if (keyAction == KeyEvent.ACTION_UP) return PREIME_SHORTCUT_ACTION2;
            return PREIME_SHORTCUT_ACTION_DONE;
        }
        if (metaOn && !altOn && !ctrlOn && !shiftOn) {
            if (keyCode == KeycodeConstants.KEYCODE_ESCAPE) {
                if (keyAction == KeyEvent.ACTION_UP) return PREIME_SHORTCUT_ACTION2;
                return PREIME_SHORTCUT_ACTION_DONE;
            }
        }
        if (((ag || ae || zh || sc || grave || cj) && keyAction == KeyEvent.ACTION_DOWN) || ((as || cs || ss) && keyAction == KeyEvent.ACTION_UP)) {
            return PREIME_SHORTCUT_ACTION;
        }
        if (((ag || ae || zh || sc || grave || cj) && keyAction == KeyEvent.ACTION_UP)) return PREIME_SHORTCUT_ACTION_DONE;
        if (((as || cs || ss) && keyAction == KeyEvent.ACTION_DOWN)) return PREIME_SHORTCUT_ACTION_DONE;
        if ((mAltEsc || mAltSpace || mAltGrave) && altPressed) return PREIME_SHORTCUT_ACTION_DONE;
        return PREIME_SHORTCUT_ACTION_NULL;
    }

    public void doImeShortcutsAction() {
        doImeShortcutsAction(mIMEShortcutsAction);
    }

    public void doImeShortcutsAction(int action) {
        if (action == 0) {
            doToggleSoftKeyboard();
        } else if (action == 1261) {
            ((Activity)this.getContext()).onKeyUp(0xffff0056, null);
        } else if (action == 1361) {
            ((Activity)this.getContext()).onKeyUp(0xffff0061, null);
        } else if (action == 1362) {
            ((Activity)this.getContext()).onKeyUp(0xffff0062, null);
        } else if (action == 1365) {
            ((Activity)this.getContext()).onKeyUp(0xffff0063, null);
        } else {
            int imeType = mUseCookedIme ? EditorInfo.TYPE_CLASS_TEXT : EditorInfo.TYPE_NULL;
            if (mIMEInputType == imeType) {
                setImeShortcutsAction(action);
            } else {
                setIMEInputType(imeType);
            }
        }
    }

    public void setImeShortcutsAction(int action) {
        if (action == 60 || action == 1360) {
            action = mIMEInputTypeDefault;
        }
        switch (action) {
            case 50:
                setIMEInputType(EditorInfo.TYPE_CLASS_TEXT);
                break;
            case 51:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
                break;
            case 52:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_URI);
                break;
            case 53:
                setIMEInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD, true);
                break;
            case 54:
                setIMEInputType(EditorInfo.TYPE_NULL);
                break;
            default:
                setIMEInputType(EditorInfo.TYPE_CLASS_TEXT);
                break;
        }
    }

    public void setPreIMEShortcut(String key, boolean value) {
        if (key.equals("AltGrave")) {
            mAltGrave = value;
        } else if (key.equals("AltEsc")) {
            mAltEsc = value;
        } else if (key.equals("AltSpace")) {
            mAltSpace = value;
        } else if (key.equals("CtrlSpace")) {
            mCtrlSpace = value;
        } else if (key.equals("ShiftSpace")) {
            mShiftSpace = value;
        } else if (key.equals("ZENKAKU_HANKAKU")) {
            mZenkakuHankaku = value;
        } else if (key.equals("GRAVE")) {
            mGrave = value;
        } else if (key.equals("SWITCH_CHARSET")) {
            mSwitchCharset = value;
        } else if (key.equals("CtrlJ")) {
            mCtrlJ = value;
        }
    }

    public void setPreIMEShortcutsAction(int action) {
        mIMEShortcutsAction = action;
    }

    private static boolean mViCooperativeMode = false;
    public void setViCooperativeMode(int value) {
       mViCooperativeMode = ((value & 2) != 0);
    }

    public boolean setDevBoolean(Context context, String key, boolean value) {
        SharedPreferences pref = context.getSharedPreferences("dev", Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        editor.putBoolean(key, value);
        editor.apply();
        return value;
    }

    public boolean getDevBoolean(Context context, String key, boolean defValue) {
        SharedPreferences pref = context.getSharedPreferences("dev", Context.MODE_PRIVATE);
        return pref.getBoolean(key, defValue);
    }

    private boolean handleControlKey(int keyCode, boolean down) {
        if (keyCode == mControlKeyCode) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleControlKey " + keyCode);
            }
            mKeyListener.handleControlKey(down);
            invalidate();
            return true;
        }
        return false;
    }

    private boolean handleHardwareControlKey(int keyCode, KeyEvent event) {
        if (keyCode == KeycodeConstants.KEYCODE_CTRL_LEFT ||
            keyCode == KeycodeConstants.KEYCODE_CTRL_RIGHT) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleHardwareControlKey " + keyCode);
            }
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            mKeyListener.handleHardwareControlKey(down);
            invalidate();
            return true;
        }
        return false;
    }

    private boolean handleFnKey(int keyCode, boolean down) {
        if (keyCode == mFnKeyCode) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleFnKey " + keyCode);
            }
            mKeyListener.handleFnKey(down);
            invalidate();
            return true;
        }
        return false;
    }

    private boolean isSystemKey(int keyCode, KeyEvent event) {
        return event.isSystem();
    }

    private void clearSpecialKeyStatus() {
        if (mIsControlKeySent) {
            mIsControlKeySent = false;
            mKeyListener.handleControlKey(false);
            invalidate();
        }
        if (mIsAltKeySent) {
            mIsAltKeySent = false;
            mKeyListener.handleAltKey(false);
            invalidate();
        }
        if (mIsFnKeySent) {
            mIsFnKeySent = false;
            mKeyListener.handleFnKey(false);
            invalidate();
        }
    }

    private void updateText() {
        ColorScheme scheme = mColorScheme;
        if (mTextSize > 0) {
            mTextRenderer = new PaintRenderer(mTextSize, scheme, mTextFont, mTextLeading);
        }
        else {
            mTextRenderer = new Bitmap4x8FontRenderer(getResources(), scheme);
        }

        mForegroundPaint.setColor(scheme.getForeColor());
        mBackgroundPaint.setColor(scheme.getBackColor());
        mCharacterWidth = mTextRenderer.getCharacterWidth();
        mCharacterHeight = mTextRenderer.getCharacterHeight();

        updateSize(true);
    }

    /**
     * This is called during layout when the size of this view has changed. If
     * you were just added to the view hierarchy, you're called with the old
     * values of 0.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (mTermSession == null) {
            // Not ready, defer until TermSession is attached
            mDeferInit = true;
            return;
        }

        if (!mKnownSize) {
            mKnownSize = true;
            initialize();
        } else {
            updateSize(false);
        }
    }

    private void updateSize(int w, int h) {
        mColumns = Math.max(1, (int) (((float) w) / mCharacterWidth));
        mVisibleColumns = Math.max(1, (int) (((float) mVisibleWidth) / mCharacterWidth));

        mTopOfScreenMargin = mTextRenderer.getTopMargin();
        mRows = Math.max(1, (h - mTopOfScreenMargin) / mCharacterHeight);
        mVisibleRows = Math.max(1, (mVisibleHeight - mTopOfScreenMargin) / mCharacterHeight);
        mTermSession.updateSize(mColumns, mRows);

        // Reset our paging:
        mTopRow = 0;
        mLeftColumn = 0;

        invalidate();
    }

    /**
     * Update the view's idea of its size.
     *
     * @param force Whether a size adjustment should be performed even if the
     *              view's size has not changed.
     */
    public void updateSize(boolean force) {
        //Need to clear saved links on each display refresh
        mLinkLayer.clear();
        if (mKnownSize) {
            int w = getWidth();
            int h = getHeight();
            // Log.w("Term", "(" + w + ", " + h + ")");
            if (force || w != mVisibleWidth || h != mVisibleHeight) {
                mVisibleWidth = w;
                mVisibleHeight = h;
                updateSize(mVisibleWidth, mVisibleHeight);
            }
        }
    }

    /**
     * Draw the view to the provided {@link Canvas}.
     *
     * @param canvas The {@link Canvas} to draw the view to.
     */
    final int CTRL_MODE_MASK = 3 << TextRenderer.MODE_CTRL_SHIFT;
    @Override
    protected void onDraw(Canvas canvas) {
        updateSize(false);

        if (mEmulator == null) {
            // Not ready yet
            return;
        }

        int w = getWidth();
        int h = getHeight();

        boolean reverseVideo = mEmulator.getReverseVideo();
        mTextRenderer.setReverseVideo(reverseVideo);

        Paint backgroundPaint =
                reverseVideo ? mForegroundPaint : mBackgroundPaint;
        canvas.drawRect(0, 0, w, h, backgroundPaint);
        float x = -mLeftColumn * mCharacterWidth;
        float y = mCharacterHeight + mTopOfScreenMargin;
        int endLine = mTopRow + mRows;
        int cx = mEmulator.getCursorCol();
        int cy = mEmulator.getCursorRow();
        boolean cursorVisible = mCursorVisible && mEmulator.getShowCursor();
        String effectiveImeBuffer = mImeBuffer;
        int combiningAccent = mKeyListener.getCombiningAccent();
        if (combiningAccent != 0) {
            effectiveImeBuffer += String.valueOf((char) combiningAccent);
        }
        int cursorStyle = mKeyListener.getCursorMode();
        if ((cursorStyle & CTRL_MODE_MASK) == 0) {
            ((Activity)this.getContext()).onKeyUp(0xffff1364, null);
        }

        for (int i = mTopRow; i < endLine; i++) {
            int cursorX = -1;
            if (i == cy && cursorVisible) {
                cursorX = cx;
            }
            int selx1 = -1;
            int selx2 = -1;
            if ( i >= mSelY1 && i <= mSelY2 ) {
                if ( i == mSelY1 ) {
                    selx1 = mSelX1;
                }
                if ( i == mSelY2 ) {
                    selx2 = mSelX2;
                } else {
                    selx2 = mColumns;
                }
            }
            mEmulator.getScreen().drawText(i, canvas, x, y, mTextRenderer, cursorX, selx1, selx2, effectiveImeBuffer, cursorStyle, mImeSpannableString);
            y += mCharacterHeight;
        }
    }

    private void ensureCursorVisible() {
        mTopRow = 0;
        if (mVisibleColumns > 0) {
            int cx = mEmulator.getCursorCol();
            int visibleCursorX = mEmulator.getCursorCol() - mLeftColumn;
            if (visibleCursorX < 0) {
                mLeftColumn = cx;
            } else if (visibleCursorX >= mVisibleColumns) {
                mLeftColumn = (cx - mVisibleColumns) + 1;
            }
        }
    }

    /**
     * Toggle text selection mode in the view.
     */
    public void toggleSelectingText() {
        mIsSelectingText = ! mIsSelectingText;
        setVerticalScrollBarEnabled( ! mIsSelectingText );
        if ( ! mIsSelectingText ) {
            mSelX1 = -1;
            mSelY1 = -1;
            mSelX2 = -1;
            mSelY2 = -1;
        }
    }

    /**
     * Whether the view is currently in text selection mode.
     */
    public boolean getSelectingText() {
        return mIsSelectingText;
    }

    /**
     * Get selected text.
     *
     * @return A {@link String} with the selected text.
     */
    public String getSelectedText() {
        return mEmulator.getSelectedText(mSelX1, mSelY1, mSelX2, mSelY2);
    }

    /**
     * Send a Ctrl key event to the terminal.
     */
    public void sendControlKey() {
        mIsControlKeySent = true;
        mKeyListener.handleControlKey(true);
        invalidate();
    }

    /**
     * Send an Fn key event to the terminal.  The Fn modifier key can be used to
     * generate various special characters and escape codes.
     */
    public void sendFnKey() {
        mIsFnKeySent = true;
        mKeyListener.handleFnKey(true);
        invalidate();
    }

    /**
     * Set the key code to be sent when the Back key is pressed.
     */
    public void setBackKeyCharacter(int keyCode) {
        mKeyListener.setBackKeyCharacter(keyCode);
        mBackKeySendsCharacter = (keyCode != 0);
    }

    /**
     * Set whether to prepend the ESC keycode to the character when when pressing
     * the ALT Key.
     * @param flag
     */
    public void setAltSendsEsc(boolean flag) {
        mKeyListener.setAltSendsEsc(flag);
    }

    public void setIgnoreXoff(boolean flag) {
        mIgnoreXoff = flag;
        mKeyListener.setmIgnoreXoff(flag);
    }

    static public void setCursorBlink(int blink) {
        mCursorBlink = blink;
    }

    static public void setCursorHeight(int height) {
        PaintRenderer.setCursorHeight(height);
    }

    static public void setCursorColor(int color) {
        ColorScheme.setDefaultCursorColor(color);
    }

    static public void setForceFlush(boolean flush) {
        int chr = flush ? 0 : 128;
        TranscriptScreen.setForceFlush(chr);
    }

    /**
     * Set the keycode corresponding to the Ctrl key.
     */
    public void setControlKeyCode(int keyCode) {
        mControlKeyCode = keyCode;
    }

    /**
     * Set the keycode corresponding to the Fn key.
     */
    public void setFnKeyCode(int keyCode) {
        mFnKeyCode = keyCode;
    }

    public void setTermType(String termType) {
         mKeyListener.setTermType(termType);
    }

    /**
     * Set whether mouse events should be sent to the terminal as escape codes.
     */
    public void setMouseTracking(boolean flag) {
        mMouseTracking = flag;
    }

     public String getTranscriptScreenText() {
         if (mEmulator == null) return null;
         TranscriptScreen ts = mEmulator.getScreen();
         if (ts == null) return null;
         return ts.getTranscriptScreenText();
    }

    public String getTranscriptText() {
        if (mEmulator == null) return null;
        TranscriptScreen ts = mEmulator.getScreen();
        if (ts == null) return null;
        return ts.getTranscriptText();
    }

    public String getTranscriptCurrentText() {
        if (mEmulator == null) return null;
        TranscriptScreen ts = mEmulator.getScreen();
        if (ts == null) return null;
        return ts.getSelectedText(0, mTopRow, mVisibleColumns, mVisibleRows+mTopRow-1);
    }
}
