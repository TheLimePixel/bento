package io.github.thelimepixel.bento

val printBuffer = StringBuilder()

@Suppress("unused")
fun fakePrintln(str: String) {
    printBuffer.appendLine(str)
}