#include <jni.h>
#include <string>
#include <android/log.h>

//ffmpeg 是c写的，要用c的include
extern "C"{
#include "libavformat/avformat.h"
#include "libswresample/swresample.h"
};

//using namespace std;
#define TAG "TEST_JNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG,__VA_ARGS__)
#define AUDIO_SAMPLE_RATE 44100

//暂时用全局变量，后面再抽取优化
jmethodID jAudioTrackWriteMid;
jobject audioTrack;

/**
 * 创建 java 的 AudioTrack
 * @param env
 * @return
 */
jobject initAudioTrack(JNIEnv *env){
    jclass jAudioTrackClass = env->FindClass("android/media/AudioTrack");
    jmethodID jAudioTrackCMid = env->GetMethodID(jAudioTrackClass,"<init>","(IIIIII)V"); //构造

    //  public static final int STREAM_MUSIC = 3;
    int streamType = 3;
    int sampleRateInHz = 44100;
    // public static final int CHANNEL_OUT_STEREO = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT);
    int channelConfig = (0x4 | 0x8);
    // public static final int ENCODING_PCM_16BIT = 2;
    int audioFormat = 2;
    // getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat)
    jmethodID jGetMinBufferSizeMid = env->GetStaticMethodID(jAudioTrackClass, "getMinBufferSize", "(III)I");
    int bufferSizeInBytes = env->CallStaticIntMethod(jAudioTrackClass, jGetMinBufferSizeMid, sampleRateInHz, channelConfig, audioFormat);
    // public static final int MODE_STREAM = 1;
    int mode = 1;

    //创建了AudioTrack
    jobject jAudioTrack = env->NewObject(jAudioTrackClass,jAudioTrackCMid, streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode);

    //play方法
    jmethodID jPlayMid = env->GetMethodID(jAudioTrackClass,"play","()V");
    env->CallVoidMethod(jAudioTrack,jPlayMid);

    // write method
    jAudioTrackWriteMid = env->GetMethodID(jAudioTrackClass, "write", "([BII)I");

    return jAudioTrack;

}


extern "C"
JNIEXPORT void JNICALL
Java_dc_test_ffmpeg_Test04PlayAudioActivity_printAudioInfo2(JNIEnv *env, jobject instance,jstring url_) {

    const char *url = env->GetStringUTFChars(url_, 0);

    LOGE("待播放地址%s",url);

}

