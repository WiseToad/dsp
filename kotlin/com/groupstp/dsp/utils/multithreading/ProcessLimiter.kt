package com.groupstp.dsp.domain.utils.multithreading

import java.util.concurrent.Semaphore
import kotlin.math.max

/**
 * Ограничитель количества одновременно выполняемых процессов.
 */
class ProcessLimiter(
    limit: Int = 0
) {
    var limit = limit
        set(value) {
            if(value > 0) {
                if(value > semaphoreLimit) {
                    semaphore.release(value - semaphoreLimit)
                } else if(semaphoreLimit > value) {
                    semaphore.reducePermits(semaphoreLimit - value)
                }
                semaphoreLimit = value
            }
            field = value
        }

    private var semaphoreLimit = max(1, limit)

    private val semaphore = object: Semaphore(semaphoreLimit, true) {
        public override fun reducePermits(reduction: Int) = super.reducePermits(reduction)
    }

    fun acquire(): Permit {
        return if(limit > 0) {
            semaphore.acquire()
            Permit(true)
        } else {
            Permit(false)
        }
    }

    inner class Permit(
        private val isAcquired: Boolean
    ): AutoCloseable {
        override fun close() {
            if(isAcquired) {
                semaphore.release()
            }
        }
    }
}
