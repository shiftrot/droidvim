LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libjackpal-termexec2

SRC_PATH = $(LOCAL_PATH)/process
LOCAL_C_INCLUDES += $(SRC_PATH)
LOCAL_SRC_FILES := \
    $(SRC_PATH)/process.cpp \

LOCAL_LDLIBS := -llog -lc

include $(BUILD_SHARED_LIBRARY)

