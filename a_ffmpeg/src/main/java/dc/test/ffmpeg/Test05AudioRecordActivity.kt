package dc.test.ffmpeg

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.*
import android.os.Build
import android.os.SystemClock
import android.view.TextureView
import android.widget.LinearLayout
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

    lateinit var layoutContent: LinearLayout

    override fun initLayout() {
        super.initLayout()
        layoutContent = LinearLayout(this)
        layoutContent.orientation = LinearLayout.VERTICAL
        setLayout(false, layoutContent, false, Color.WHITE)
    }

    override fun initData() {
        super.initData()

        Logger.w(javaClass.name, Thread.currentThread())

        PermissionUtils.with(this)
            .permisson(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
            .callback(object : AbsPermissionCallback() {
                override fun onResult(isAllGrant: Boolean, hasDenied: Boolean, hasRationale: Boolean) {
                    Logger.w(this@Test05AudioRecordActivity, "授权$isAllGrant")

                    if (!isAllGrant) return

                    //音频编码，视频编码，音视频合成muxer
                    initAudioCodec()
                    initVideoCodec()
                    initMediaMuxer()

                    startAudioRecord()
                    initCamera()
                }

            }).request()
    }


    var isRecording = false;    //停止录制pcm

    var isAudioRecording = false; //停止编码audio
    var isMuxerStatus = false     //混合器 启动状态
    var lastPTS = 0L    //音频音轨最后时间戳

    var isVideoRecording = false //编码video


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

            //初始化配置往上挪
//            //音频编码，视频编码，音视频合成muxer
//            initAudioCodec()
//            initVideoCodec()
//            initMediaMuxer()

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

                    //long timeoutUs：用于等待返回可用buffer的时间
                    //timeoutUs == 0立马返回
                    //timeoutUs < 0无限期等待可用buffer
                    //timeoutUs > 0等待timeoutUs时间
                    var bufferIndex = audioCodec.dequeueInputBuffer(0)  //返回用于填充有效数据输入buffer的索引，如果当前没有可用buffer则返回-1
                    if (bufferIndex < 0) {
                        SystemClock.sleep(10)
                        continue
                    }
                    var byteBuffer =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            audioCodec.getInputBuffer(bufferIndex)  //获取需要编码数据的输入流队列，返回的是一个ByteBuffer数组
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
                    var pts = getPTSUs()
                    Logger.w("pcm一帧 时间戳 $pts")
                    audioCodec.queueInputBuffer(bufferIndex, 0, readSize, pts, 0) //将填充好的buffer发给MediaCodec

                } else {
                    Logger.w("pcm采集异常", readSize)
                }

                SystemClock.sleep(10)
            }


        }).start()


    }

    fun getPTSUs(): Long {
        var result = System.nanoTime() / 1000L
        if (result < lastPTS) result = lastPTS
        return result
    }

    var bos: BufferedOutputStream? = null
    var fos: FileOutputStream? = null
    var file: File? = null


    override fun onDestroy() {
        super.onDestroy()
        isAudioRecording = false
        isRecording = false

        isVideoRecording = false

        bos?.close()
        fos?.close()


        if (isVideoRecording) {
            isVideoRecording = false
            camera.setPreviewCallback(null)
            camera.stopPreview()
            camera.lock()
            camera.release()
            //camera = null
        }

        if (isMuxerStatus) {
            mediaMuxer.stop()
            mediaMuxer.release()
            isMuxerStatus = false
        }


    }


    //音频 编码
    lateinit var audioCodec: MediaCodec

    fun initAudioCodec() {
        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC) //AAC 音频

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2) //单声道1，双声道2
        format.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
//        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 6) //单声道1，双声道2
//        format.setInteger(MediaFormat.KEY_BIT_RATE, 24000)
//        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
//        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)

        audioCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    //视频 编码
    lateinit var videoCodec: MediaCodec

    fun initVideoCodec() {
        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)   //可通过Camera#Parameters#getSupportedPreviewFpsRange获取
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoWidth * videoHeight * 3)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)  //每秒关键帧数

        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);

        //修复绿屏3-2
