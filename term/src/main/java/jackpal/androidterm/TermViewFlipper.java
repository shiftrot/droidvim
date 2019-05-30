/*
 * Copyright (C) 2011 Steven Luo
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

package jackpal.androidterm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

import jackpal.androidterm.compat.AndroidCompat;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.UpdateCallback;
import jackpal.androidterm.util.TermSettings;

public class TermViewFlipper extends ViewFlipper implements Iterable<View> {
    private Context context;
    private Toast mToast;
    private LinkedList<UpdateCallback> callbacks;
    private boolean mStatusBarVisible = false;

    private int mCurWidth;
    private int mCurHeight;
    private Rect mVisibleRect = new Rect();
    private Rect mWindowRect = new Rect();
    private LayoutParams mChildParams = null;
    private boolean mRedoLayout = false;

    /**
     * True if we must poll to discover if the view has changed size.
     * This is the only known way to detect the view changing size due to
     * the IME being shown or hidden in API level <= 7.
     */
    private final boolean mbPollForWindowSizeChange = (AndroidCompat.SDK < 8);
    private static final int SCREEN_CHECK_PERIOD = 1000;
    private final Handler mHandler = new Handler();
    private Runnable mCheckSize = new Runnable() {
        public void run() {
            adjustChildSize();
            mHandler.postDelayed(this, SCREEN_CHECK_PERIOD);
        }
    };

    class ViewFlipperIterator implements Iterator<View> {
        int pos = 0;

        public boolean hasNext() {
            return (pos < getChildCount());
        }

        public View next() {
            return getChildAt(pos++);
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public TermViewFlipper(Context context) {
        super(context);
        commonConstructor(context);
    }

    public TermViewFlipper(Context context, AttributeSet attrs) {
        super(context, attrs);
        commonConstructor(context);
    }

    private void commonConstructor(Context context) {
        this.context = context;
        callbacks = new LinkedList<UpdateCallback>();

        updateVisibleRect();
        Rect visible = mVisibleRect;
        mChildParams = new LayoutParams(visible.width(), visible.height(),
                Gravity.TOP | Gravity.LEFT);
        // FIXME (mWindowMode)
        mFunctionBarSize = new PrefValue(context).getInt("functinbar_size", mFunctionBarSize);
    }

    public void updatePrefs(TermSettings settings) {
        boolean statusBarVisible = settings.showStatusBar();
        int[] colorScheme = settings.getColorScheme();
        setBackgroundColor(colorScheme[1]);
        mStatusBarVisible = statusBarVisible;
    }

    public Iterator<View> iterator() {
        return new ViewFlipperIterator();
    }

    public void addCallback(UpdateCallback callback) {
        callbacks.add(callback);
    }

    public void removeCallback(UpdateCallback callback) {
        callbacks.remove(callback);
    }

    private void notifyChange() {
        for (UpdateCallback callback : callbacks) {
            callback.onUpdate();
        }
        mCurrentDisplayChild = getDisplayedChild();
    }

    private static int mCurrentDisplayChild = 0;

    static public int getCurrentDisplayChild() {
        return mCurrentDisplayChild;
    }

    public void onPause() {
        mCurrentDisplayChild = getDisplayedChild();
        if (mbPollForWindowSizeChange) {
            mHandler.removeCallbacks(mCheckSize);
        }
        pauseCurrentView();
    }

    public void onResume() {
        if (mbPollForWindowSizeChange) {
            mCheckSize.run();
        }
        resumeCurrentView();
        if (mCurrentDisplayChild >= 0 && getChildCount() >= mCurrentDisplayChild) {
            setDisplayedChild(mCurrentDisplayChild);
        }
    }

    public void pauseCurrentView() {
        EmulatorView view = (EmulatorView) getCurrentView();
        if (view == null) {
            return;
        }
        view.onPause();
    }

    public void resumeCurrentView() {
        EmulatorView view = (EmulatorView) getCurrentView();
        if (view == null) {
            return;
        }
        view.onResume();
        view.requestFocus();
    }

    private void showTitle() {
        if (getChildCount() <= 1) {
            return;
        }

        EmulatorView view = (EmulatorView) getCurrentView();
        if (view == null) {
            return;
        }
        TermSession session = view.getTermSession();
        if (session == null) {
            return;
        }

        String title = String.format(Locale.getDefault(), context.getString(R.string.window_title) + " %1$d", getDisplayedChild() + 1);
        if (session instanceof GenericTermSession) {
            title = ((GenericTermSession) session).getTitle(title);
        }

        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(context, title, Toast.LENGTH_SHORT);
        mToast.setGravity(Gravity.CENTER, 0, 0);
        mToast.show();
    }

    @Override
    public void showPrevious() {
        pauseCurrentView();
        super.showPrevious();
        showTitle();
        resumeCurrentView();
        notifyChange();
    }

    @Override
    public void showNext() {
        pauseCurrentView();
        super.showNext();
        showTitle();
        resumeCurrentView();
        notifyChange();
    }

    @Override
    public void setDisplayedChild(int position) {
        pauseCurrentView();
        super.setDisplayedChild(position);
        showTitle();
        resumeCurrentView();
        notifyChange();
    }

    @Override
    public void addView(View v, int index) {
        super.addView(v, index, mChildParams);
    }

    @Override
    public void addView(View v) {
        super.addView(v, mChildParams);
    }

    private void updateVisibleRect() {
        Rect visible = mVisibleRect;
        Rect window = mWindowRect;

        /* Get rectangle representing visible area of this view, as seen by
           the activity (takes other views in the layout into account, but
           not space used by the IME) */
        getGlobalVisibleRect(visible);

        /* Get rectangle representing visible area of this window (takes
           IME into account, but not other views in the layout) */
        getWindowVisibleDisplayFrame(window);
        if (mWindowMode) return;
        /* Work around bug in getWindowVisibleDisplayFrame on API < 10, and
           avoid a distracting height change as status bar hides otherwise */
        if (!mStatusBarVisible) {
            window.top = 0;
        }

        // Clip visible rectangle's top to the visible portion of the window
        if (visible.width() == 0 && visible.height() == 0) {
            visible.left = window.left;
            visible.top = window.top;
        } else {
            if (visible.left < window.left) {
                visible.left = window.left;
            }
            if (visible.top < window.top) {
                visible.top = window.top;
            }
        }
        // Always set the bottom of the rectangle to the window bottom
        /* XXX This breaks with a split action bar, but if we don't do this,
           it's possible that the view won't resize correctly on IME hide */
        visible.right = window.right;
        visible.bottom = window.bottom;
        visible.bottom -= mEditTextView ? mEditTextViewSize : 0;
        visible.bottom -= mFunctionBar ? mFunctionBarSize : 0;
    }

    private void adjustChildSize() {
        adjustChildSize(false);
    }

    private void adjustChildSize(boolean force) {
        updateVisibleRect();
        Rect visible = mVisibleRect;
        int width = visible.width();
        int height = visible.height();

        if (force || mCurWidth != width || mCurHeight != height) {
            mCurWidth = width;
            mCurHeight = height;

            LayoutParams params = mChildParams;
            params.width = width;
            params.height = height;
            for (View v : this) {
                updateViewLayout(v, params);
            }
            mRedoLayout = true;

            EmulatorView currentView = (EmulatorView) getCurrentView();
            if (currentView != null) {
                currentView.updateSize(false);
            }
        }
    }

    /**
     * Called when the view changes size.
     * (Note: Not always called on Android < 2.2)
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        adjustChildSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mWindowMode) adjustChildSize();
        if (mRedoLayout) {
            requestLayout();
            mRedoLayout = false;
        }
        super.onDraw(canvas);
    }

    public void redraw() {
        invalidate();
    }

    private static boolean mWindowMode = true;
    public void setDrawMode(boolean windowMode) {
        mWindowMode = windowMode;
    }

    private int mEditTextViewSize = 0;
    private boolean mEditTextView = false;

    public void setEditTextViewSize(int size) {
        if (mWindowMode) return;
        if (size > 0) {
            PrefValue pv = new PrefValue(context);
            if (pv.getInt("edit_text_view_size", mEditTextViewSize) != size)
                pv.setInt("edit_text_view_size", size);
            mEditTextViewSize = size;
        }
    }

    public void setEditTextView(boolean bool) {
        if (mWindowMode) return;
        if (mEditTextView == bool) return;
        mEditTextView = bool;
        adjustChildSize();
    }

    private int mFunctionBarSize = 0;
    private boolean mFunctionBar = true;

    public void setFunctionBarSize(int size) {
        if (mWindowMode) return;
        if (size > 0) {
            PrefValue pv = new PrefValue(context);
            if (pv.getInt("functinbar_size", mFunctionBarSize) != size)
                pv.setInt("functinbar_size", size);
            mFunctionBarSize = size;
        }
    }

    public void setFunctionBar(boolean bool) {
        if (mWindowMode) return;
        if (mFunctionBar == bool) return;
        mFunctionBar = bool;
        adjustChildSize();
    }

}
