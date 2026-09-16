package com.zsecure.com.utils

import java.io.ByteArrayInputStream
import java.io.InputStream

object GoogleAuthDecoder {
    fun decodeMigration(payload: ByteArray): List<Map<String, String>> {
        val accounts = mutableListOf<Map<String, String>>()
        val bis = ByteArrayInputStream(payload)
        
        while (bis.available() > 0) {
            val key = readVarint(bis)
            val tag = (key shr 3).toInt()
            val wireType = (key and 7).toInt()
            
            if (tag == 1 && wireType == 2) {
                val len = readVarint(bis)
                val subBytes = ByteArray(len.toInt())
                bis.read(subBytes)
                val acc = parseOtpParameters(subBytes)
                if (acc != null) {
                    accounts.add(acc)
                }
            } else {
                skipField(bis, wireType)
            }
        }
        return accounts
    }
    
    private fun parseOtpParameters(bytes: ByteArray): Map<String, String>? {
        val bis = ByteArrayInputStream(bytes)
        var secret: ByteArray? = null
        var name = ""
        var issuer = ""
        
        while (bis.available() > 0) {
            val key = readVarint(bis)
            val tag = (key shr 3).toInt()
            val wireType = (key and 7).toInt()
            
            when {
                tag == 1 && wireType == 2 -> {
                    val len = readVarint(bis)
                    secret = ByteArray(len.toInt())
                    bis.read(secret)
                }
                tag == 2 && wireType == 2 -> {
                    val len = readVarint(bis)
                    val strBytes = ByteArray(len.toInt())
                    bis.read(strBytes)
                    name = String(strBytes, Charsets.UTF_8)
                }
                tag == 3 && wireType == 2 -> {
                    val len = readVarint(bis)
                    val strBytes = ByteArray(len.toInt())
                    bis.read(strBytes)
                    issuer = String(strBytes, Charsets.UTF_8)
                }
                else -> skipField(bis, wireType)
            }
        }
        
        if (secret == null) return null
        
        return mapOf(
            "secret" to encodeBase32(secret),
            "name" to name,
            "issuer" to if (issuer.isEmpty()) "Service" else issuer
        )
    }
    
    private fun readVarint(input: InputStream): Long {
        var value = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b == -1) throw java.io.EOFException()
            value = value or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return value
    }
    
    private fun skipField(input: InputStream, wireType: Int) {
        when (wireType) {
            0 -> readVarint(input)
            1 -> input.skip(8)
            2 -> {
                val len = readVarint(input)
                input.skip(len)
            }
            5 -> input.skip(4)
        }
    }
    
    private fun encodeBase32(bytes: ByteArray): String {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val sb = StringBuilder()
        var i = 0
        var index = 0
        var digit: Int
        var currByte: Int
        var nextByte: Int
        
        while (i < bytes.size) {
            currByte = if (bytes[i] >= 0) bytes[i].toInt() else bytes[i].toInt() + 256
            if (index > 3) {
                nextByte = if (i + 1 < bytes.size) {
                    if (bytes[i + 1] >= 0) bytes[i + 1].toInt() else bytes[i + 1].toInt() + 256
                } else 0
                digit = currByte and (0xFF ushr index)
                index = (index + 5) % 8
                digit = digit shl index
                digit = digit or (nextByte ushr (8 - index))
                i++
            } else {
                digit = (currByte ushr (8 - (index + 5))) and 0x1F
                index = (index + 5) % 8
                if (index == 0) i++
            }
            sb.append(base32Chars[digit])
        }
        return sb.toString()
    }
}
