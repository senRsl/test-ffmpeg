package dc.test.ffmpeg

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import dc.android.base.activity.BridgeActivity
import dc.android.libs.PermissionUtils
import dc.android.libs.permission.AbsPermissionCallback
import dc.common.Logger
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream


/**
 *
 *
 * @ClassName: Test05AudioRecordActivity
 * @author senrsl
 *
 * @Package: dc.test.ffmpeg
 * @CreateTime: 2020/12/15 5:13 下午
 */
class Test05AudioRecordActivity : BridgeActivity() {

    companion object {
        @JvmStatic
        fun start(context: Context) {
            val starter = Intent(context, Test05AudioRecordActivity::class.java)
            context.startActivity(starter)
        }
    }

    override fun initData() {
        super.initData()

        PermissionUtils.with(this)
            .permisson(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
            .callback(object : AbsPermissionCallback() {
                override fun onResult(isAllGrant: Boolean, hasDenied: Boolean, hasRationale: Boolean) {
                    Logger.w(this@Test05AudioRecordActivity, "授权$isAllGrant")

                    if (isAllGrant) startAudioRecord()
                }

            }).request()
    }


    var isRecording = false;    //停止录制pcm
    var isAudioRecording = false; //停止编码audio
    var isMuxerStatus = false     //混合器 启动状态
    var lastPTSUs = 0L

    fun startAudioRecord() {
        Logger.w(this, "startAudioRecord")

        if (isRecording) return   //已经开始录制了，就不再重复start

        //基础参数
        val audioSource = MediaRecorder.AudioSource.MIC;
        val sampleRate = 44100;
        val channelConfigs = AudioFormat.CHANNEL_IN_STEREO;
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigs, audioFormat);

        if (AudioRecord.ERROR_BAD_VALUE == bufferSize) {
            Logger.w(this, "参数无效")
            return
        }

        //开始录制
        isRecording = true;
        val audioRecord = AudioRecord(audioSource, sampleRate, channelConfigs, audioFormat, bufferSize)
        audioRecord.startRecording()

        //不断读取pcm
        Thread(Runnable {

//            val dir = "/sdcard/SENRSL/"
            val dir = getExternalFilesDir(null)
            file = File(dir?.canonicalPath + "/audio04_" + System.currentTimeMillis() + ".pcm")
            fos = FileOutputStream(file)
            bos = BufferedOutputStream(fos)

            Logger.w(dir?.absoluteFile, dir?.freeSpace, file?.exists(), bos)

            //音频编码，视频编码，音视频合成muxer
            initAudioCodec()
            initVideoCodec()
            initMediaMuxer()

            isAudioRecording = true
            AudioCodecThread().start()


            //var presentationTimeUs = 0L

            while (isRecording) {
                val buffer = ByteArray(bufferSize)
                val readSize = audioRecord.read(buffer, 0, bufferSize);
                if (readSize > 0) {
                    Logger.w(javaClass.canonicalName, readSize, dir?.canonicalPath, "audio04.pcm")
                    //成功读取pcm音频
                    // FileUtils.byte2File(buffer, 0, readSize, dir?.canonicalPath, "audio04.pcm")  //非要搞那么复杂，手动创建一个

                    //终于保存成功了。。。。
                    bos!!.write(buffer, 0, readSize)

                    //二期，保存pcm到mp4

                    var bufferIndex = audioCodec.dequeueInputBuffer(0)
                    if (bufferIndex < 0) {
                        SystemClock.sleep(10)
                        continue
                    }
                    var byteBuffer =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            audioCodec.getInputBuffer(bufferIndex)
                        } else {
                            audioCodec.inputBuffers[bufferIndex]
                        }
                    if (null == byteBuffer) {
                        SystemClock.sleep(10)
                        continue
                    }
                    byteBuffer.clear()
                    byteBuffer.put(buffer)  //填充pcm

                    //presentationTimeUs += (1.0 * bufferSize / (sampleRate * 2 * (audioFormat / 8)) * 1000000).toLong()
                    //Logger.w("pcm一帧 时间戳 " + presentationTimeUs / 1000000)
                    var ptsus = getPTSUs()
                    Logger.w("pcm一帧 时间戳 $ptsus")
                    audioCodec.queueInputBuffer(bufferIndex, 0, readSize, ptsus, 0)

                } else {
                    Logger.w("pcm采集异常", readSize)
                }

                SystemClock.sleep(10)
            }


        }).start()


    }

    fun getPTSUs(): Long {
        var result = System.nanoTime() / 1000L
        if (result < lastPTSUs) result = lastPTSUs
        return result
    }

    var bos: BufferedOutputStream? = null
    var fos: FileOutputStream? = null
    var file: File? = null


    override fun onDestroy() {
        super.onDestroy()
        isAudioRecording = false
        isRecording = false

        bos?.close()
        fos?.close()

        mediaMuxer.stop()
        mediaMuxer.release()
        isMuxerStatus = false
    }


    //音频 编码
    lateinit var audioCodec: MediaCodec

    fun initAudioCodec() {
        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC) //AAC 音频

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2) //单声道1，双声道2
        format.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)

        audioCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    //视频 编码
    lateinit var videoCodec: MediaCodec

    fun initVideoCodec() {
        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)   //可通过Camera#Parameters#getSupportedPreviewFpsRange获取
        format.setInteger(MediaFormat.KEY_BIT_RATE, 1920 * 1080 * 3)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)  //每秒关键帧数

        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);

        videoCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        surface = videoCodec.createInputSurface()
    }


    lateinit var surface: Surface

    //音视频混合器，把编码后的音视频数据合在一起，生成mp4等格式
    lateinit var mediaMuxer: MediaMuxer

    fun initMediaMuxer() {
        val dir = getExternalFilesDir(null)
        var fileMuxer = File(dir?.canonicalPath + "/test05_" + System.currentTimeMillis() + ".mp4")

        mediaMuxer = MediaMuxer(fileMuxer.canonicalPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }


    inner class AudioCodecThread : Thread() {

        var bufferInfo = MediaCodec.BufferInfo()    //缓冲区
        var audioTrackIndex = -1    //track 下标
        //var presentationTimeUs = 0L

        override fun run() {
            super.run()
            audioCodec.start()

            while (isAudioRecording) {

                //获取一帧解码完成的数据到bufferInfo，没有数据就阻塞
                var outBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0)
                Logger.w(javaClass.canonicalName, outBufferIndex)

                //第一次返回-2，此时添加音轨
                if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    bufferInfo.presentationTimeUs = getPTSUs()
                    audioTrackIndex = mediaMuxer.addTrack(audioCodec.outputFormat)
                    lastPTSUs = bufferInfo.presentationTimeUs

                    Logger.w("音频轨道 已添加", bufferInfo.size, bufferInfo.presentationTimeUs)
                    //音频音轨 和视频音轨?添加完成后，启动混合器
                    //TODO 音频及视频都准备好后再启动混合器
                    mediaMuxer.start()
                    isMuxerStatus = true
                } else {

                    while (isAudioRecording && outBufferIndex >= 0) {
                        //判断是否启动混合器
                        if (!isMuxerStatus) {
                            Logger.w("混合器尚未启动")
                            SystemClock.sleep(10)
                            continue
                        }

                        var outputBuffer = audioCodec.getOutputBuffers()[outBufferIndex]
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        //看起来是在校正时间
                        //如果不修正时间，那打开音频就不是从 0:00 开始
                        //if (presentationTimeUs == 0L) presentationTimeUs = bufferInfo.presentationTimeUs
                        bufferInfo.presentationTimeUs = getPTSUs()

                        Logger.w(
                            "writeSampleData",
                            javaClass.canonicalName,
                            audioTrackIndex,
                            outputBuffer,
                            bufferInfo.size,
                            bufferInfo.offset,
                            bufferInfo.flags,
                            bufferInfo.presentationTimeUs
                        )
                        //编码后的音频数据写入混合器
                        mediaMuxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)

                        var data = ByteArray(outputBuffer.remaining())
                        outputBuffer.get(data, 0, data.size)

                        audioCodec.releaseOutputBuffer(outBufferIndex, false)
                        outBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0)

                    }

                }

            }
        }

    }

}