package com.m3u.extension.app

import android.util.Log
import com.m3u.extension.api.runner.CodeRunner

class XXCodeRunner: CodeRunner("coderunner") {
    override fun run() {
        Log.e("TAG", "run from extension!", )
    }
}