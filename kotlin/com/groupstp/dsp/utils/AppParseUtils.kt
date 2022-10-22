package com.groupstp.dsp.domain.utils

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Методы для поддержки парсинга строк.
 */
object AppParseUtils {

    class Range (
        val first: Int,
        val last: Int
    )

    /**
     * Выполнить интерполяцию строки в формате Kotlin.
     *
     * Параметры подстановки задаются в виде $param, либо ${param}. Поддерживаются вложенные параметры вида
     * ${param.dateFrom}. Извлечение элементов списка в виде ${param[0]}, вызов функций и прочие сложные случаи
     * не поддерживаются.
     *
     * @param string  исходная строка
     * @param values  DTO, либо Map<String, Any?> со значениями параметров подстановки
     * @return        интерполированная строка
     */
    @JvmStatic
    fun interpolate(string: String, values: Any?): String {

        val pattern = Pattern.compile("\\$(\\w+)|\\$\\{(.*?)}")
        val matcher = pattern.matcher(string)
        val interpolatedString = StringBuilder()
        while(matcher.find()) {
            var valueString = ""
            for(j in 1..matcher.groupCount()) {
                if(matcher.group(j) != null) {
                    val names = matcher.group(j).split(".")
                    var value = values
                    for (name in names) {
                        val canonizedName = name.trim()
                        value = try {
                            when (value) {
                                null -> break
                                is Map<*, *> -> value[canonizedName]
                                is Collection<*> -> null
                                else -> value::class.members.first { it.name == canonizedName }.call(value)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    valueString = Matcher.quoteReplacement(value?.toString() ?: "")
                    break
                }
            }
            matcher.appendReplacement(interpolatedString, valueString)
        }
        matcher.appendTail(interpolatedString)
        return interpolatedString.toString()
    }

    /**
     * Распарсить строку со списком диапазонов.
     *
     * Диапазоны должны быть перечислены через запятую, границы заданы через '-'.
     * Обе границы являются необязательными и если заданы, то входят в диапазон (являются inclusive).
     * Распарсенный список не проверяется на пересечение диапазонов друг с другом и не сортируется.
     * Проверка на валидность диапазонов и на выход за допустимые границы - выполняется.
     *
     * Пример: 1, 3-5, 7-
     *
     * @param string  исходная строка
     * @param first   допустимая нижняя граница диапазона
     * @param last    допустимая верхняя граница диапазона
     * @return        список диапазонов
     */
    @JvmStatic
    fun parseRanges(string: String, first: Int = 0, last: Int = Int.MAX_VALUE): List<Range> {

        val ranges = string.split(",").mapNotNull { rangeString ->
            if(rangeString.isEmpty()) null
            else {
                val bounds = rangeString.split("-").map { it.trim() }
                try {
                    when (bounds.size) {
                        1 -> Range(bounds[0].toInt(), bounds[0].toInt())
                        2 -> Range(
                            if(!bounds[0].isEmpty()) bounds[0].toInt() else first,
                            if(!bounds[1].isEmpty()) bounds[1].toInt() else last
                        )
                        else -> throw IllegalArgumentException("Диапазон должен быть задан как '{first} - {last}'")
                    }
                }
                catch(e: Exception) {
                    throw IllegalArgumentException("Неверный диапазон: [$rangeString]", e)
                }
            }
        }

        for(range in ranges) {
            if(range.first < first || range.last > last) {
                throw IllegalArgumentException("Выход границ диапазона за допустимые пределы: [${range.first} - ${range.last}]")
            }
            if(range.first > range.last) {
                throw IllegalArgumentException("Нижняя граница диапазона больше верхней: [${range.first} - ${range.last}]")
            }
        }

        return ranges
    }
}
