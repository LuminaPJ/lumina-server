package org.lumina.utils

import java.security.MessageDigest
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * 将用户提交的对象中所有字符串 `trim()` 化
 */
fun Any.normalized(): Any {
    val obj = this
    val kClass = obj::class

    if (obj is Map<*, *>) {
        return obj.mapValues { (_, value) ->
            when (value) {
                is String -> value.trim()
                is Map<*, *> -> value.normalized()
                is List<*> -> value.normalized()
                else -> value
            }
        } as Map<*, *>
    }

    if (obj is List<*>) {
        return obj.map { item ->
            when (item) {
                is String -> item.trim()
                is Map<*, *> -> item.normalized()
                is List<*> -> item.normalized()
                else -> item
            }
        }
    }

    kClass.memberProperties.forEach { property ->
        property.isAccessible = true
        if (property is kotlin.reflect.KMutableProperty<*>) {
            val value = property.getter.call(obj)
            if (value is String) {
                property.setter.call(obj, value.trim())
            } else if (value is Map<*, *> || value is List<*>) {
                property.setter.call(obj, value.normalized())
            }
        }
    }

    return obj
}

/**
 * 将字节数组转换成十六进制字符串（大写字母）
 */
fun ByteArray.toHashString(): String {
    return joinToString("") { "%02X".format(it) }
}

/**
 * 计算字符串的 SM3 杂凑值并返回杂凑值的 Hex 字符串
 */
fun String.sm3():  String {
    val messageDigest = MessageDigest.getInstance("SM3")
    return messageDigest.digest(this.toByteArray()).toHashString()
}

