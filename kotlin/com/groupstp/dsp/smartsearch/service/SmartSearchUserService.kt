package com.groupstp.dsp.smartsearch.service

import ch.qos.logback.classic.Level
import com.groupstp.dsp.domain.utils.AppLogUtils
import com.groupstp.dsp.domain.utils.expirable.ExpirableCache
import com.groupstp.dsp.smartsearch.config.SmartSearchProps
import liquibase.pro.packaged.it
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import javax.persistence.EntityManager

@Service
class SmartSearchUserService (
    smartSearchProps: SmartSearchProps,
    private val entityManager: EntityManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    //TODO: В перспективе переделать на trigger-driven систему обновления кэша доступов пользователей

    private val isRegopers = ExpirableCache<String?, Boolean?>(smartSearchProps.userCacheTtl) { login ->
        if(login == null) null
        else AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
            entityManager.createNativeQuery("""
                select coalesce(e.work_in_regional_operator, false) as work_in_regional_operator
                from sys_user as u
                    join dsp_employee as e on e.user_id = u.id
                where u.login = ?1
                    and u.delete_ts is null
                    and e.delete_ts is null
            """.trimIndent())
                .setParameter(1, login)
                .resultList.firstOrNull()
                ?.let { it as Boolean }
        }
    }

    private val permittedCarrierIds = ExpirableCache<String?, List<UUID>>(smartSearchProps.userCacheTtl) { login ->
        if(login == null) emptyList()
        else AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
            entityManager.createNativeQuery("""
                select cast(cl.carrier_id as varchar)
                from sys_user as u
                    join dsp_employee as e on e.user_id = u.id
                    join dsp_carrier_employee_link as cl on cl.employee_id = e.id
                where u.login = ?1
                    and e.delete_ts is null
                    and u.delete_ts is null
            """.trimIndent())
                .setParameter(1, login)
                .resultList
                .filterIsInstance(String::class.java)
                .map { UUID.fromString(it) }
        }
    }

    private val permittedRegionIds = ExpirableCache<String?, List<UUID>>(smartSearchProps.userCacheTtl) { login ->
        if(login == null) emptyList()
        else AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
            entityManager.createNativeQuery("""
                select cast(rl.region_id as varchar)
                from sys_user as u
                    join dsp_employee as e on e.user_id = u.id
                    join dsp_region_employee_link as rl on rl.employee_id = e.id
                where u.login = ?1
                    and u.delete_ts is null
                    and e.delete_ts is null
            """.trimIndent())
                .setParameter(1, login)
                .resultList
                .filterIsInstance(String::class.java)
                .map { UUID.fromString(it) }
        }
    }

    private val employeeIds = ExpirableCache<String?, UUID?>(smartSearchProps.userCacheTtl) { login ->
        if (login == null) null
        else AppLogUtils.execWithSqlLogging(log.name, Level.TRACE) {
            entityManager.createNativeQuery(
                """
                select cast(e.id as varchar)
                from sys_user as u
                    join dsp_employee as e on e.user_id = u.id
                where u.login = ?1
                    and u.delete_ts is null
                    and e.delete_ts is null
            """.trimIndent()
            )
                .setParameter(1, login)
                .resultList
                .filterIsInstance(String::class.java)
                .map { UUID.fromString(it) }
                .firstOrNull()
        }
    }

    fun getIsRegoper(login: String?): Boolean? = isRegopers.value(login)

    fun getPermittedCarrierIds(login: String?): List<UUID> = permittedCarrierIds.value(login)

    fun getPermittedRegionIds(login: String?): List<UUID> = permittedRegionIds.value(login)

    fun getEmployeeId(login: String?): UUID? = employeeIds.value(login)
}
