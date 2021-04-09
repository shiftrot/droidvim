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

package jackpal.androidterm.util;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.view.KeyEvent;

import java.util.Locale;

import jackpal.androidterm.R;
import jackpal.androidterm.SyncFileObserver;
import jackpal.androidterm.Term;
import jackpal.androidterm.TermService;
import jackpal.androidterm.compat.AndroidCompat;

/**
 * Terminal emulator settings
 */
public class TermSettings {
    private SharedPreferences mPrefs;

    private int mStatusBar;
    private boolean mFunctionBar;
    private boolean mFunctionBarNavigationButton;
    private int mCursorDirectionCtrl;
    private boolean mOnelineTextBox;
    private boolean mOnelineTextBoxEsc;
    private boolean mOnelineTextBoxCr;
    private int mActionBarMode;
    private int mOrientation;
    private int mCursorStyle;
    private int mCursorBlink;
    private String mCursorColor;
    private float mFontSize;
    private int mFontLeading;
    private String mFontFile;
    private int mAmbiWidth;
    private boolean mHwAcceleration;
    private boolean mForceFlushDrawText;
    private int mTheme;
    private int mColorId;
    private int mKeepScreenTime;
    private boolean mKeepScreenAtStartup;
    private int mIMEColor;
    private boolean mUTF8ByDefault;
    private int mActionBarIconAction;
    private int mActionBarPlusAction;
    private int mActionBarMinusAction;
    private int mActionBarXAction;
    private int mActionBarInvertAction;
    private int mActionBarUserAction;
    private int mDoubleTapAction;
    private int mRightDoubleTapAction;
    private int mLeftDoubleTapAction;
    private int mBottomDoubleTapAction;
    private int mTopDoubleTapAction;
    private int mBackKeyAction;
    private int mControlKeyId;
    private int mFnKeyId;
    private int mUseCookedIME;
    private int mUseDirectCookedIME;
    private String mMRUCommand;
    private String mExternalAppId;
    private int mExternalAppButtonMode;
    private boolean mShowDotfiles;
    private boolean mUseFilesAppButton;
    private String mFilerAppId;
    private int mFilerAppButtonMode;
    private int mDropboxFilePicker;
    private int mGoogleDriveFilePicker;
    private int mOneDriveFilePicker;
    private boolean mCloudStorageReadCheck;
    private boolean mCloudStorageWriteCheck;
    private int mHtmlViewerMode;
    private String mShell;
    private String mFailsafeShell;
    private String mInitialCommand;
    private String mIntentCommand;
    private String mTermType;
    private boolean mCloseOnExit;
    private boolean mProot;
    private String mHomePath;

    private boolean mAltUses8bitMSB;
    private boolean mAltSendsEsc;

    private boolean mIgnoreXoff;
    private boolean mBackAsEsc;

    private boolean mMouseTracking;
    private boolean mVolumeAsCursor;
    private boolean mPinchInOut;

    private boolean mUseKeyboardShortcuts;
    private boolean mAutoHideFunctionbar;
    private int mImeShortcutsAction;
    private int mImeDefaultInputtype;
    private int mViCooperativeMode;
    private boolean mForceNormalInputModeToPhysicalKeyboard;

