package com.groupstp.dsp.domain.utils.streamedcache

import java.io.*

class FileStreamedCache(prefix: String): StreamedCache {

    private val file = File.createTempFile(prefix, ".tmp")

    override fun getOutputStream(): OutputStream = FileOutputStream(file)

    override fun getInputStream(): InputStream = FileInputStream(file)

    override fun close() {
        file.delete()
    }
}
