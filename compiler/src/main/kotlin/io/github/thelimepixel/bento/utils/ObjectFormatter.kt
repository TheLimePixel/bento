package io.github.thelimepixel.bento.utils

class ObjectFormatter : Formatter<Any?> {
    override fun format(value: Any?): String = StringBuilder(" ")
        .also { format(value.toString().iterator(), it, StringBuilder()) }
        .toString().trimIndent()

    private fun format(iterator: CharIterator, builder: StringBuilder, prefix: StringBuilder) {
        var inString = false
        while (iterator.hasNext()) {
            val curr = iterator.next()

            if (inString) {
                if (curr == '"') inString = false
                builder.append(curr)
            } else when (curr) {
                ',' -> builder.append('\n').append(prefix)
                '=' -> builder.append(": ")
                '(' -> {
                    builder.append(" {\n ")
                    prefix.append("  ")
                    builder.append(prefix)
                }

                '[', '{' -> {
                    builder.append("$curr\n ")
                    prefix.append("  ")
                    builder.append(prefix)
                }

                ')', '}', ']' -> {
                    prefix.setLength(prefix.length - 2)
                    builder.append('\n').append(prefix).append(' ')
                    builder.append(if (curr == ')') '}' else curr)
                }

                '"' -> {
                    inString = true
                    builder.append('"')
                }

                else -> builder.append(curr)
            }
        }
    }
}