    public static final String STATUSBAR_ICON_KEY = "statusbar_icon";
    private static final String STATUSBAR_KEY = "statusbar";
    private static final String FUNCTIONBAR_KEY = "functionbar";
    private static final String FUNCTIONBAR_NAVIAGATION_BUTTON_KEY = "functionbar_navigation_button";
    private static final String CURSOR_DIRECTION_CTRL = "cursor_direction_ctrl_mode";
    private static final String ONELINE_TEXTBOX_KEY = "oneline_textbox";
    private static final String ONELINE_TEXTBOX_ESC_KEY = "oneline_textbox_esc";
    private static final String ONELINE_TEXTBOX_CR_KEY = "oneline_textbox_cr";
    private static final String ACTIONBAR_KEY = "actionbar";
    private static final String ORIENTATION_KEY = "orientation";
    public static final String CURSORSTYLE_KEY = "cursorstyle";
    private static final String CURSORBLINK_KEY = "cursorblink";
    private static final String CURSORCOLOR_KEY = "cursorcolor";
    private static final String FONTSIZE_KEY = "fontsize";
    private static final String FONTLEADING_KEY = "fontleading";
    private static final String FONTFILE_KEY = "fontfile";
    public static final String AMBIWIDTH_KEY = "ambiwidth";
    private static final String FORCE_FLUSH_DRAW_KEY = "force_flush_drawtext";
    public static final String THEME_KEY = "theme";
    private static final String KEEP_SCREEN_AT_STARTUP_KEY = "keepscreen_at_startup";
    private static final String KEEP_SCREEN_TIME_KEY = "keepscreentime";
    public static final String COLOR_KEY = "color";
    private static final String IMECOLOR_KEY = "composingtext";
    private static final String UTF8_KEY = "utf8_by_default";
    private static final String HWACCELERATION_KEY = "hw_acceleration_by_default";
    private static final String ACTIONBAR_ICON_KEY = "functionbar_diamond_action_rev3";
    private static final String ACTIONBAR_PLUS_KEY = "actionbar_plus_action";
    private static final String ACTIONBAR_MINUS_KEY = "actionbar_minus_action";
    private static final String ACTIONBAR_X_KEY = "actionbar_x_action";
    private static final String ACTIONBAR_USER_KEY = "actionbar_user_action";
    private static final String ACTIONBAR_INVERT_KEY = "actionbar_invert_action";
    private static final String DOUBLE_TAP_KEY = "double_tap_action";
    private static final String RIGHT_DOUBLE_TAP_KEY = "right_double_tap_action";
    private static final String LEFT_DOUBLE_TAP_KEY = "left_double_tap_action";
    private static final String BOTTOM_DOUBLE_TAP_KEY = "bottom_double_tap_action";
    private static final String TOP_DOUBLE_TAP_KEY = "top_double_tap_action";
    private static final String BACKACTION_KEY = "backaction";
    private static final String CONTROLKEY_KEY = "controlkey";
    private static final String FNKEY_KEY = "fnkey";
    private static final String IME_KEY = "ime";
    private static final String IME_DIRECT_KEY = "ime_direct_input_method";
    private static final String MRU_COMMAND_KEY = "mru_command";
    private static final String EXTERNAL_APP_ID_KEY = "external_app_package_name";
    private static final String EXTERNAL_APP_BUTTON_MODE_KEY = "external_app_action_mode";
    private static final String FILER_APP_BUTTON_MODE_KEY = "filer_app_action_mode";
    private static final String FILES_BUTTON_KEY = "use_app_files_button_rev2";
    private static final String DROPBOX_FILE_PICKER_KEY = "cloud_dropbox_filepicker";
    private static final String GOOGLEDRIVE_FILE_PICKER_KEY = "cloud_googledrive_filepicker";
    private static final String ONEDRIVE_FILE_PICKER_KEY = "cloud_onedrive_filepicker";
    private static final String CLOUD_STRAGE_READ_CHECK_KEY = "cloud_storage_read_check";
    private static final String CLOUD_STRAGE_WRITE_CHECK_KEY = "cloud_storage_write_check";
    private static final String HTML_VIEWER_MODE_KEY = "html_viewer_mode";
    private static final String SHELL_KEY = "android_shell_path";
    private static final String INITIALCOMMAND_KEY = "initialcommand_rev6";
    private static final String INTENTCOMMAND_KEY = "intent_command";
    private static final String TERMTYPE_KEY = "termtype";
    private static final String CLOSEONEXIT_KEY = "close_window_on_process_exit";
    private static final String PROOT_KEY = "proot";
    private static final String HOMEPATH_KEY = "home_path";
    private static final String ALT_USES_8BIT_META = "alt_uses_8bit_meta";
    private static final String ALT_SENDS_ESC = "alt_sends_esc";
    private static final String IGNORE_XON = "ignore_xoff";
    private static final String BACK_AS_ESC = "back_as_esc";
    private static final String MOUSE_TRACKING = "mouse_tracking";
    private static final String VOLUME_AS_CURSOR = "volume_as_cursor";
    private static final String PINCH_IN_OUT = "pinch_in_out";
    private static final String USE_KEYBOARD_SHORTCUTS = "use_keyboard_shortcuts";
    private static final String AUTO_HIDE_FUNCTIONBAR = "auto_hide_functionbar";
    private static final String IME_SHORTCUTS_ACTION = "ime_shortcuts_action_rev2";
    private static final String IME_DEFAULT_INPUTTYPE = "ime_default_inputtype";
    private static final String IME_VI_COOPERATIVE_MODE = "vi_cooperative_mode";
    public static final String FORCE_NOMAL_INPUT_TO_PHYSICAL_KEYBOARD = "force_normal_input_to_physical_keyboard";
    private static String SHOW_DOTFILES_KEY;

    public static final int WHITE = 0xffffffff;
    public static final int BLACK = 0xff000000;
    public static final int BLUE = 0xff344ebd;
    public static final int GREEN = 0xff00ff00;
    public static final int AMBER = 0xffffb651;
    public static final int RED = 0xffff0113;
    public static final int HOLO_BLUE = 0xff33b5e5;
    public static final int SOLARIZED_FG = 0xff657b83;
    public static final int SOLARIZED_BG = 0xfffdf6e3;
    public static final int SOLARIZED_DARK_FG = 0xff839496;
    public static final int SOLARIZED_DARK_BG = 0xff002b36;
    public static final int LINUX_CONSOLE_WHITE = 0xffaaaaaa;

