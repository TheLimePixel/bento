package io.github.thelimepixel.bento.utils

class ObjectFormatter {
    private tailrec fun format(iterator: CharIterator, builder: StringBuilder, prefix: StringBuilder) {
        if (!iterator.hasNext()) return

        when (val curr = iterator.next()) {
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

            else -> builder.append(curr)
        }

        format(iterator, builder, prefix)
    }

    fun format(obj: Any): String = StringBuilder(" ")
        .also { format(obj.toString().iterator(), it, StringBuilder()) }
        .toString().trimIndent()
}