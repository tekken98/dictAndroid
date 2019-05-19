package tekken.Dict

import android.util.Log
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayList

internal class Dict(filename: String) {
    var d_isIndex: FileInputStream? = null
    var d_isDict: FileInputStream? = null
    var d_isCache: FileInputStream? = null
    var d_Success: Boolean = false

    var d_offsetArray: IntArray? = null // store evevy index word offset
    var d_count: Int = 0  // current indexFile word index

    lateinit var d_indexCache: Array<Array<Cache?>> // [i][j]

    var currentWord: String? = null // current word
    var d_ip: Int = 0  // current word index
    var d_over: Boolean = false  // indexfile end flag
    var d_buff: ByteArray? = null  // indexFile buff
    var d_wordBuff: ByteArray? = null

    var d_buffBegin: Int = 0 // file's offset at buffBegin
    var d_offset: Int = 0 //  indexFile offset

    var d_buffEnd: Int = 0

    var d_explainOffset: Int = 0
    var d_explainSize: Int = 0
    var d_explainHashArray: HashMap<String, Cache> = HashMap<String, Cache>()
    var d_explainCache: Cache? = null

    val BUFFSIZE = 40960
    val CACHESIZE = 128
    var d_dictFileName: String? = null

    fun getCurWord(): String? {
        return currentWord
    }

    internal inner class Cache {
        var d_flag: Int = 0
        var d_offset: Int = 0
        var d_count: Int = 0
    }

    internal inner class IntBuff {
        var byte: Int = 0
        var buff: ByteArray? = null

        constructor (size: Int) {
            buff = null
            byte = 0
            buff = ByteArray(size)
        }


        fun storeInt(i: Int) {
            if (buff != null) {
                buff!![byte + 3] = (i and 0xff).toByte()
                buff!![byte + 2] = (i shr 8 and 0xff).toByte()
                buff!![byte + 1] = (i shr 16 and 0xff).toByte()
                buff!![byte] = (i shr 24 and 0xff).toByte()
                byte += 4
            }
        }
    }

    init {
        d_dictFileName = filename
        if (init())
            initCache()
    }

    fun isSuccess(): Boolean {
        return d_Success
    }

    fun init(): Boolean {
        try {
            if (d_isIndex != null)
                d_isIndex!!.close()
            if (d_isDict != null)
                d_isIndex!!.close()

            d_isIndex = FileInputStream(d_dictFileName!! + ".idx")
            d_isDict = FileInputStream(d_dictFileName!! + ".dict")

            d_buffBegin = 0
            d_offset = 0
            d_buffEnd = BUFFSIZE
            if (d_buff == null)
                d_buff = ByteArray(BUFFSIZE)
            if (d_explainCache == null) {
                d_explainCache = Cache()
                d_explainCache!!.d_offset = 0
                d_explainCache!!.d_count = 0
                d_explainCache!!.d_flag = 0
            }
            d_ip = 0
            d_over = false
            d_count = 0
            Log.d("debug", "init Dict")
            return true
        } catch (e: Exception) {
            Log.d("debug", e.toString())
            return false
        }

    }

    fun moveBuff() {
        var i = d_ip
        var j = 0
        while (i < d_buffEnd) {
            d_buff!![j] = d_buff!![i]
            i++
            j++
        }
        d_buffBegin += d_ip
    }

    fun readIndexBuff(count: Int): Boolean {
        try {
            val offset = BUFFSIZE - count
            val ret = d_isIndex!!.read(d_buff, offset, count)
            if (ret < count && ret > 0) {
                d_over = true
                d_buffEnd = offset + ret
            }
            d_ip = 0
        } catch (e: Exception) {
            println(e.toString())
            return false
        }

        return true
    }

    fun toInt(buff: ByteArray, i: Int): Int {
        return (buff[i + 0].toInt() and 0xff).shl(24) or
                (buff[i + 1].toInt() and 0xff).shl(16) or
                (buff[i + 2].toInt() and 0xff).shl(8) or
                (buff[i + 3].toInt() and 0xff)
    }

    fun nextIndex(): Boolean {
        if (d_ip >= d_buffEnd && d_over == true) {
            return false
        }
        var i: Int
        do {

            i = d_ip
            while (i < d_buffEnd) {
                if (d_buff!![i].toInt() == 0x0)
                    break
                i++
            }
            if (i + 9 > d_buffEnd) {
                if (d_over == false) {
                    moveBuff()
                    if (!readIndexBuff(d_ip)) {
                        return false
                    }
                } else
                    return false
            } else {
                val w = ByteArray(i - d_ip)

                for (j in d_ip until i) {
                    w[j - d_ip] = d_buff!![j]
                }
                currentWord = String(w)
                d_offset = d_buffBegin + d_ip
                d_explainOffset = toInt(d_buff!!, i + 1)
                d_explainSize = toInt(d_buff!!, i + 5)
                d_ip = i + 9
                d_count++
            }
        } while (d_ip == 0)
        return true
    }

