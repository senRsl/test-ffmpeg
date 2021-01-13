#include <jni.h>
#include <string>
#include <android/log.h>

//include不要放在这儿，要放到 extern C 里面去，不然各种找不到

#define LOG_TAG "TEST_JNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

extern "C" {

#include <libavutil/avutil.h>
#include <libavformat/avformat.h>

JNIEXPORT jstring JNICALL
Java_dc_test_ffmpeg_Test03PlayAudioActivity_printAudioInfo(JNIEnv *env, jobject instance,
                                                           jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "hello native log");

    LOGE("待处理地址：%s %s", "aaaaa  :", url);

    //1，av_register_all()：的作用是初始化所有组件，只有调用了该函数，才能使用复用器和编解码器
    av_register_all();
    AVFormatContext *avFormatContext = NULL;
    int audio_stream_idx;
    AVStream *audio_stream;

    //2,avformat_open_input()/avformat_close_input()函数会读文件头，对 mp4 文件而言，它会解析所有的 box。但它只把读到的结果保存在对应的数据结构下。这个时候，AVStream 中的很多字段都是空白的。
    int open_res = avformat_open_input(&avFormatContext, url, NULL, NULL);
    if (open_res != 0) {
        LOGE("Can't open file: %s", av_err2str(open_res));
        return env->NewStringUTF("1111111111");
    }

    //3，获取文件信息
    //读取一部分视音频数据并且获得一些相关的信息，会检测一些重要字段，如果是空白的，就设法填充它们。
    // 因为我们解析文件头的时候，已经掌握了大量的信息，avformat_find_stream_info 就是通过这些信息来填充自己的成员，
    // 当重要的成员都填充完毕后，该函数就返回了。这中情况下，该函数效率很高。但对于某些文件，单纯的从文件头中获取信息是不够的，
    // 比如 video 的 pix_fmt 是需要调用 h264_decode_frame 才可以获取其pix_fmt的。
    int find_stream_info_res = avformat_find_stream_info(avFormatContext, NULL);
    if (find_stream_info_res < 0) {
        LOGE("Find stream info error: %s", av_err2str(find_stream_info_res));
        goto __avformat_close;
    }

    //4，获取采样率和通道
    //av_find_best_stream:获取音视频及字幕的 stream_index , 以前没有这个函数时，我们一般都是写的 for 循环。
    audio_stream_idx = av_find_best_stream(avFormatContext, AVMediaType::AVMEDIA_TYPE_AUDIO, -1, -1,
                                           NULL, 0);
    if (audio_stream_idx < 0) {
        LOGE("Find audio stream info error: %s", av_err2str(find_stream_info_res));
        goto __avformat_close;
    }
    audio_stream = avFormatContext->streams[audio_stream_idx];

    LOGE("采样率：%d", audio_stream->codecpar->sample_rate);
    LOGE("通道数: %d", audio_stream->codecpar->channels);
    LOGE("format: %d", audio_stream->codecpar->format);
    LOGE("extradata_size: %d", audio_stream->codecpar->extradata_size);

    __avformat_close:
    //5,关闭
    avformat_close_input(&avFormatContext);

    return env->NewStringUTF("22222222Intellij竟然不支持C++插件也没有。。。。");
}

}