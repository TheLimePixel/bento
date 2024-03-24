package io.github.thelimepixel.bento.errors

interface ErrorKind {
    val ignore: Boolean
        get() = false
}