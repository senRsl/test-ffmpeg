package dc.test.ffmpeg

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
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


    var isRecording = false;    //停止录制

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

            while (isRecording) {
                val buffer = ByteArray(bufferSize)
                val readSize = audioRecord.read(buffer, 0, bufferSize);
                if (readSize > 0) {
                    Logger.w(javaClass.canonicalName, readSize, dir?.canonicalPath, "audio04.pcm")
                    //成功读取pcm音频
                    // FileUtils.byte2File(buffer, 0, readSize, dir?.canonicalPath, "audio04.pcm")  //非要搞那么复杂，手动创建一个

                    //终于保存成功了。。。。
                    bos!!.write(buffer, 0, readSize)

                } else {
                    Logger.w("pcm采集异常", readSize)
                }

                SystemClock.sleep(10)
            }

        }).start()


    }

    var bos: BufferedOutputStream? = null
    var fos: FileOutputStream? = null
    var file: File? = null


    override fun onDestroy() {
        super.onDestroy()
        isRecording = false

        bos?.close()
        fos?.close()
    }


}