//        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)

        videoCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // surface = videoCodec.createInputSurface()
    }


    //lateinit var surface: Surface

    //音视频混合器，把编码后的音视频数据合在一起，生成mp4等格式
    lateinit var mediaMuxer: MediaMuxer

    fun initMediaMuxer() {
        val dir = getExternalFilesDir(null)
        var fileMuxer = File(dir?.canonicalPath + "/test05_" + System.currentTimeMillis() + ".mp4")

        mediaMuxer = MediaMuxer(fileMuxer.canonicalPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }


    var traceCount = 0
    fun startMuxer() {
        if (traceCount < 2 || isMuxerStatus) return
        if (isMuxerStatus) return
        mediaMuxer.start()
        isMuxerStatus = true
    }

    //音频轨道
    inner class AudioCodecThread : Thread() {

        var bufferInfo = MediaCodec.BufferInfo()    //缓冲区
        var audioTrackIndex = -1    //track 下标
        //var presentationTimeUs = 0L

        override fun run() {
            super.run()
            audioCodec.start()

            while (isAudioRecording) {

                //获取一帧解码完成的数据到bufferInfo，没有数据就阻塞
                //已成功解码的输出buffer的索引或INFO_*常量之一(INFO_TRY_AGAIN_LATER, INFO_OUTPUT_FORMAT_CHANGED 或 INFO_OUTPUT_BUFFERS_CHANGED)。
                //返回INFO_TRY_AGAIN_LATER而timeoutUs指定为了非负值，表示超时了。
                //返回INFO_OUTPUT_FORMAT_CHANGED表示输出格式已更改，后续数据将遵循新格式。
                var outBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0)
                Logger.w(javaClass.canonicalName, outBufferIndex)

                //第一次返回-2，此时添加音轨
                if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    bufferInfo.presentationTimeUs = getPTSUs()
                    audioTrackIndex = mediaMuxer.addTrack(audioCodec.outputFormat)
                    lastPTS = bufferInfo.presentationTimeUs

                    Logger.w("音频轨道 已添加", bufferInfo.size, bufferInfo.presentationTimeUs)
                    //音频音轨 和视频音轨?添加完成后，启动混合器
                    //音频及视频都准备好后再启动混合器
                    traceCount++
                    startMuxer()

                } else {

                    while (isAudioRecording && outBufferIndex >= 0) {
                        //判断是否启动混合器
                        if (!isMuxerStatus) {
                            Logger.w("音频:混合器尚未启动")
                            SystemClock.sleep(10)
                            continue
                        }

                        //获取编解码之后的数据输出流队列，使用outBufferIndex索引，拿到输出buffer
                        var outputBuffer = audioCodec.getOutputBuffers()[outBufferIndex]
                        //半天找到这么个解释。。。。
                        //如果API<=19，需要根据BufferInfo的offset偏移量调整ByteBuffer的位置
                        //并且限定将要读取缓存区数据的长度，否则输出数据会混乱
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

                        //压榨剩余价值
                        var data = ByteArray(outputBuffer.remaining())  //remaining 剩余
                        outputBuffer.get(data, 0, data.size)

                        //将输出buffer返回给codec或将其渲染在输出surface。
                        //boolean render：如果在配置codec时指定了一个有效的surface，则传递true会将此输出buffer在surface上渲染。一旦不再使用buffer，该surface将把buffer释放回codec。
                        //处理完成，释放ByteBuffer数据。
                        audioCodec.releaseOutputBuffer(outBufferIndex, false)
                        outBufferIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0)

                    }

                }

            }
        }

    }

    //视频轨道 添加
    //跟音频添加轨道几乎一模一样
    inner class VideoCodecThread : Thread() {

        var bufferInfo = MediaCodec.BufferInfo()    //缓冲区
        var trackIndex = -1    //track 下标

        override fun run() {
            super.run()
            videoCodec.start()

            while (isVideoRecording) {

//                Logger.w("videoCodecThread muxer $isMuxerStatus $mediaMuxer")
//                if (!isMuxerStatus) {
//                    sleep(1000)
//                    continue
//                }

                //获取一帧解码完成的数据到bufferInfo，没有数据就阻塞
                var outBufferIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 0)

                //第一次返回-2，此时添加音轨
                if (outBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    //grantorIdx must be less than 3,Muxer is not initialized. 看起来视频跑在了音频前面
                    //addTrack方法必须在MUXER_STATE_INITIALIZED状态下调用，也就是在start之前
                    trackIndex = mediaMuxer.addTrack(videoCodec.outputFormat)

                    Logger.w("视频轨道已添加 $trackIndex")
                    //音频及视频都准备好后再启动混合器
                    traceCount++
                    startMuxer()
                } else {

                    while (isVideoRecording && outBufferIndex >= 0) {
                        //判断是否启动混合器
                        if (!isMuxerStatus) {
                            Logger.w("视频：混合器尚未启动")
                            SystemClock.sleep(10)
                            continue
                        }

                        var outputBuffer = videoCodec.getOutputBuffers()[outBufferIndex]
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        bufferInfo.presentationTimeUs = getPTSUs()
                        //编码后的音频数据写入混合器
                        mediaMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)

                        videoCodec.releaseOutputBuffer(outBufferIndex, false)
                        outBufferIndex = videoCodec.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            }
        }

    }

    lateinit var vTexture: TextureView
    lateinit var camera: Camera

    //修复绿屏3-3 需要各处保持分辨率一致
    val videoWidth = 1280
    val videoHeight = 720

    fun initCamera() {
        vTexture = TextureView(this)
        layoutContent.addView(vTexture)


        vTexture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                Logger.w(javaClass.canonicalName, "onSurfaceTextureSizeChanged", width, height)
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                Logger.w(javaClass.canonicalName, "onSurfaceTextureUpdated", surface)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Logger.w(javaClass.canonicalName, "onSurfaceTextureDestroyed", surface)
//                camera.stopPreview()
//                camera.release()
                return false
            }

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                Logger.w(javaClass.canonicalName, "onSurfaceTextureAvailable", surface, width, height)

                val cameraId = 1
                camera = Camera.open(cameraId) //打开 0后置 1前置 相机
                if (null == camera || isVideoRecording) return

                //显示方向
                camera.setDisplayOrientation(90)

                val params = camera.parameters
                //params.setPreviewSize(width, height)

                params.setPreviewSize(videoWidth, videoHeight)
                val bestSize = getCameraResolution(params, Point(width, height))
                bestSize.let {
                    Logger.w("best", bestSize.toString(), width, height)
                }
