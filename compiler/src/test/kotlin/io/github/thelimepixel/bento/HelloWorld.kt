package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.binding.BentoBinding
import io.github.thelimepixel.bento.binding.collectFunctions
import io.github.thelimepixel.bento.parsing.ASTFormatter
import io.github.thelimepixel.bento.parsing.BentoParsing
import io.github.thelimepixel.bento.utils.ObjectFormatter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HelloWorld {
    private val code = """
        fun main() {
            println("Hello, World!")
        }
    """.trimIndent()

    @Test
    fun testParse() {
        val node = BentoParsing().parseFIle(code)
        val actual = ASTFormatter().format(node)

        val expected = """
            File
            ├─FunDef
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
            │   │ └─ArgList
            │   │   ├─LParen
            │   │   ├─StringLiteral("Hello, World!")
            │   │   └─RParen
            │   ├─Newline
            │   └─RBrace
            └─EOF
        """.trimIndent()

        assertEquals(expected, actual)
    }

    @Test
    fun testBind() {
        val node = BentoParsing().parseFIle(code)
        val items = node.collectFunctions()
        val hirMap = BentoBinding().bind(items)
        val formatter = ObjectFormatter()
        val actual = items.joinToString(separator = "\n") {
            val hir = hirMap[it]!!
            formatter.format(hir)
        }

        val expected = """
            FunctionDef {
              ref: ASTRef {
                span: 0..43
                type: FunDef
              }
              scope: ScopeExpr {
                ref: ASTRef {
                  span: 22..54
                  type: ScopeExpr
                }
                statements: [
                  CallExpr {
                    ref: ASTRef {
                      span: 12..36
                      type: CallExpr
                    }
                    on: IdentExpr {
                      ref: ASTRef {
                        span: 0..7
                        type: Identifier
                      }
                      binding: println
                    }
                    args: [
                      StringExpr {
                        ref: ASTRef {
                          span: 2..17
                          type: StringLiteral
                        }
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        assertEquals(expected, actual)
    }
}