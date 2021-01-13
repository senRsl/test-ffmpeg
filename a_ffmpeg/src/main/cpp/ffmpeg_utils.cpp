#include <stdio.h>
#include <jni.h>

extern "C" {

#include <libavutil/avutil.h>

JNIEXPORT jstring JNICALL
Java_dc_test_ffmpeg_Test01VersionActivity_getVersion(JNIEnv *env, jclass clazz) {
    const char *version = av_version_info();
    printf("getVersion is %s", version);
    return env->NewStringUTF(version);
}

JNIEXPORT jstring JNICALL
Java_dc_test_ffmpeg_Test01VersionActivity_getVersion431(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF("4.3.1");
}

}