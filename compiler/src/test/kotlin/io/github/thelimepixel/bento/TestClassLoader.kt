package io.github.thelimepixel.bento

class TestClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    fun load(className: String, bytecode: ByteArray): Class<*> {
        val clazz = super.defineClass(className, bytecode, 0, bytecode.size)
        resolveClass(clazz)
        return clazz
    }
}