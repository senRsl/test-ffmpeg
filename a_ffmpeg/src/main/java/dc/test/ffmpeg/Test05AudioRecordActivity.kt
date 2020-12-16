package dc.test.ffmpeg

import android.content.Context
import android.content.Intent
import dc.android.base.activity.BridgeActivity

/**
 *
 *
 * @ClassName: Test05AudioRecordActivity
 * @author senrsl
 *
 * @Package: dc.test.ffmpeg
 * @CreateTime: 2020/12/15 5:13 下午
 */
class Test05AudioRecordActivity :BridgeActivity(){

    companion object {
        @JvmStatic
        fun start(context: Context) {
            val starter = Intent(context, Test05AudioRecordActivity::class.java)
            context.startActivity(starter)
        }
    }



}