    // foreground color, background color
    public static final int[][] COLOR_SCHEMES = {
            {BLACK, WHITE},
            {WHITE, BLACK},
            {WHITE, BLUE},
            {GREEN, BLACK},
            {AMBER, BLACK},
            {RED, BLACK},
            {HOLO_BLUE, BLACK},
            {SOLARIZED_FG, SOLARIZED_BG},
            {SOLARIZED_DARK_FG, SOLARIZED_DARK_BG},
            {LINUX_CONSOLE_WHITE, BLACK},
            {BLACK, SOLARIZED_BG}
    };

    public static final int ACTION_BAR_MODE_NONE = 0;
    public static final int ACTION_BAR_MODE_ALWAYS_VISIBLE = 1;
    public static final int ACTION_BAR_MODE_HIDES = 2;
    private static final int ACTION_BAR_MODE_MAX = 3;

    public static final int ORIENTATION_UNSPECIFIED = 0;
    public static final int ORIENTATION_LANDSCAPE = 1;
    public static final int ORIENTATION_PORTRAIT = 2;

    /**
     * An integer not in the range of real key codes.
     */
    public static final int KEYCODE_NONE = -1;

    public static final int CONTROL_KEY_ID_NONE = 7;
    public static final int[] CONTROL_KEY_SCHEMES = {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_AT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_CAMERA,
            KEYCODE_NONE
    };

    public static final int FN_KEY_ID_NONE = 7;
    public static final int[] FN_KEY_SCHEMES = {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_AT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_CAMERA,
            KEYCODE_NONE
    };

    public static final int BACK_KEY_STOPS_SERVICE = 0;
    public static final int BACK_KEY_CLOSES_WINDOW = 1;
    public static final int BACK_KEY_CLOSES_ACTIVITY = 2;
    public static final int BACK_KEY_SENDS_ESC = 3;
    public static final int BACK_KEY_SENDS_TAB = 4;
    public static final int BACK_KEY_TOGGLE_IME = 5;
    public static final int BACK_KEY_TOGGLE_IME_ESC = 6;
    private static final int BACK_KEY_MAX = 6;
    private static final int ACTIONBAR_KEY_MAX = 65535;
    private static final int IME_SHORTCUTS_ACTION_MAX = 65535;
    private static final int VI_COOPRATIVE_MODE_MAX = 3;

    public TermSettings(Resources res, SharedPreferences prefs) {
        readDefaultPrefs(res);
        readPrefs(prefs);
    }