//                bestSize?.x?.let { x -> params.setPreviewSize(x, bestSize.y) }

                //设置自动对焦
//                val focusModels = params.supportedFocusModes
//                if (focusModels.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
//                    params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
//                    camera.parameters = params
//                }

                //前置摄像头时，配置此参数会崩溃
                if (0 == cameraId) params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO

                camera.parameters = params  //设置配置参数

                camera.setPreviewTexture(surface)   //绑定相机和预览View
                camera.startPreview()   //开始预览

                isVideoRecording = true
                VideoCodecThread().start()

                val videoInputThread = VideoInputThread()
                videoInputThread.start()

                showSupportVideoFormat()

                camera.setPreviewCallback(object : Camera.PreviewCallback {
                    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
                        //Thread[main,5,main] 主线程返回
                        Logger.w(javaClass.name, "onPreviewFrame", data, camera, Thread.currentThread())
                        if (null == camera || null == data) return
                        val cameraSize = camera.parameters.previewSize

                        //val image =
                        //录出来绿屏。。。。
                        //onPreviewFrame默认返回格式为 he default will be the YCbCr_420_SP (NV21) format
                        //采用MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar格式录制，把NV21转成NV12保存即可解决
                        var yuv420sp = ByteArray(videoWidth * videoHeight * 3 / 2)
                        convertNv21ToNv12(data, yuv420sp, videoWidth, videoHeight)

                        //videoInputThread.putData(data, data.size)
                        videoInputThread.putData(yuv420sp, yuv420sp.size)


                    }

                })
            }

        }
    }

    private fun getCameraResolution(
        parameters: Camera.Parameters,
        screenResolution: Point
    ): Point? {
        var tmp = 0f
        var mindiff = 100f
        val x_d_y = 1F * screenResolution.x / screenResolution.y
        var best: Camera.Size? = null
        val supportedPreviewSizes: List<Camera.Size> = parameters.supportedPreviewSizes
        for (s in supportedPreviewSizes) {
            Logger.w("支持的分辨率 ${s.width}   ${s.height}")
            tmp = Math.abs(s.height / s.width - x_d_y)
            if (tmp < mindiff) {
                mindiff = tmp
                best = s
            }
        }
        return Point(best!!.width, best.height)
    }


    inner class VideoInputThread : Thread() {

        override fun run() {
            super.run()

            while (isVideoRecording) {
                sleep(800)

                Logger.w("${javaClass.name}, $data, $dataSize")

                if (null == data) continue

                //long timeoutUs：用于等待返回可用buffer的时间
                //timeoutUs == 0立马返回
                //timeoutUs < 0无限期等待可用buffer
                //timeoutUs > 0等待timeoutUs时间
                //dequeueInputBuffer can't be used with input surface : 注释initVideoCodec的赋值surface
                var bufferIndex = videoCodec.dequeueInputBuffer(0)  //返回用于填充有效数据输入buffer的索引，如果当前没有可用buffer则返回-1
                if (bufferIndex < 0) {
                    SystemClock.sleep(10)
                    return
                }
                var byteBuffer =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        videoCodec.getInputBuffer(bufferIndex)  //获取需要编码数据的输入流队列，返回的是一个ByteBuffer数组
                    } else {
                        videoCodec.inputBuffers[bufferIndex]
                    }
                if (null == byteBuffer) {
                    SystemClock.sleep(10)
                    return
                }
                byteBuffer.clear()
                byteBuffer.put(data)  //填充pcm

                //presentationTimeUs += (1.0 * bufferSize / (sampleRate * 2 * (audioFormat / 8)) * 1000000).toLong()
                //Logger.w("pcm一帧 时间戳 " + presentationTimeUs / 1000000)
                var pts = getPTSUs()
                Logger.w("video camera 一帧 时间戳 $pts")
                videoCodec.queueInputBuffer(bufferIndex, 0, dataSize, pts, 0) //将填充好的buffer发给MediaCodec
            }
        }

        var data: ByteArray = byteArrayOf()
        var dataSize = 0
        fun putData(data: ByteArray, dataSize: Int) {
            this.data = data
            this.dataSize = dataSize
        }
    }

    fun showSupportVideoFormat() {
        /**
         * 格式在 android.graphics.ImageFormat
         * api在 developer.android.com/reference/android/graphics/ImageFormat
         *
         * NV21: Constant Value: 17 (0x00000011)
         * YV12: Constant Value: 842094169 (0x32315659)
         */
        val previewFormats: List<Int> = camera.getParameters().getSupportedPreviewFormats()
        previewFormats.forEach { Logger.w("showSupportVideoFormat $it") }
    }


    //修复绿屏3-1
    fun convertNv21ToNv12(nv21: ByteArray, nv12: ByteArray, width: Int, height: Int) {
        if (nv21 == null || nv12 == null) return;
        var framesize = width * height
        var i = 0;
        var j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);

        while (i < framesize) {
            nv12[i] = nv21[i];
            i++
        }

        while (j < framesize / 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
            j += 2
        }

        j = 0
        while (j < framesize / 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
            j += 2
        }
    }

    //问题遗留1：
    // 存储的mp4使用 IINA播放器无法播放，表现为音频正常播放，视频帧不动，时间进度显示也不动，
    // 但使用QuickTime Player 表现均正常
    // 奇怪，相同的视频 后来再用 IINA打开进度条又正常了。。。。


}