extern "C"
JNIEXPORT void JNICALL
Java_dc_test_ffmpeg_Test04PlayAudioActivity_nativePlay(JNIEnv *env, jobject instance,jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);

    LOGE("待播放地址%s",url_);

    AVFormatContext *pFormatContext = NULL;
    AVCodecParameters *pCodecParameters = NULL;
    AVCodec *pCodec = NULL;
    int formatFindStreamInfoRes = 0;
    int audioStramIndex = 0;
    AVCodecContext *pCodecContext = NULL;
    int codecParametersToContextRes = -1;
    int codecOpenRes = -1;
    AVPacket *pPacket = NULL;
    AVFrame *pFrame = NULL;
    int index = 0;

    int outChannels;
    int dataSize;

    uint8_t *resampleOutBuffer;
    jbyte *jPcmData;
    SwrContext *swrContext = NULL;

    int64_t out_ch_layout;
    int out_sample_rate;
    enum AVSampleFormat out_sample_fmt;
    int64_t in_ch_layout;
    enum AVSampleFormat in_sample_fmt;
    int in_sample_rate;
    int swrInitRes;


    ///1、初始化所有组件，只有调用了该函数，才能使用复用器和编解码器（源码）
    av_register_all();
    ///2、打开文件
    int open_input_result = avformat_open_input(&pFormatContext,url,NULL,NULL);
    if (open_input_result != 0){
        LOGE("format open input error: %s", av_err2str(open_input_result));
        goto _av_resource_destroy;
    }

    ///3.填充流信息到 pFormatContext
    formatFindStreamInfoRes = avformat_find_stream_info(pFormatContext, NULL);
    if (formatFindStreamInfoRes < 0) {
        LOGE("format find stream info error: %s", av_err2str(formatFindStreamInfoRes));
        goto _av_resource_destroy;
    }

    ///4.、查找音频流的 index，后面根据这个index处理音频
    audioStramIndex = av_find_best_stream(pFormatContext, AVMediaType::AVMEDIA_TYPE_AUDIO, -1, -1,NULL, 0);
    if (audioStramIndex < 0) {
        LOGE("format audio stream error:");
        goto _av_resource_destroy;
    }


    ///4、查找解码器
    //audioStramIndex 上一步已经获取了，通过音频流的index，可以从pFormatContext中拿到音频解码器的一些参数
    pCodecParameters = pFormatContext->streams[audioStramIndex]->codecpar;
    pCodec = avcodec_find_decoder(pCodecParameters->codec_id);

    LOGE("采样率：%d", pCodecParameters->sample_rate);
    LOGE("通道数: %d", pCodecParameters->channels);
    LOGE("format: %d", pCodecParameters->format);

    if (pCodec == NULL) {
        LOGE("codec find audio decoder error");
        goto _av_resource_destroy;
    }

    LOGE("之前的跑完了，下面开始解码");

    ///5、打开解码器
    //分配AVCodecContext，默认值
    pCodecContext = avcodec_alloc_context3(pCodec);
    if (pCodecContext == NULL){
        LOGE("avcodec_alloc_context3 error");
        goto _av_resource_destroy;
    }
    //pCodecParameters 转 context
    codecParametersToContextRes = avcodec_parameters_to_context(pCodecContext,pCodecParameters);
    if(codecParametersToContextRes <0){
        LOGE("avcodec_parameters_to_context error");
        goto _av_resource_destroy;
    }
    //
    codecOpenRes = avcodec_open2(pCodecContext,pCodec,NULL);
    if (codecOpenRes != 0) {
        LOGE("codec audio open error: %s", av_err2str(codecOpenRes));
        goto _av_resource_destroy;
    }

    LOGE("解码器初始化完成");

    //到此，pCodecContext 已经初始化完毕，下面可以用来获取每一帧数据

    pPacket = av_packet_alloc();
    pFrame = av_frame_alloc();

    ///创建java 的 AudioTrack
    audioTrack = initAudioTrack(env);

    LOGE("初始化 AudioTrack完成");

    // ---------- 重采样 构造 swrContext 参数 start----------
    out_ch_layout = AV_CH_LAYOUT_STEREO;
    out_sample_fmt = AVSampleFormat::AV_SAMPLE_FMT_S16;
    out_sample_rate = AUDIO_SAMPLE_RATE;
    in_ch_layout = pCodecContext->channel_layout;
    in_sample_fmt = pCodecContext->sample_fmt;
    in_sample_rate = pCodecContext->sample_rate;
    swrContext = swr_alloc_set_opts(NULL, out_ch_layout, out_sample_fmt,
                                    out_sample_rate, in_ch_layout, in_sample_fmt, in_sample_rate, 0, NULL);
    if (swrContext == NULL) {
        // 提示错误
        LOGE("swr_alloc_set_opts error");
        goto _av_resource_destroy;
    }
    swrInitRes = swr_init(swrContext);
    if (swrInitRes < 0) {
        LOGE("swr_init error");
        goto _av_resource_destroy;
    }
    LOGE("重采样 完成");
    // ---------- 重采样 构造 swrContext 参数 end----------


    // size 是播放指定的大小，是最终输出的大小
    outChannels = av_get_channel_layout_nb_channels(out_ch_layout); //通道数
    dataSize = av_samples_get_buffer_size(NULL, outChannels, pCodecParameters->frame_size,out_sample_fmt, 0);
    resampleOutBuffer = (uint8_t *) malloc(dataSize);

    //一帧一帧播放，while循环
    while (av_read_frame(pFormatContext,pPacket) >=0){
        LOGE("单帧 01");
        // Packet 包，压缩的数据，解码成 pcm 数据
        //判断是音频帧
        if (pPacket->stream_index != audioStramIndex) {
            continue;
        }
        LOGE("单帧 02");

        //输入原数据到解码器
        int codecSendPacketRes = avcodec_send_packet(pCodecContext,pPacket);
        if (codecSendPacketRes == 0){
            LOGE("单帧 05");
            //解码器输出解码后的数据 pFrame  AVCodecContext *avctx, AVFrame *frame
            int codecReceiveFrameRes = avcodec_receive_frame(pCodecContext,pFrame);
            if(codecReceiveFrameRes == 0){
                index++;
                LOGE("单帧 06");

                //数据转换成Buffer,需要导入 libswresample/swresample.h
                swr_convert(swrContext, &resampleOutBuffer, pFrame->nb_samples,
                            (const uint8_t **) pFrame->data, pFrame->nb_samples);
                LOGE("单帧 06.1");

                jbyteArray jPcmDataArray = env->NewByteArray(dataSize);
                // native 创建 c 数组
                jPcmData = env->GetByteArrayElements(jPcmDataArray, NULL);

                //内存拷贝
                memcpy(jPcmData, resampleOutBuffer, dataSize);

                // 同步刷新到 jbyteArray ，并释放 C/C++ 数组
                env->ReleaseByteArrayElements(jPcmDataArray, jPcmData, 0);

                ///public int write(@NonNull byte[] audioData, int offsetInBytes, int sizeInBytes) {}
                env->CallIntMethod(audioTrack, jAudioTrackWriteMid, jPcmDataArray, 0, dataSize);

                LOGE("解码第 %d 帧dataSize =%d ", index , dataSize);

                // 解除 jPcmDataArray 的持有，让 javaGC 回收
                env->DeleteLocalRef(jPcmDataArray);

            }

            LOGE("单帧 07");

        }
        LOGE("单帧 03");

        //解引用
        av_packet_unref(pPacket);
        av_frame_unref(pFrame);
        LOGE("单帧 04");
    }
    LOGE("逐帧播放 完成");

    /// 解引用数据 data ， 2. 销毁 pPacket 结构体内存  3. pPacket = NULL
    av_frame_free(&pFrame);
    av_packet_free(&pPacket);

    _av_resource_destroy:
    if (pFormatContext != NULL){
        avformat_close_input(&pFormatContext);
        avformat_free_context(pFormatContext);
        pFormatContext = NULL;
    }

    env->ReleaseStringUTFChars(url_, url);
}
