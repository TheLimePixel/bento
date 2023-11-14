package io.github.thelimepixel.bento.utils

interface Formatter<in T> {
    fun format(value: T): String
}