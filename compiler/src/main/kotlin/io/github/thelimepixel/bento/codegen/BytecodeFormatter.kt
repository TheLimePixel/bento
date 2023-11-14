package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.utils.Formatter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.io.StringWriter

class BytecodeFormatter : Formatter<ByteArray> {
    override fun format(value: ByteArray): String {
        val classReader = ClassReader(value)
        val stringWriter = StringWriter()
        val traceClassVisitor = TraceClassVisitor(PrintWriter(stringWriter))
        classReader.accept(traceClassVisitor, ClassReader.SKIP_DEBUG + ClassReader.SKIP_FRAMES)
        return stringWriter.toString()
    }
}