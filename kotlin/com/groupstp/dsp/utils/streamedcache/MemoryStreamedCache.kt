package com.groupstp.dsp.domain.utils.streamedcache

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class MemoryStreamedCache(size: Int = 32): StreamedCache {

    private class BufOutputStream(size: Int): ByteArrayOutputStream(size) {
        // Expose superclass protected attributes
        val buf: ByteArray get() = super.buf
        val count get() = super.count
    }

    private val bufOutputStream = BufOutputStream(size)

    override fun getOutputStream(): OutputStream = bufOutputStream

    override fun getInputStream(): InputStream = ByteArrayInputStream(bufOutputStream.buf, 0, bufOutputStream.count)

    override fun close() {}
}
