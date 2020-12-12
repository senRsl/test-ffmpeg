#include <stdio.h>

JNIEXPORT void JNICALL Java_dc_test_ffmpeg_sayHello(JNIEnv *env, jobject obj) {
  printf("Hello World!\n");
  return;
}