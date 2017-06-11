APP_PLATFORM := android-16
APP_ABI := armeabi x86 arm64-v8a x86_64

APP_CFLAGS := -Werror=return-type -Werror=implicit-function-declaration -Wno-multichar
APP_CFLAGS += -DHAVE_PTHREADS
APP_CFLAGS += -Wno-format
#APP_CPPFLAGS := -std=c++11
#APP_CPPFLAGS += -D__STDC_LIMIT_MACROS -D__STDC_CONSTANT_MACROS

APP_THIN_ARCHIVE := true

ifneq (,$(findstring clang,$(NDK_TOOLCHAIN_VERSION)))
APP_CFLAGS += -Wno-return-type-c-linkage
endif

# [ShrinkFileSize] It's safe to say this because JNI_OnLoad and SWIG generated functions are marked as exported.
APP_CFLAGS += -fvisibility=hidden

# [ShrinkFileSize] Tell the linker to garbage collect unused sections.
APP_LDFLAGS += -Wl,--gc-sections
