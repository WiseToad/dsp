package com.groupstp.dsp.smartsearch.config

/**
 * Свойства поискового атрибута.
 */
class SmartSearchAttribute(
    val contextName: String,
    val attributeName: String,
    val attributeNumber: Int,
    val indexAlias: String, // Значение из файла конфигурации, дополненное префиксом системы и суффиксом с номером артибута
    val valueType: String,
) {
    val uniqueName = "$contextName.$attributeName"
    lateinit var context: SmartSearchContext
}
