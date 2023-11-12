package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.parsing.ASTFormatter
import io.github.thelimepixel.bento.parsing.BentoParsing
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HelloWorld {
    val code = """
        fun main() {
            println("Hello, World!")
        }
    """.trimIndent()

    @Test
    fun testParse() {
        val node = BentoParsing().parseFIle(code)

        val expected = """
            File
            ├─FunctionDeclaration
            │ ├─FunKeyword
            │ ├─Whitespace( )
            │ ├─Identifier(main)
            │ ├─ParamList
            │ │ ├─LParen
            │ │ └─RParen
            │ ├─Whitespace( )
            │ └─ScopeExpr
            │   ├─LBrace
            │   ├─Newline
            │   ├─Whitespace(    )
            │   ├─CallExpr
            │   │ ├─Identifier(println)
            │   │ ├─LParen
            │   │ ├─StringLiteral("Hello, World!")
            │   │ └─RParen
            │   ├─Newline
            │   └─RBrace
            └─EOF
        """.trimIndent()

        assertEquals(expected, ASTFormatter().format(node))
    }
}