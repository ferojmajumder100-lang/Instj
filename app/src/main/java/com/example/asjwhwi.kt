package com.example

import android.util.Base64

object Asjwhwi {
    // This is the Base64 encoded version of https://pastebin.com/raw/hkgf3b24
    private const val ENCODED_URL = "aHR0cHM6Ly9wYXN0ZWJpbi5jb20vcmF3L2hrZ2YzYjI0"
    
    val RAW_URL: String
        get() = String(Base64.decode(ENCODED_URL, Base64.DEFAULT))
    
    const val EXPECTED_PACKAGE = "fbtool.coms"
    const val EXPECTED_APP_NAME = "FB TOOL"
}