    fun preIndex(): Boolean {
        var tmp = d_count--
        init()
        d_count = --tmp
        d_count = if (d_count < 0) 0 else d_count
        try {
            d_isIndex!!.skip(d_offsetArray!![d_count].toLong())
            readIndexBuff(BUFFSIZE)
            nextIndex()
            d_count--
        } catch (e: Exception) {
            return false
        }

        return true
    }

    fun skipCache(w: String): Boolean {

        val first: Char
        val second: Char
        if (w.length > 1) {
            first = w[0]
            second = w[1]
        } else {
            first = w[0]
            second = 0x0.toChar()
        }

        try {
            val i = first.toInt()
            val j = second.toInt()
            msg("skip i: " + i + " " + i.toChar() + " j:" + " " + j + " " + j.toChar())
            if (d_indexCache[i][j]!!.d_flag == 1) {
                msg(" in [i][j] " + first.toString() + ":" + second.toString() + " " + d_indexCache[i][j]!!.d_offset.toString())
                d_isIndex!!.skip(d_indexCache[i][j]!!.d_offset.toLong())
                d_count = d_indexCache[i][j]!!.d_count
            } else if (d_indexCache[i][0]!!.d_flag == 1) {
                d_isIndex!!.skip(d_indexCache[i][0]!!.d_offset.toLong())
                d_count = d_indexCache[i][0]!!.d_count
            } else {
                return false
            }
            readIndexBuff(BUFFSIZE)
            return true
        } catch (e: Exception) {
            return false
        }

    }

    fun min(a: Int, b: Int): Int {
        return if (a > b) b else a
    }

    fun findWord2(w: String): String {
        var tempOffset = 0
        var tempSize = 0
        init()
        if (!skipCache(w))
            return ""
        val len = w.length
        var max = 0
        var tempWord = ""
        val W = w.toUpperCase()
        while (nextIndex()) {
            var i: Int
            val Wd = currentWord!!.toUpperCase()
            val maxLength = min(len, Wd.length)
            i = 0
            while (i < maxLength) {
                if (W[i] == Wd[i]) {
                    i++
                    continue
                } else
                    break
            }
            if (max <= i) {
                if (max < i) {
                    tempOffset = d_explainOffset
                    tempSize = d_explainSize
                    tempWord = currentWord!!
                    max = i
                    if (max == len)
                        break
                }
            } else
                break
        }
        if (tempOffset == d_explainCache!!.d_offset)
            return "SAME"
        else {
            currentWord = tempWord
            d_explainCache!!.d_offset = tempOffset
            d_explainCache!!.d_count = tempSize
        }
        return "CONT"
    }

    fun flushCurrentCache() {
        d_explainCache!!.d_offset = 0
    }

    fun findWord(w: String): String? {
        if (w.length == 0) return ""
        val tempOffset: Int
        val tempSize: Int

        if (w.length > 2 && d_explainHashArray.containsKey(w)) {
            val ca = d_explainHashArray.get(w)
            Log.d("debug", "find HashMap :" + w)
            tempOffset = ca!!.d_offset
            tempSize = ca.d_count
            currentWord = w
            if (tempOffset == d_explainCache!!.d_offset) {
                return "SAME"
            }
        } else {
            val tmp = findWord2(w)
            tempOffset = d_explainCache!!.d_offset
            tempSize = d_explainCache!!.d_count
            val cache = Cache()
            cache.d_offset = tempOffset
            cache.d_count = tempSize
            if (w.length > 2 && !d_explainHashArray.containsKey(w))
                d_explainHashArray.set(w, cache)
            if (tmp.equals("SAME"))
                return "SAME"
        }
        val a: String = "tempOffset is " + tempOffset.toString() + " tempSize is " + tempSize.toString()
        Log.d("debug", a)
        return getWord(tempOffset, tempSize)
    }

    fun dumpWordAndDict() {
        println(currentWord)
        val w = getWord(d_explainOffset, d_explainSize)
        println(w)
    }

    fun findNextWord(): String? {
        nextIndex()
        return getWord(d_explainOffset, d_explainSize)
    }

    fun findPreWord(): String? {
        preIndex()
        return getWord(d_explainOffset, d_explainSize)
    }

    fun getWord(offset: Int, size: Int): String? {
        try {
            d_wordBuff = ByteArray(size)
            d_isDict!!.close()
            d_isDict = FileInputStream(d_dictFileName!! + ".dict")
            d_isDict!!.skip(offset.toLong())
            d_isDict!!.read(d_wordBuff, 0, size)
            return String(d_wordBuff!!)
        } catch (io: Exception) {
            Log.d("debug", "get null explain word!")
            return null
        }

    }

