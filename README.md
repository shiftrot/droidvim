# DroidVim

DroidVim is a Vim clone for Android.  

Features:

* External storage support - External SD card, USB memory, GoogleDrive, Dropbox, etc. (Requires Android 4.4 and up)
* Special Keys - Esc, Ctrl, Tab, Arrow keys and more.
* Direct input - Disabling predictive text and/or auto correction for normal mode.
* Clipboard - Clipboard commands ("*p "*y) are supported.
* Custom Font - Use your favorite monospaced font.
* Touch to move - Touch, swipe, flick to move cursor.
* Multi language - Vim with multi byte option, iconv and multi language messages.

Extra features (in-app purchase):

* Git - Version Control System.
* Python - Programming language.

## Download

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.droidvim)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
     alt="Get it on F-Droid"
     height="80">](https://apt.izzysoft.de/fdroid/index/apk/com.droidvim)

Or get the latest APK from the [Releases Section](https://github.com/shiftrot/droidvim/releases/latest).

## Build

To build the "DroidVim" application, choose the `droidvim` flavor and build.  
To avoid repository bloat, the latest Vim binaries are included in the `build` branch. If you need the latest Vim binaries, please checkout to the `build` branch before building.  
The `build` branch will be rebased to the latest `master` branch.  

This code is based on the "[Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator)".  
By choosing the `terminal` flavor, you can build the "Terminal Emulator for Android" compatible application with various functions of the DroidVim.  

# Licensing

- DroidVim is licensed under the Apache License, Version 2.0. See [LICENSE](https://github.com/shiftrot/droidvim/blob/master/LICENSE) for the full license text.  
- [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator) code is used which is released under Apache 2.0 license.  
