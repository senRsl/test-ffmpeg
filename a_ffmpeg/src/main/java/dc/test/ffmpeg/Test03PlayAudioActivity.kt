package dc.test.ffmpeg

import dc.android.base.activity.BridgeActivity

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
        System.loadLibrary("play_audio")
    }

    external fun printAudioInfo(path: String)

}