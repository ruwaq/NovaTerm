LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE:= libnovaterm_pty
LOCAL_SRC_FILES:= novaterm_pty.c
include $(BUILD_SHARED_LIBRARY)
