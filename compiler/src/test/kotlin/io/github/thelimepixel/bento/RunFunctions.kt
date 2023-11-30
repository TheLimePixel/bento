package io.github.thelimepixel.bento

val printBuffer = StringBuilder()
fun fakePrintln(str: String) {
    printBuffer.appendLine(str)
}

fun debugPrintln(str: String) {
    fakePrintln(str)
    println(str)
}