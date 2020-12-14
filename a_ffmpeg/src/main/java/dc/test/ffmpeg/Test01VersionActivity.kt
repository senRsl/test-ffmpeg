package dc.test.ffmpeg

import android.content.Context
import android.content.Intent
import dc.android.base.activity.BridgeActivity
import dc.common.Logger

/**
 *
 *
 * @ClassName: Test01VersionActivity
 * @author senrsl
 *
 * @Package: dc.test.ffmpeg
 * @CreateTime: 2020/12/12 5:44 下午
 */
class Test01VersionActivity : BridgeActivity() {

    init {
        System.loadLibrary("ffutils")
        System.loadLibrary("avutil")
    }

    external fun getVersion(): String
    external fun getVersion431(): String

    companion object {
        @JvmStatic
        fun start(context: Context) {
            val starter = Intent(context, Test01VersionActivity::class.java)
            context.startActivity(starter)
        }
    }

    override fun initLayout() {
        super.initLayout()

    }

    override fun initData() {
        super.initData()

//        2020-12-12 17:42:21.866 15060-15060/dc.test.ffmpeg W/TEST: 	0：dc.test.ffmpeg.TestActivity
//        1：N-100400-g001bc594d8
//        2：4.3.1
        Logger.w(this, javaClass.canonicalName, getVersion(), getVersion431())
    }
}