    fun initCache() {
        d_indexCache = Array(CACHESIZE) { arrayOfNulls<Cache>(CACHESIZE) }
        for (i in 0 until CACHESIZE)
            for (j in 0 until CACHESIZE) {
                d_indexCache[i][j] = Cache()
                d_indexCache[i][j]!!.d_flag = 0
                d_indexCache[i][j]!!.d_offset = 0
            }
        try {
            d_isCache = FileInputStream(d_dictFileName!! + ".cache")
        } catch (e: Exception) {
            Log.d("debug", d_dictFileName + " has no cache ,try to make one!")
            makeCache()
        }
        var i: Int
        var j: Int
        if (d_isCache == null) {
            try {
                d_isCache = FileInputStream(d_dictFileName!! + ".cache")
            } catch (e: Exception) {
                Log.d("debug", d_dictFileName + "open error!")
                return
            }
        }

        Log.d("debug", " open cache file" + d_dictFileName)
        d_isCache!!.read(d_buff, 0, 4)
        val w = toInt(d_buff!!, 0)
        Log.d("debug", "cache size is " + w.toString())
        var buff: ByteArray? = ByteArray(w * 16)
        d_isCache!!.read(buff!!, 0, w * 16)
        var k = 0
        msg("Load Cache...")
        while (k < w * 16) {
            i = toInt(buff, k)
            j = toInt(buff, k + 4)
            d_indexCache[i][j]!!.d_flag = 1
            d_indexCache[i][j]!!.d_offset = toInt(buff, k + 8)
            d_indexCache[i][j]!!.d_count = toInt(buff, k + 12)
            k += 16
        }
        msg("finished!")
        d_isCache!!.read(d_buff, 0, 4)
        val size = toInt(d_buff!!, 0)
        msg("Read Offset size is " + size.toString())
        d_offsetArray = IntArray(size)

        buff = ByteArray(size * 4)
        d_isCache!!.read(buff, 0, size * 4)
        i = 0
        while (i < size) {
            d_offsetArray!![i] = toInt(buff, i * 4)
            i++
        }
        d_Success = true
        readIndexBuff(BUFFSIZE)
    }


    fun msg(a: Int) {
        Log.d("debug", a.toString())
    }

    fun msg(a: String) {
        Log.d("debug", a)
    }

    fun makeCache() {
        var w: String
        var first: Char
        var second: Char
        var count = 0
        var cacheCount = 0
        val list = ArrayList<Int>()
        list.add(0)
        readIndexBuff(BUFFSIZE)
        while (nextIndex()) {
            w = currentWord!!
            if (w.length > 1) {
                first = w[0]
                second = w[1]
            } else {
                first = w[0]
                second = 0x0.toChar()
            }
            val i = first.toInt()
            val j = second.toInt()
            if (i >= 0 && i < 128 && j >= 0 && j < 128) {
                if (d_indexCache[i][j]!!.d_flag == 0) {
                    d_indexCache[i][j]!!.d_flag = 1
                    cacheCount++
                    d_indexCache[i][j]!!.d_offset = d_offset
                    d_indexCache[i][j]!!.d_count = count
                }
            }
            list.add(d_offset)
            count++
            //Log.d("debug",currentWord )
        }
        Log.d("debug", d_Success.toString())
        d_offsetArray = IntArray(list.size)
        println(list.size)
        for (i in list.indices) {
            d_offsetArray!![i] = list[i]
        }

        writeCache(cacheCount)
    }

    private fun writeCache(cacheCount: Int) {
        val total: Int
        val ib: IntBuff
        val size = d_offsetArray!!.size
        total = cacheCount * 4 * 4 + 4 + size * 4 + 4

        ib = IntBuff(total)
        var i = 0
        try {
            ib.storeInt(cacheCount)
            msg("Write Cachecount : " + cacheCount.toString()) //1093
            i = 0
            while (i < CACHESIZE) {
                for (j in 0 until CACHESIZE) {
                    if (d_indexCache[i][j]!!.d_flag == 1) {
                        ib.storeInt(i)
                        ib.storeInt(j)
                        ib.storeInt(d_indexCache[i][j]!!.d_offset)
                        ib.storeInt(d_indexCache[i][j]!!.d_count)
                    }
                }
                i++
            }
            ib.storeInt(size)
            msg("Write offset size :" + size.toString()) //43053
            i = 0
            while (i < size) {
                ib.storeInt(d_offsetArray!![i])
                i++
            }
            val os = DataOutputStream(
                FileOutputStream(d_dictFileName!! + ".cache")
            )
            os.write(ib.buff, 0, ib.byte)
            os.flush()
            os.close()
            d_Success = true
            msg("Make Cache finishied!")

        } catch (e: Exception) {
            Log.d("debug", e.toString())
            msg("cacheCount is " + cacheCount.toString())
            msg("size index is " + i.toString())
            msg("size is " + size.toString())
            msg("total is " + total.toString())
        }
    }
}
