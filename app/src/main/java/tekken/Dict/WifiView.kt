package tekken.Dict

import android.os.Environment
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.InputStreamReader

class WifiView{
      fun getPass():String{
        val info=""
        try{
            val dest = Environment.getExternalStorageDirectory().path
            val cmd = "cp /data/misc/wifi/wpa_supplicant.conf $dest/dic/"
            if (!runRootCmd(cmd)){
                Log.d("Info","Not have root!")
            }else{
                Log.d("Info","Have root!")
            }
            val fis = FileInputStream("$dest/dic/wpa_supplicant.conf")

            val bfr  = BufferedReader(InputStreamReader(fis,"UTF-8"))
            var line:String
            var pass = ""
            while (bfr.ready()){
                line = bfr.readLine()
                pass += line + "\n"
            }
            fis.close()
            return pass
        }catch(ex:Exception){
            Log.d("Debug",ex.toString())
        }
        return info
    }
     fun runRootCmd(cmd : String): Boolean {
        var process: Process? = null
        var os: DataOutputStream? = null
        try {
            Log.d("Info",cmd)
            process = Runtime.getRuntime().exec("su") //切换到root帐号
            os = DataOutputStream(process!!.outputStream)
            os.writeBytes(cmd + "\n")
            os.writeBytes("exit\n")
            os.flush()
            return process.waitFor() == 0
        } catch (e: Exception) {
            Log.d("Error",e.toString())
            return false
        } finally {
            try {
                if (os != null) {
                    os.close()
                }
                process!!.destroy()
            } catch (e: Exception) {
                Log.d("Error",e.toString())
            }
        }

    }
}