    private void readDefaultPrefs(Resources res) {
        mStatusBar = Integer.parseInt(res.getString(R.string.pref_statusbar_default));
        mFunctionBar = res.getBoolean(R.bool.pref_functionbar_default);
        mFunctionBarNavigationButton = res.getBoolean(R.bool.pref_functionbar_navigation_button_default);
        mCursorDirectionCtrl = Integer.parseInt(res.getString(R.string.pref_cursor_direction_ctrl_mode_default));
        mOnelineTextBox = res.getBoolean(R.bool.pref_one_line_textbox_default);
        mOnelineTextBoxEsc = res.getBoolean(R.bool.pref_one_line_textbox_esc_default);
        mOnelineTextBoxCr = res.getBoolean(R.bool.pref_one_line_textbox_cr_default);
        mActionBarMode = res.getInteger(R.integer.pref_actionbar_default);
        mOrientation = res.getInteger(R.integer.pref_orientation_default);
        mCursorStyle = Integer.parseInt(res.getString(R.string.pref_cursorstyle_default));
        mCursorBlink = Integer.parseInt(res.getString(R.string.pref_cursorblink_default));
        mCursorColor = res.getString(R.string.pref_cursorcolor_default);
        mFontSize = Float.parseFloat(res.getString(R.string.pref_fontsize_default));
        mFontLeading = Integer.parseInt(res.getString(R.string.pref_fontleading_default));
        mFontFile = res.getString(R.string.pref_fontfile_default);
        mAmbiWidth = Integer.parseInt(res.getString(R.string.pref_ambiguous_width_default));
        mTheme = Integer.parseInt(res.getString(R.string.pref_theme_default));
        mKeepScreenTime = Integer.parseInt(res.getString(R.string.pref_keep_screen_default));
        mKeepScreenAtStartup = res.getBoolean(R.bool.pref_keepscreen_at_startup_default);
        mColorId = Integer.parseInt(res.getString(R.string.pref_color_default));
        mIMEColor = Integer.parseInt(res.getString(R.string.pref_composingtext_default));
        mUTF8ByDefault = res.getBoolean(R.bool.pref_utf8_by_default_default);
        mHwAcceleration = res.getBoolean(R.bool.pref_hw_acceleration_by_default);
        mForceFlushDrawText = res.getBoolean(R.bool.pref_force_flush_drawtext);
        mActionBarIconAction = Integer.parseInt(res.getString(R.string.pref_actionbar_diamond_default));
        mActionBarPlusAction = Integer.parseInt(res.getString(R.string.pref_actionbar_plus_default));
        mActionBarMinusAction = Integer.parseInt(res.getString(R.string.pref_actionbar_minus_default));
        mActionBarXAction = Integer.parseInt(res.getString(R.string.pref_actionbar_x_default));
        mActionBarUserAction = Integer.parseInt(res.getString(R.string.pref_actionbar_user_default));
        mActionBarInvertAction = Integer.parseInt(res.getString(R.string.pref_actionbar_invert_default));
        mDoubleTapAction = Integer.parseInt(res.getString(R.string.pref_double_tap_default));
        mRightDoubleTapAction = Integer.parseInt(res.getString(R.string.pref_right_double_tap_default));
        mLeftDoubleTapAction = Integer.parseInt(res.getString(R.string.pref_left_double_tap_default));
        mBottomDoubleTapAction = Integer.parseInt(res.getString(R.string.pref_bottom_double_tap_default));
        mTopDoubleTapAction = Integer.parseInt(res.getString(R.string.pref_top_double_tap_default));
        mMRUCommand = res.getString(R.string.pref_mru_command_default);
        SHOW_DOTFILES_KEY = res.getString(R.string.pref_key_show_dotfiles);
        mShowDotfiles = res.getBoolean(R.bool.pref_show_dotfiles_default);
        mExternalAppId = res.getString(R.string.pref_external_app_id_default);
        mExternalAppButtonMode = Integer.parseInt(res.getString(R.string.pref_external_app_action_mode_default));
        mUseFilesAppButton = res.getBoolean(R.bool.pref_use_app_files_button_default);
        mFilerAppId = res.getString(R.string.pref_filer_app_id_default);
        mFilerAppButtonMode = Integer.parseInt(res.getString(R.string.pref_filer_app_action_mode_default));
        mDropboxFilePicker = Integer.parseInt(res.getString(R.string.pref_cloud_dropbox_filepicker_default));
        mGoogleDriveFilePicker = Integer.parseInt(res.getString(R.string.pref_cloud_googledrive_filepicker_default));
        mOneDriveFilePicker = Integer.parseInt(res.getString(R.string.pref_cloud_onedrive_filepicker_default));
        mCloudStorageReadCheck = res.getBoolean(R.bool.pref_cloud_storage_read_check_default);
        mCloudStorageWriteCheck = res.getBoolean(R.bool.pref_cloud_storage_write_check_default);
        mHtmlViewerMode = Integer.parseInt(res.getString(R.string.pref_html_viewer_mode_default));
        mBackKeyAction = Integer.parseInt(res.getString(R.string.pref_backaction_default));
        mControlKeyId = Integer.parseInt(res.getString(R.string.pref_controlkey_default));
        mFnKeyId = Integer.parseInt(res.getString(R.string.pref_fnkey_default));
        mUseCookedIME = Integer.parseInt(res.getString(R.string.pref_ime_default));
        mUseDirectCookedIME = Integer.parseInt(res.getString(R.string.pref_ime_default));
        mFailsafeShell = res.getString(R.string.pref_shell_default);
        mShell = res.getString(R.string.pref_shell_default);
        mInitialCommand = res.getString(R.string.pref_initialcommand_default);
        mIntentCommand = res.getString(R.string.pref_intent_command_default);
        mTermType = res.getString(R.string.pref_termtype_default);
        mCloseOnExit = res.getBoolean(R.bool.pref_close_window_on_process_exit_default);
        mProot = res.getBoolean(R.bool.pref_proot_default);
        // the mHomePath default is set dynamically in readPrefs()
        mAltUses8bitMSB = res.getBoolean(R.bool.pref_alt_uses_8bit_meta_default);
        mAltSendsEsc = res.getBoolean(R.bool.pref_alt_sends_esc_default);
        mIgnoreXoff = res.getBoolean(R.bool.pref_ignore_xoff_default);
        mBackAsEsc = res.getBoolean(R.bool.pref_back_as_esc_default);
        mMouseTracking = res.getBoolean(R.bool.pref_mouse_tracking_default);
        mVolumeAsCursor = res.getBoolean(R.bool.pref_volume_as_cursor_tracking_default);
        mPinchInOut = res.getBoolean(R.bool.pref_pinch_in_out_default);
        mUseKeyboardShortcuts = res.getBoolean(R.bool.pref_use_keyboard_shortcuts_default);
        mAutoHideFunctionbar = res.getBoolean(R.bool.pref_auto_hide_functionbar_default);
        mImeShortcutsAction = res.getInteger(R.integer.pref_ime_shortcuts_action_default);
        mImeDefaultInputtype = res.getInteger(R.integer.pref_ime_inputtype_default);
        mForceNormalInputModeToPhysicalKeyboard = res.getBoolean(R.bool.pref_force_normal_input_to_physical_keyboard);
        mViCooperativeMode = res.getInteger(R.integer.pref_vi_cooperative_mode_default);
    }

