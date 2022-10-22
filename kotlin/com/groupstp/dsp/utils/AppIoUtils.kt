package com.groupstp.dsp.domain.utils

import org.springframework.core.io.DefaultResourceLoader
import java.io.*
import java.nio.charset.StandardCharsets

/**
 * Утилиты для работы с потоками ввода-вывода и ресурсами.
 *
 * Предназначены для:
 * - устранения ошибок, связанных с незакрытием потоков ввода-вывода
 * - повышения производительности ввода-вывода при помощи неявной буферизации
 * - исключения шаблонного кода
 */
object AppIoUtils {

    /**
     * Загрузить данные из входного потока, используя буферизацию, и закрыть его.
     *
     * @param inputStream    входной поток
     * @param load           пользовательская функция, выполняющая загрузку данных
     * @return               возврат из пользовательской функции
     */
    @JvmStatic
    inline fun <R> loadAll(inputStream: InputStream, load: (BufferedInputStream) -> R): R {
        return inputStream.use {
            BufferedInputStream(inputStream).use { bufferedInputStream ->
                load(bufferedInputStream)
            }
        }
    }

    /**
     * Сохранить данные в выходной поток, используя буферизацию, и закрыть его.
     *
     * @param outputStream   выходной поток
     * @param save           пользовательская функция, выполняющая сохранение данных
     * @return               возврат из пользовательской функции
     */
    @JvmStatic
    inline fun <R> saveAll(outputStream: OutputStream, save: (BufferedOutputStream) -> R): R {
        return outputStream.use {
            BufferedOutputStream(outputStream).use { bufferedOutputStream ->
                save(bufferedOutputStream)
            }
        }
    }

    /**
     * Загрузить данные из входного потока, используя буферизацию, и закрыть его.
     *
     * В отличие от метода loadAll, работает в паре с пользовательской функцией,
     * использующей интерфейс Reader вместо InputStream.
     *
     * @param inputStream    входной поток
     * @param read           пользовательская функция, выполняющая загрузку данных через интерфейс Reader
     * @return               возврат из пользовательской функции
     */
    @JvmStatic
    inline fun <R> readAll(inputStream: InputStream, read: (Reader) -> R): R {
        return loadAll(inputStream) { bufferedInputStream ->
            InputStreamReader(bufferedInputStream, StandardCharsets.UTF_8).use { reader ->
                read(reader)
            }
        }
    }

    /**
     * Сохранить данные в выходной поток, используя буферизацию, и закрыть его.
     *
     * В отличие от метода saveAll, работает в паре с пользовательской функцией,
     * использующей интерфейс Writer вместо OutputStream.
     *
     * @param outputStream   выходной поток
     * @param write          пользовательская функция, выполняющая сохранение данных через интерфейс Writer
     * @return               возврат из пользовательской функции
     */
    @JvmStatic
    inline fun <R> writeAll(outputStream: OutputStream, write: (Writer) -> R): R {
        return saveAll(outputStream) { bufferedOutputStream ->
            OutputStreamWriter(bufferedOutputStream, StandardCharsets.UTF_8).use { writer ->
                write(writer)
            }
        }
    }

    /**
     * Загрузить данные из указанного ресурса.
     *
     * @param location       расположение ресурса
     * @param load           пользовательская функция, выполняющая загрузку данных
     * @return               возврат из пользовательской функции
     */
    @JvmStatic
    inline fun <R> loadResource(location: String, load: (BufferedInputStream) -> R): R {
        return loadAll(DefaultResourceLoader().getResource(location).inputStream, load)
    }

    /**
     * Загрузить текст из указанного ресурса.
     *
     * @param location       расположение ресурса
     * @retrun               текст, загруженный из ресурса
     */
    @JvmStatic
    fun loadResourceText(location: String): String {
        return loadResource(location) { resourceStream ->
            resourceStream.readAllBytes().toString(StandardCharsets.UTF_8)
        }
    }
}
