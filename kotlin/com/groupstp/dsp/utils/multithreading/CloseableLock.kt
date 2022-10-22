package com.groupstp.dsp.domain.utils.multithreading

import java.util.concurrent.locks.Lock

class CloseableLock(
    private val lock: Lock
): AutoCloseable {
    override fun close() {
        lock.unlock()
    }
}