    public void readPrefs(SharedPreferences prefs) {
        mPrefs = prefs;
        setLocalizedDefault();
        mStatusBar = readIntPref(STATUSBAR_KEY, mStatusBar, 1);
        mFunctionBar = readBooleanPref(FUNCTIONBAR_KEY, mFunctionBar);
        mFunctionBarNavigationButton = readBooleanPref(FUNCTIONBAR_NAVIAGATION_BUTTON_KEY, mFunctionBarNavigationButton);
        mCursorDirectionCtrl = readIntPref(CURSOR_DIRECTION_CTRL, mCursorDirectionCtrl, 3);
        mOnelineTextBox = readBooleanPref(ONELINE_TEXTBOX_KEY, mOnelineTextBox);
        mOnelineTextBoxEsc = readBooleanPref(ONELINE_TEXTBOX_ESC_KEY, mOnelineTextBoxEsc);
        mOnelineTextBoxCr = readBooleanPref(ONELINE_TEXTBOX_CR_KEY, mOnelineTextBoxCr);
        mActionBarMode = readIntPref(ACTIONBAR_KEY, mActionBarMode, ACTION_BAR_MODE_MAX);
        mOrientation = readIntPref(ORIENTATION_KEY, mOrientation, 2);
        mCursorStyle = readIntPref(CURSORSTYLE_KEY, mCursorStyle, 3);
        mCursorBlink = readIntPref(CURSORBLINK_KEY, mCursorBlink, 1);
        mCursorColor = readStringPref(CURSORCOLOR_KEY, mCursorColor);
        mFontSize = readFloatPref(FONTSIZE_KEY, mFontSize, 288.0f);
        mFontLeading = readIntPref(FONTLEADING_KEY, mFontLeading, 288);
        mFontFile = readStringPref(FONTFILE_KEY, mFontFile);
        mAmbiWidth = readIntPref(AMBIWIDTH_KEY, mAmbiWidth, 3);
        mTheme = readIntPref(THEME_KEY, mTheme, 4);
        mKeepScreenTime = readIntPref(KEEP_SCREEN_TIME_KEY, mKeepScreenTime, 120);
        mKeepScreenAtStartup = readBooleanPref(KEEP_SCREEN_AT_STARTUP_KEY, mKeepScreenAtStartup);
        mColorId = readIntPref(COLOR_KEY, mColorId, COLOR_SCHEMES.length - 1);
        mIMEColor = readIntPref(IMECOLOR_KEY, mIMEColor, 100);
        mUTF8ByDefault = readBooleanPref(UTF8_KEY, mUTF8ByDefault);
        mHwAcceleration = readBooleanPref(HWACCELERATION_KEY, mHwAcceleration);
        mForceFlushDrawText = readBooleanPref(FORCE_FLUSH_DRAW_KEY, mForceFlushDrawText);
        mActionBarIconAction = readIntPref(ACTIONBAR_ICON_KEY, mActionBarIconAction, ACTIONBAR_KEY_MAX);
        mActionBarPlusAction = readIntPref(ACTIONBAR_PLUS_KEY, mActionBarPlusAction, ACTIONBAR_KEY_MAX);
        mActionBarMinusAction = readIntPref(ACTIONBAR_MINUS_KEY, mActionBarMinusAction, ACTIONBAR_KEY_MAX);
        mActionBarXAction = readIntPref(ACTIONBAR_X_KEY, mActionBarXAction, ACTIONBAR_KEY_MAX);
        mActionBarUserAction = readIntPref(ACTIONBAR_USER_KEY, mActionBarUserAction, ACTIONBAR_KEY_MAX);
        mActionBarInvertAction = readIntPref(ACTIONBAR_INVERT_KEY, mActionBarInvertAction, ACTIONBAR_KEY_MAX);
        mDoubleTapAction = readIntPref(DOUBLE_TAP_KEY, mDoubleTapAction, ACTIONBAR_KEY_MAX);
        mRightDoubleTapAction = readIntPref(RIGHT_DOUBLE_TAP_KEY, mRightDoubleTapAction, ACTIONBAR_KEY_MAX);
        mLeftDoubleTapAction = readIntPref(LEFT_DOUBLE_TAP_KEY, mLeftDoubleTapAction, ACTIONBAR_KEY_MAX);
        mBottomDoubleTapAction = readIntPref(BOTTOM_DOUBLE_TAP_KEY, mBottomDoubleTapAction, ACTIONBAR_KEY_MAX);
        mTopDoubleTapAction = readIntPref(TOP_DOUBLE_TAP_KEY, mTopDoubleTapAction, ACTIONBAR_KEY_MAX);
        mBackKeyAction = readIntPref(BACKACTION_KEY, mBackKeyAction, BACK_KEY_MAX);
        mControlKeyId = readIntPref(CONTROLKEY_KEY, mControlKeyId, CONTROL_KEY_SCHEMES.length - 1);
        mFnKeyId = readIntPref(FNKEY_KEY, mFnKeyId, FN_KEY_SCHEMES.length - 1);
        mUseCookedIME = readIntPref(IME_KEY, mUseCookedIME, 1);
        mUseDirectCookedIME = readIntPref(IME_DIRECT_KEY, mUseDirectCookedIME, 1);
        mMRUCommand = readStringPref(MRU_COMMAND_KEY, mMRUCommand);
        mShowDotfiles = readBooleanPref(SHOW_DOTFILES_KEY, mShowDotfiles);
        mExternalAppId = readStringPref(EXTERNAL_APP_ID_KEY, mExternalAppId);
        mExternalAppButtonMode = readIntPref(EXTERNAL_APP_BUTTON_MODE_KEY, mExternalAppButtonMode, 2);
        mUseFilesAppButton = readBooleanPref(FILES_BUTTON_KEY, mUseFilesAppButton);
        mFilerAppButtonMode = readIntPref(FILER_APP_BUTTON_MODE_KEY, mFilerAppButtonMode, 2);
        mDropboxFilePicker = readIntPref(DROPBOX_FILE_PICKER_KEY, mDropboxFilePicker, 2);
        mGoogleDriveFilePicker = readIntPref(GOOGLEDRIVE_FILE_PICKER_KEY, mGoogleDriveFilePicker, 2);
        mOneDriveFilePicker = readIntPref(ONEDRIVE_FILE_PICKER_KEY, mOneDriveFilePicker, 2);
        mCloudStorageReadCheck = readBooleanPref(CLOUD_STRAGE_READ_CHECK_KEY, mCloudStorageReadCheck);
        mCloudStorageWriteCheck = readBooleanPref(CLOUD_STRAGE_WRITE_CHECK_KEY, mCloudStorageWriteCheck);
        mHtmlViewerMode = readIntPref(HTML_VIEWER_MODE_KEY, mHtmlViewerMode, 2);
        mShell = readStringPref(SHELL_KEY, mShell);
        mInitialCommand = readStringPref(INITIALCOMMAND_KEY, mInitialCommand);
        if (Term.mTerminalMode != 0) {
            mInitialCommand = "cd %STARTUP_DIR%";
            if ((Term.mTerminalMode & Term.TERMINAL_MODE_BASH) != 0) {
                mInitialCommand += "\nbash.app";
            } else {
                mInitialCommand += "\nsh.app";
            }
        }
        mIntentCommand = readStringPref(INTENTCOMMAND_KEY, mIntentCommand);
        mTermType = readStringPref(TERMTYPE_KEY, mTermType);
        mCloseOnExit = readBooleanPref(CLOSEONEXIT_KEY, mCloseOnExit);
        mProot = readBooleanPref(PROOT_KEY, mProot);
        mHomePath = readStringPref(HOMEPATH_KEY, mHomePath);
        mAltUses8bitMSB = readBooleanPref(ALT_USES_8BIT_META, mAltUses8bitMSB);
        mAltSendsEsc = readBooleanPref(ALT_SENDS_ESC, mAltSendsEsc);
        mIgnoreXoff = readBooleanPref(IGNORE_XON, mIgnoreXoff);
        mBackAsEsc = readBooleanPref(BACK_AS_ESC, mBackAsEsc);
        mMouseTracking = readBooleanPref(MOUSE_TRACKING, mMouseTracking);
        mVolumeAsCursor = readBooleanPref(VOLUME_AS_CURSOR, mVolumeAsCursor);
        mPinchInOut = readBooleanPref(PINCH_IN_OUT, mPinchInOut);
        mUseKeyboardShortcuts = readBooleanPref(USE_KEYBOARD_SHORTCUTS, mUseKeyboardShortcuts);
        mAutoHideFunctionbar = readBooleanPref(AUTO_HIDE_FUNCTIONBAR, mAutoHideFunctionbar);
        mImeShortcutsAction = readIntPref(IME_SHORTCUTS_ACTION, mImeShortcutsAction, IME_SHORTCUTS_ACTION_MAX);
        mImeDefaultInputtype = readIntPref(IME_DEFAULT_INPUTTYPE, mImeDefaultInputtype, IME_SHORTCUTS_ACTION_MAX);
        mForceNormalInputModeToPhysicalKeyboard = readBooleanPref(FORCE_NOMAL_INPUT_TO_PHYSICAL_KEYBOARD, mForceNormalInputModeToPhysicalKeyboard);
        mViCooperativeMode = readIntPref(IME_VI_COOPERATIVE_MODE, mViCooperativeMode, VI_COOPRATIVE_MODE_MAX);
        mPrefs = null;  // we leak a Context if we hold on to this
    }

