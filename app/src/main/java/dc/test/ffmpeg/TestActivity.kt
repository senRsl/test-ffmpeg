package dc.test.ffmpeg

import dc.android.base.activity.BridgeActivity
import dc.common.Logger

/**
 *
 *
 * @ClassName: TestActivity
 * @author senrsl
 *
 * @Package: dc.test.ffmpeg
 * @CreateTime: 2020/12/12 4:10 下午
 */
class TestActivity : BridgeActivity() {

    init {
        System.loadLibrary("utils")
        System.loadLibrary("avutil")
    }

    external fun getVersion(): String
    external fun getVersion431(): String


    override fun initLayout() {
        super.initLayout()

    }

    override fun initData() {
        super.initData()

//        2020-12-12 17:42:21.866 15060-15060/dc.test.ffmpeg W/TEST: 	0：dc.test.ffmpeg.TestActivity
//        1：N-100400-g001bc594d8
//        2：4.3.1
        Logger.w(javaClass.canonicalName, getVersion(), getVersion431())
    }

}