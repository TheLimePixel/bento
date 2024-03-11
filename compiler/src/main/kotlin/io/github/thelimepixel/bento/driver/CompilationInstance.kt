package io.github.thelimepixel.bento.driver

import io.github.thelimepixel.bento.binding.Binding
import io.github.thelimepixel.bento.binding.BindingContext
import io.github.thelimepixel.bento.binding.PackageTree
import io.github.thelimepixel.bento.codegen.Codegen
import io.github.thelimepixel.bento.codegen.JVMBindingContext
import io.github.thelimepixel.bento.parsing.Parsing
import io.github.thelimepixel.bento.typing.Typechecking
import io.github.thelimepixel.bento.typing.TypingContext

class CompilationInstance(
    val packageTree: PackageTree,
    val parsing: Parsing,
    val binding: Binding,
    val topBindingContext: BindingContext,
    val topTypingContext: TypingContext,
    val topJVMBindingContext: JVMBindingContext,
    val typing: Typechecking,
    val bentoCodegen: Codegen,
)