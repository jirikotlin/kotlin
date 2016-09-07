import android.annotation.SuppressLint
import android.util.Log
import android.util.Log.DEBUG

@SuppressWarnings("UnusedDeclaration")
class LogTest {

    fun checkConditional(m: String) {
        Log.d(TAG1, "message") // ok: unconditional, but not performing computation
        Log.d(TAG1, m) // ok: unconditional, but not performing computation
        Log.d(TAG1, "a" + "b") // ok: unconditional, but not performing non-constant computation
        Log.d(TAG1, Constants.MY_MESSAGE) // ok: unconditional, but constant string
        Log.i(TAG1, "message" + m) // error: unconditional w/ computation
        Log.i(TAG1, toString()) // error: unconditional w/ computation
        Log.e(TAG1, toString()) // ok: only flagging debug/info messages
        Log.w(TAG1, toString()) // ok: only flagging debug/info messages
        Log.wtf(TAG1, toString()) // ok: only flagging debug/info messages
        if (Log.isLoggable(TAG1, 0)) {
            Log.d(TAG1, toString()) // ok: conditional
        }
    }

    fun checkWrongTag(tag: String) {
        if (Log.isLoggable(TAG1, Log.DEBUG)) {
            Log.d(TAG2, "message") // warn: mismatched tags!
        }
        if (Log.isLoggable("my_tag", Log.DEBUG)) {
            Log.d("other_tag", "message") // warn: mismatched tags!
        }
        if (Log.isLoggable("my_tag", Log.DEBUG)) {
            Log.d("my_tag", "message") // ok: strings equal
        }
        if (Log.isLoggable(TAG1, Log.DEBUG)) {
            Log.d(LogTest.TAG1, "message") // OK: same tag; different access syntax
        }
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, "message") // ok: same variable
        }
    }

    fun checkLongTag(shouldLog: Boolean) {
        if (shouldLog) {
            // String literal tags
            Log.d("short_tag", "message") // ok: short
            Log.d("really_really_really_really_really_long_tag", "message") // error: too long

            // Resolved field tags
            Log.d(TAG1, "message") // ok: short
            Log.d(TAG22, "message") // ok: short
            Log.d(TAG23, "message") // ok: threshold
            Log.d(TAG24, "message") // error: too long
            Log.d(LONG_TAG, "message") // error: way too long

            // Locally defined variable tags
            val LOCAL_TAG = "MyReallyReallyReallyReallyReallyLongTag"
            Log.d(LOCAL_TAG, "message") // error: too long

            // Concatenated tags
            Log.d(TAG22 + TAG1, "message") // error: too long
            Log.d(TAG22 + "MyTag", "message") // error: too long
        }
    }

    fun checkWrongLevel(tag: String) {
        if (Log.isLoggable(TAG1, Log.DEBUG)) {
            Log.d(TAG1, "message") // ok: right level
        }
        if (Log.isLoggable(TAG1, Log.INFO)) {
            Log.i(TAG1, "message") // ok: right level
        }
        if (Log.isLoggable(TAG1, Log.DEBUG)) {
            Log.v(TAG1, "message") // warn: wrong level
        }
        if (Log.isLoggable(TAG1, DEBUG)) { // static import of level
            Log.v(TAG1, "message") // warn: wrong level
        }
        if (Log.isLoggable(TAG1, Log.VERBOSE)) {
            Log.d(TAG1, "message") // warn? verbose is a lower logging level, which includes debug
        }
        if (Log.isLoggable(TAG1, Constants.MY_LEVEL)) {
            Log.d(TAG1, "message") // ok: unknown level alias
        }
    }

    @SuppressLint("all")
    fun suppressed1() {
        Log.d(TAG1, "message") // ok: suppressed
    }

    @SuppressLint("LogConditional")
    fun suppressed2() {
        Log.d(TAG1, "message") // ok: suppressed
    }

    private object Constants {
        val MY_MESSAGE = "My Message"
        val MY_LEVEL = 5
    }

    companion object {
        val TAG1 = "MyTag1"
        val TAG2 = "MyTag2"
        val TAG22 = "1234567890123456789012"
        private val TAG23 = "12345678901234567890123"
        private val TAG24 = "123456789012345678901234"
        private val LONG_TAG = "MyReallyReallyReallyReallyReallyLongTag"
    }
}