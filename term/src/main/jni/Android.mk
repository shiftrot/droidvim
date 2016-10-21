LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libjackpal-androidterm5

SRC_PATH = $(LOCAL_PATH)/termExec
LOCAL_C_INCLUDES += $(SRC_PATH)

LOCAL_SRC_FILES := \
    $(SRC_PATH)/common.cpp \
    $(SRC_PATH)/fileCompat.cpp \
    $(SRC_PATH)/termExec.cpp \

LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)

