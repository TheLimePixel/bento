package io.github.thelimepixel.bento.codegen

sealed interface JVMType {
    data class Class(private val `class`: JVMClass) : JVMType {
        override fun toString(): String = "L$`class`;"
    }
    data class Array(private val nested: JVMType) : JVMType {
        override fun toString(): String = "[$nested"
    }
    data object Void : JVMType {
        override fun toString(): String = "V"
    }
    data object Bool : JVMType {
        override fun toString(): String = "Z"
    }
    data object Char : JVMType {
        override fun toString(): String = "C"
    }
    data object Byte : JVMType {
        override fun toString(): String = "B"
    }
    data object Short : JVMType {
        override fun toString(): String = "S"
    }
    data object Int : JVMType {
        override fun toString(): String = "I"
    }
    data object Long : JVMType {
        override fun toString(): String = "J"
    }
    data object Float : JVMType {
        override fun toString(): String = "F"
    }
    data object Double : JVMType {
        override fun toString(): String = "D"
    }
}

class JVMDescriptor(val parameters: List<JVMType>, val returnType: JVMType) {
    override fun toString(): String = parameters.joinToString("", "(", ")") + returnType
}

val emptyJVMDescriptor = JVMDescriptor(emptyList(), JVMType.Void)