package io.github.thelimepixel.bento.parsing

/***
 * This class is used to represent the result of a parsing subroutine. The result can be one of:
 * [Success] - the expected item was successfully parsed
 * [Pass] - the item was not parsed, but this is not an error
 * [Failure] - the item was not parsed and what was found instead was wrapped in an error
 * [Recover] - the item was not parsed as instead the start of an item that cannot be ignored was found
 */
enum class ParseResult(val passing: Boolean) {
    Success(true),
    Pass( true),
    Failure(false),
    Recover(false);

    inline fun ifNotPassing(fn: (ParseResult) -> Unit) {
        if (!passing) fn(this)
    }

    inline fun then(fn: () -> ParseResult): ParseResult =
        if (passing) fn()
        else this

    inline fun ifIs(type: ParseResult, then: (ParseResult) -> Unit) =
        if (this == type) then(this) else Unit

    inline fun ifNot(type: ParseResult, then: (ParseResult) -> Unit) =
        if (this != type) then(this) else Unit
}