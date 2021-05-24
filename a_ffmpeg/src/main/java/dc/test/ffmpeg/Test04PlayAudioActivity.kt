package dc.test.ffmpeg

import android.Manifest
import android.content.Context
import android.content.Intent
import dc.android.base.activity.BridgeActivity
import dc.android.common.BridgeContext
import dc.android.libs.PermissionUtils
import dc.android.libs.permission.AbsPermissionCallback
import dc.common.Logger

/**
 *
 *
 * @ClassName: Test04PlayAudioActivity
 * @author senrsl
 *
 * @Package: dc.test.ffmpeg
 * @CreateTime: 2020/12/14 5:48 下午
 */
class Test04PlayAudioActivity : BridgeActivity() {

    init {
        System.loadLibrary("ffutils")
    }

    external fun printAudioInfo2(path: String)
    external fun nativePlay(path: String)

    companion object {
        @JvmStatic
        fun start(context: Context, path: String) {
            val starter = Intent(context, Test04PlayAudioActivity::class.java)
            starter.putExtra(BridgeContext.KEY_VAR_1, path)
            context.startActivity(starter)
        }
    }

    override fun initData() {
        super.initData()

        PermissionUtils.with(this)
                .permisson(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .callback(object : AbsPermissionCallback() {
                    override fun onResult(isAllGrant: Boolean, hasDenied: Boolean, hasRationale: Boolean) {
                        Logger.w(this@Test04PlayAudioActivity, "授权$isAllGrant")

                        //android 11  /mnt/sdcard/ 这个路径不好使了，不断做死的google。。。。
                        //val path = "/mnt/sdcard/SENRSL/audio03.mp3"
//                        val path = "/sdcard/SENRSL/audio03.mp3"
                        val path = intent.getStringExtra(BridgeContext.KEY_VAR_1) ?: return
                        //val file = File(path)
                        printAudioInfo2(path)
                        nativePlay(path)
                    }

                }).request()
    }

}