    private static final String FIRST_KEY = "pref_first";

    private void setLocalizedDefault() {
        boolean first = mPrefs.getBoolean(FIRST_KEY, true);
        if (!first) return;
        SharedPreferences.Editor editor = mPrefs.edit();
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        if (language.equals("ja")) {
            editor.putString(AMBIWIDTH_KEY, "2");
        }
        editor.putBoolean(FIRST_KEY, false);
        editor.apply();
    }

    private int readIntPref(String key, int defaultValue, int maxValue) {
        int val;
        try {
            val = Integer.parseInt(
                    mPrefs.getString(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException e) {
            val = defaultValue;
        }
        val = Math.max(0, Math.min(val, maxValue));
        return val;
    }

    private float readFloatPref(String key, float defaultValue, float maxValue) {
        float val;
        try {
            val = Float.parseFloat(mPrefs.getString(key, Float.toString(defaultValue)));
        } catch (NumberFormatException e) {
            val = defaultValue;
        }
        val = Math.max(0, Math.min(val, maxValue));
        return val;
    }

    private String readStringPref(String key, String defaultValue) {
        return mPrefs.getString(key, defaultValue);
    }

    private boolean readBooleanPref(String key, boolean defaultValue) {
        return mPrefs.getBoolean(key, defaultValue);
    }

    public boolean showStatusBar() {
        return true;
    }

    public boolean showFunctionBar() {
        return mFunctionBar;
    }

    public boolean showFunctionBarNavigationButton() {
        return mFunctionBarNavigationButton;
    }

    public int getCursorDirectionControlMode() {
        return mCursorDirectionCtrl;
    }

    public boolean showOnelineTextBox() {
        return mOnelineTextBox;
    }

    public boolean getOneLineTextBoxEsc() {
        return mOnelineTextBoxEsc;
    }

    public boolean getOneLineTextBoxCr() {
        return mOnelineTextBoxCr;
    }

    public int actionBarMode() {
        return mActionBarMode;
    }

    public int getScreenOrientation() {
        return mOrientation;
    }

    public int getCursorStyle() {
        return mCursorStyle % 2;
    }

    public int getCursorBlink() {
        return mCursorStyle >= 2 ? 1 : 0;
        // return mCursorBlink;
    }

    public int getCursorColor() {
        if (mCursorColor.equals("")) return 0;
        int color;
        try {
            // #ARGB
            color = Integer.parseInt(mCursorColor, 16);
            color = 0xff000000 + (color & 0x00ffffff);
        } catch (Exception e) {
            color = 0xff808080;
        }
        return color;
    }

    public float getFontSize() {
        return mFontSize;
    }

    public int getFontLeading() {
        return mFontLeading;
    }

    public String getFontFile() {
        return mFontFile;
    }

    public int getAmbiWidth() {
        return mAmbiWidth;
    }

    public boolean getForceFlushDrawText() {
        return mForceFlushDrawText;
    }

    public boolean getHwAcceleration() {
        return mHwAcceleration;
    }

    public int[] getColorScheme() {
        return COLOR_SCHEMES[mColorId];
    }

    public int getKeepScreenTime() {
        return mKeepScreenTime;
    }

    public boolean getKeepScreenAtStartup() {
        return mKeepScreenAtStartup;
    }

    public int getColorTheme() {
        int theme = mTheme;
        if (mTheme == 4) {
            // FIXME: Android changes background color.
            if (AndroidCompat.SDK >= Build.VERSION_CODES.O) {
                theme = getCOLORFGBG().equals("'0;15'") ? 3 : 2;
            } else {
                theme = getCOLORFGBG().equals("'0;15'") ? 1 : 0;
            }
        }
        return theme;
    }

    public String getCOLORFGBG() {
        int bg = COLOR_SCHEMES[mColorId][1];
        return ((bg == TermSettings.WHITE) || (bg == TermSettings.SOLARIZED_BG)) ? "'0;15'" : "'15;0'";
    }

    public int getIMEColor() {
        return mIMEColor;
    }

    public boolean defaultToUTF8Mode() {
        return mUTF8ByDefault;
    }

    public int getActionBarIconKeyAction() {
        return mActionBarIconAction;
    }

    public int getActionBarPlusKeyAction() {
        mActionBarPlusAction = 1250;
        return mActionBarPlusAction;
    }

    public int getActionBarMinusKeyAction() {
        mActionBarMinusAction = 999;
        return mActionBarMinusAction;
    }

    public int getActionBarXKeyAction() {
        return 1251;
//        return mActionBarXAction;
    }

    public int getActionBarUserKeyAction() {
        return mActionBarUserAction;
    }

    public int getActionBarInvertKeyAction() {
        return mActionBarInvertAction;
    }

    public int getDoubleTapAction() {
        return mDoubleTapAction;
    }

    public int getRightDoubleTapAction() {
        return mRightDoubleTapAction;
    }

    public int getLeftDoubleTapAction() {
        return mLeftDoubleTapAction;
    }

    public int getBottomDoubleTapAction() {
        return mBottomDoubleTapAction;
    }

    public int getTopDoubleTapAction() {
        return mTopDoubleTapAction;
    }

    public int getActionBarQuitKeyAction() {
        return mActionBarXAction;
    }

    public int getBackKeyAction() {
        return mBackKeyAction;
    }

    public boolean backKeySendsCharacter() {
        if (mBackKeyAction == BACK_KEY_TOGGLE_IME) return false;
        return mBackKeyAction >= BACK_KEY_SENDS_ESC;
    }

    public boolean getAltSendsEscFlag() {
        return mAltSendsEsc;
    }

    public boolean getAltUses8bitMSB() {
        return mAltUses8bitMSB;
    }

    public boolean getIgnoreXoff() {
        return mIgnoreXoff;
    }

    public boolean getBackAsEscFlag() {
        return mBackAsEsc;
    }

    public boolean getMouseTrackingFlag() {
        return mMouseTracking;
    }

    public boolean getVolumeAsCursor() {
        return mVolumeAsCursor;
    }

    public boolean getPinchInOut() {
        return mPinchInOut;
    }

    public boolean getUseKeyboardShortcutsFlag() {
        return mUseKeyboardShortcuts;
    }

    public boolean getAutoHideFunctionbar() {
        return mAutoHideFunctionbar;
    }

    public int getImeShortcutsAction() {
        return mImeShortcutsAction;
    }

    public int getImeDefaultInputtype() {
        return mImeDefaultInputtype;
    }

    public boolean getForceNormalInputModeToPhysicalKeyboard() {
        return mForceNormalInputModeToPhysicalKeyboard;
    }

    public int getViCooperativeMode() {
        return mViCooperativeMode;
    }

    public int getBackKeyCharacter() {
        switch (mBackKeyAction) {
            case BACK_KEY_SENDS_ESC:
                return 27;
            case BACK_KEY_SENDS_TAB:
                return 9;
            default:
                return 0;
        }
    }

    public int getControlKeyId() {
        return mControlKeyId;
    }

    public int getFnKeyId() {
        return mFnKeyId;
    }

    public int getControlKeyCode() {
        return CONTROL_KEY_SCHEMES[mControlKeyId];
    }

    public int getFnKeyCode() {
        return FN_KEY_SCHEMES[mFnKeyId];
    }

    public String getMRUCommand() {
        return mMRUCommand;
    }

    public boolean getShowDotfiles() {
        return mShowDotfiles;
    }

    public String getExternalAppId() {
        return mExternalAppId;
    }

    public int getExternalAppButtonMode() {
        return mExternalAppButtonMode;
    }

    public String getFilerAppId() {
        return mFilerAppId;
    }

    public int getFilerAppButtonMode() {
        return mUseFilesAppButton ? 2 : 0;
        // return mFilerAppButtonMode;
    }

    public int getDropboxFilePicker() {
        return mDropboxFilePicker;
    }

    public void setDropboxFilePicker(int value) {
        mDropboxFilePicker = value;
    }

    public int getGoogleDriveFilePicker() {
        return mGoogleDriveFilePicker;
    }

    public void setGoogleDriveFilePicker(int value) {
        mGoogleDriveFilePicker = value;
    }

    public int getOneDriveFilePicker() {
        return mOneDriveFilePicker;
    }

    public void setOneDriveFilePicker(int value) {
        mOneDriveFilePicker = value;
    }

    public int getCloudStorageCheck() {
        int mode = SyncFileObserver.HASH_CHECK_MODE_NONE;
        if (mCloudStorageReadCheck) mode += SyncFileObserver.HASH_CHECK_MODE_READ;
        if (mCloudStorageWriteCheck) mode += SyncFileObserver.HASH_CHECK_MODE_WRITE;
        return mode;
    }

    public int getHtmlViewerMode() {
        return mHtmlViewerMode;
    }

    public boolean useCookedIME() {
        return (mUseCookedIME != 0);
    }

    public boolean useDirectCookedIME() {
        return (mUseDirectCookedIME != 0);
    }

    public String getShell() {
        return mShell;
    }

    public String getFailsafeShell() {
        return mFailsafeShell;
    }

    public String getInitialCommand() {
        return mInitialCommand;
    }

    public String getIntentCommand() {
        return mIntentCommand;
    }

    public String getTermType() {
        return mTermType;
    }

    public boolean closeWindowOnProcessExit() {
        return mCloseOnExit;
    }

    public boolean isProot() {
        return mProot;
    }

    public void setHomePath(String homePath) {
        mHomePath = homePath;
    }

    public String getHomePath() {
        if (mHomePath == null) return TermService.getHOME();
        return mHomePath;
    }

}
