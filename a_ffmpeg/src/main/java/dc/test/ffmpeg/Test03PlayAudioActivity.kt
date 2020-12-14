package dc.test.ffmpeg

import android.Manifest
import android.content.Context
import android.content.Intent
import dc.android.base.activity.BridgeActivity
import dc.android.libs.PermissionUtils
import dc.android.libs.permission.AbsPermissionCallback
import dc.common.Logger

/**
 *
 *
 * @ClassName: Test03PlayAudioActivity
 * @author senrsl
 *
 * @Package: dc.test.ffmpeg
 * @CreateTime: 2020/12/14 11:00 上午
 */
class Test03PlayAudioActivity : BridgeActivity() {

    init {
        System.loadLibrary("ffutils")
    }

    external fun printAudioInfo(path: String): String

    companion object {
        @JvmStatic
        fun start(context: Context) {
            val starter = Intent(context, Test03PlayAudioActivity::class.java)
            context.startActivity(starter)
        }
    }

    override fun initData() {
        super.initData()

        PermissionUtils.with(this)
                .permisson(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .callback(object : AbsPermissionCallback() {
                    override fun onResult(isAllGrant: Boolean, hasDenied: Boolean, hasRationale: Boolean) {
                        Logger.w(this@Test03PlayAudioActivity, "授权$isAllGrant")

                        //android 11  /mnt/sdcard/ 这个路径不好使了，不断做死的google。。。。
                        //val path = "/mnt/sdcard/SENRSL/audio03.mp3"
                        val path = "/sdcard/SENRSL/audio03.mp3"
                        //val file = File(path)
                        val result = printAudioInfo(path)
                        Logger.w(result)
                    }

                }).request()
    }

}