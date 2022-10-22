package com.groupstp.dsp.hibernate

import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.lang.reflect.ParameterizedType
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

/**
 * Hibernate-тип: массив элементов заданного типа.
 */
abstract class ArrayType<T> (
    private val sqlElementTypeName: String
): UserType {

    override fun sqlTypes(): IntArray {
        return intArrayOf(Types.ARRAY)
    }

    override fun returnedClass(): Class<*> {
        val javaElementType = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]
        return java.lang.reflect.Array.newInstance(javaElementType as Class<*>, 0).javaClass
    }

    @Throws(HibernateException::class, SQLException::class)
    override fun nullSafeGet(rs: ResultSet, names: Array<String>, session: SharedSessionContractImplementor, owner: Any?): Any? {
        val array = rs.getArray(names[0])
        return array?.array
    }

    @Throws(HibernateException::class, SQLException::class)
    override fun nullSafeSet(st: PreparedStatement, value: Any?, index: Int, session: SharedSessionContractImplementor) {
        if(value != null) {
            @Suppress("UNCHECKED_CAST")
            val array = session.connection().createArrayOf(sqlElementTypeName, value as Array<T?>?)
            st.setArray(index, array)
        } else {
            st.setNull(index, Types.ARRAY)
        }
    }

    override fun equals(x: Any?, y: Any?): Boolean {
        @Suppress("UNCHECKED_CAST")
        return (x as Array<T?>?) contentDeepEquals (y as Array<T?>?)
    }

    override fun hashCode(x: Any?): Int {
        @Suppress("UNCHECKED_CAST")
        return (x as Array<T?>?)?.contentDeepHashCode() ?: 0
    }

    override fun deepCopy(value: Any?): Any? {
        @Suppress("UNCHECKED_CAST")
        return (value as Array<T?>?)?.clone()
    }

    override fun isMutable(): Boolean {
        return true
    }

    override fun disassemble(value: Any?): Serializable? {
        @Suppress("UNCHECKED_CAST")
        return (value as Array<T?>?)?.clone() // тип mutable, поэтому clone
    }

    override fun assemble(cached: Serializable?, owner: Any?): Any? {
        @Suppress("UNCHECKED_CAST")
        return (cached as Array<T?>?)?.clone() // тип mutable, поэтому clone
    }

    override fun replace(original: Any?, target: Any?, owner: Any?): Any? {
        @Suppress("UNCHECKED_CAST")
        return (original as Array<T?>?)?.clone() // тип mutable, поэтому clone
    }
}
