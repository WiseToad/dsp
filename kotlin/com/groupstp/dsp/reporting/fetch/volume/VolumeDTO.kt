package com.groupstp.dsp.reporting.fetch.volume

import java.util.*

class VolumeDTO (
    val containerGroupId: UUID?,
    val parentRegionName: String?,
    val regionName: String?,
    val containerAreaCode: String?,
    val address: String?,
    val ownerCompanyName: String?,
    val amount: String?,
    val volume: String?,
    val tripsPerDay: Int?,
    val schedule: String?,
    val removalDateAppTzTruncs: Array<Date?>?,
    val factAmounts: Array<Int?>?,
    val factVolumes: Array<Double?>?,
    val factAmountTotal: Int?,
    val factVolumeTotal: Double?,
    val failReasons: String?,
    val tariff: String?
) {
    var rowNum: Int = 0
    var volumeTotal: Double? = null
}
