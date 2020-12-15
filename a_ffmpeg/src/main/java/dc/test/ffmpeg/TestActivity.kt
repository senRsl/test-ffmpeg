package dc.test.ffmpeg

import android.graphics.Color
import android.view.View
import dc.android.base.activity.BridgeActivity

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


    override fun initLayout() {
        super.initLayout()
        setLayout(true, R.layout.activity_test, true, Color.WHITE)
    }


    fun onclick(v: View) {
        when (v.id) {
            R.id.btn_get_version -> Test01VersionActivity.start(this)
            R.id.btn_get_audio_info -> Test03PlayAudioActivity.start(this)
            R.id.btn_get_audio_play -> Test04PlayAudioActivity.start(this)
        }

    }

}