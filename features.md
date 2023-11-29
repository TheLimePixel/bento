# Language Features

This is a sporadic list of features that the language should have. Following the requirements,
one can find that they are structured in a sort of DAG. That said, to avoid having every
interaction as its own features, features may reference other features that they do not depend
on, both in the description and examples, though that does not mean they cannot be implemented
without them.

As the compiler is currently WIP, this also serves as a sort of TODO list, where all unimplemented features are 
marked as such. 

## Single-line Comments

A single-line comment begins with two slashes and ends at a new-line character.

For example:

```kotlin
// This is a comment
```

## Multi-line Comments

A multi-line comment begins with a `/*` and ends with a `*/`. They can be nested within each
other. 

For example:

```kotlin
/*
This is a
comment /* within
a comment */
*/
```

## Basic String Literals

A value of type `String` can be represented by putting a string of characters between a pair of
double quote. For example: `"This is a string"`.

## Character Literals

TODO

A value of type `Char` can be represented by putting any UTF-16 character aside for newline or
`'` in between two single quotes. For example: `'a'`.

## Escaped Characters

TODO

Aside for raw characters, string and character literals may also contain escaped characters.
These are sequences of characters which are used to represent a single character. These are the
supported escaped sequences:

- `\0` - null character
- `\n` - newline
- `\r` - carriage return
- `\v` - vertical tab
- `\f` - form feed
- `\b` - backspace
- `\t` - tab
- `\\` - backslash
- `\"` - double quote
- `\'` - single quote
- `\uxxxx` - unicode character with hexadecimal code `xxxx`

## Raw String Literals

TODO

#### Requires

- [Basic String Literals](features.md#basic-string-literals)
- [Escaped Characters](features.md#escaped-characters)

To use backslashes and double quotes in strings without having to escape them, raw string literals
can be used. Raw string literals are just like normal string literals, except they are opened by
putting some number of hashes before a double quote, and they are ended by putting the same
number of hashes after a double quote. Escaped characters can still be used in them, but to do
so the backslash needs to be followed by the same number of hashes. For example: `#"¯\_(ツ)_/¯\#n"#`

## Basic Integer Literals

TODO

Sequences of digits with optional underscores in between represent values of the integer types (`I8`, `I16`, `I32`,
`I64`). If the type isn't specified, integer literals are by default of type `I32`. Only if the value is outside the range of `I32` is
it an `I64` by default. Being outside the range of values of `I64` results in an error.

## Basic Floating-Point Literals

TODO

#### Requires
- [Basic Integer Literals](features.md#basic-integer-literals)

Two integer literals joined by a `.` represent a value of a floating-point type (`F32` or `F64`). Unless the type is
specified, all floating-point literals are of type `F64`.

## Numeric Literal Type Suffices

TODO

#### Requires
- [Basic Integer Literals](features.md#basic-integer-literals)

Integers and string literals can have the type of the value appended in order to specify it.

## Integer Literals in Different Number Systems

TODO

## Requires
- [Basic Integer Literals](features.md#basic-integer-literals)

Integer literals have prefixes added to change the number system being used. Namely:
- `0b` for binary
- `0o` for octal
- `0x` for hexadecimal

## Scientific Notation For Floating-Point Literals

TODO

#### Requires
- [Basic Floating-Point Literals](features.md#basic-floating-point-literals)

Floating point literals can also be in scientific notation where the exponent is specified after an `e` or `E`. For 
example: `1e50`, `1.5e-2`, `1_264.635_e-10`.

## Scopes

A scope is a section of code where certain symbols are bound to certain values and definitions.
There are two types of scopes:

- declaration scopes
- expression scopes

The former type contain globally-accessible items which are created once and exist throughout the
runtime of a program, while the latter type contain local items which are created and deleted
every time the scope is run.

A scope is usually delimited by curly braces, with file-level scopes being exceptions.

## Type Annotations

Bento is a statically-typed programming language, meaning that every expression and symbol which gets bound or 
returns a value is assigned a type at compile-time. As such, many declarations require
or allow one to specify the type of value that is being dealt with. That is usually done by
putting said type after a colon.

## Identifiers

Identifiers are sequences of characters which start with a letter or underscore and can contain
alphanumeric characters, underscores and primes (apostrophes/single quotes). Identifiers are
references to items and values, which can be used for accessing or calling said items and values.

## Backticked Identifiers

TODO

#### Requires

- [Identifiers](features.md#identifiers)

Identifiers may also be represented by a sequence of characters between two backticks. This
allows the use of whitespace and various symbols inside the identifier. Note that newlines are
not allowed.

## Basic Functions

#### Requires

- [Identifiers](features.md#identifiers)
- [Scopes](features.md#scopes)

A basic function in Bento, which takes no parameters and returns `Unit` can be declared using the
`fun` keyword, its name, two parentheses and an expression scope which is executed when it is
called.

For example:

```kotlin
fun foo() {
    // Code here
}
```

## Basic Function Calls

You can call a function by putting a comma-separated list of values, one for each parameter, in
between two parentheses, following a reference to one. For example, given a function `foo(a: I32,
b: I32)` in scope, you can simply call it with arguments a: 0 and b: 0 by doing `foo(0, 0)`.

## Basic Patterns

TODO

#### Requires

- [Identifiers](features.md#identifiers)

When declaring a variable or parameter a pattern needs to be provided for how the value needs to
be bound. There are two basic declaration patterns:

- identifiers, where the value is bound to the identifier
- wildcards (represented by a sequence of underscores), where the value is simply discarded

## Function Parameters

#### Requires

- [Basic Functions](features.md#basic-functions)
- [Basic Patterns](features.md#basic-patterns)
- [Type Annotations](features.md#type-annotations)

A function can take one or more parameters which are listed between the parentheses. Every
parameter consists of a pattern and a type annotation corresponding to the value it gets bound to.

For example:

```kotlin
fun process(a: I32, b: I32, c: I32) {
    // Code here
}
```

## Function Return Values

#### Requires

- [Basic Functions](features.md#basic-functions)
- [Type Annotations](features.md#type-annotations)

By default, functions simply return the single value of the `Unit` type. For it to return a
different type of value, a type annotation needs to be inserted after the parameter list.
When that is done, the function returns the value of the last expression inside the function
body. 

For example:

```kotlin
fun five(): I32 {
    5
}
```

## Passing Arguments By Name

TODO

#### Requires

- [Basic Function Calls](features.md#basic-function-calls)

When calling a function you may pass the arguments out of order by specifying the name of the
parameter before every value. You can start a call without the argument names, but once you
start passing them out of order, you must stick to passing them by name.

For example:

```kotlin
fun foo(a: I32, b: I32, c: I32) {
    ...
}

// in a scope
foo(1, 2, 3)
foo(1, b: 2, 3)
// foo(1, c: 3, 2)  - error: c is passed out of order, so the third argument must be named
foo(c: 3, a: 1, b: 2)
// foo(1, 2, a: 3)  - error: two values passed in for a
```

## Postfix Scope Passing

TODO

#### Requires

- [Basic Function Calls](features.md#basic-function-calls)

When the last argument of a function is a scope expression, the scope may be taken out of the
argument list. In the case that the scope is the only argument, the regular argument list may be
left out. This not only works for just scopes by themselves, but also for anonymous functions and partial
application of scopes. Note that the scope must still be on the same line as the function call.

For example:

```kotlin
fun foo(a: I32, b: I32) {
    ...
}

fun bar(s: String) {
    ...
}

// in a scope
foo(0) {
    let ten = 10
    ten * ten
}

bar {
    let a = 10
    let b = 20
    a * b / 2
}
```

## Default Arguments

TODO

#### Requires

- [Basic Functions](features.md#basic-functions)

When defining a function, some parameters can be given default values, allowing them to be left
out in calls. The default values are expressions which are evaluated every time these arguments
are left out.

For example:

```kotlin
fun foo(a: I32 = 0, b: I32 = 1, c: I32 = 2) {
    ...
}

// in a scope
foo()               // all default values
foo(1)              // b and c get default values
foo(1, 2)           // c gets the default values
foo(1, 2, 3)        // all arguments are given
foo(b: 10)          // a and c get defalut values
```

## Let Statement

TODO

#### Requires

- [Basic Patterns](features.md#basic-patterns)
- [Scopes](features.md#scopes)
- [Type Annotations](features.md#type-annotations)

A `let` statement can be used to bind a value to a pattern for the rest of
the scope. It consists of the `let` keyword, a pattern, an optional type annotation, and an
expression following an equals sign. 

For example:

```kotlin
let a = 10
let squared: I32 = a * a
```

## Top Level Let

TODO

#### Requires

- [Let Statement](features.md#let-statement)

Just as in the local scope, `let` can be used in the global scope to make globally-accessible
bindings to values which are only created once; when the file is being initialized.

## Properties

TODO

#### Requires
- [Top Level Let](features.md#let-statement)

In declaration scopes, rather than having constants, there are properties. A property is an identifier which is 
associated to some value, though said value may not be constant or even stored in memory. Each property consists of 
a getter and a setter. The first one is called when using the identifier and the latter when following it with an 
equals sign and a value. Top-level `let` statements essentially store a value in memory and create a getter which is 
used to access it.

## Getters

TODO

#### Requires

- [Properties](features.md#properties)

Getters are functions which are called when just their identifier is referenced, essentially
functioning as constants which are re-computed every time. 

For example:

```kotlin
get ten(): I32 {
    println("Called ten")
    10
}

// In a scope
ten + ten           // this prints "Called ten" twice
```

## Setters

TODO

#### Requires

- [Properties](features.md#properties)

Setters are functions which are called when their bound identifier is used preceding an equals
sign. Their return type must be `Unit`. They may also be referenced by appending `_=` to their path.

For example:

```kotlin
set foo(s: String) {
    println("Tried to set foo to:")
    println(s)
}
```

## Packages

TODO

The units of Bento code which are built are called modules. Each module then consists of multiple
packages, which contain items (functions, properties, types, imports). Every Bento file
represents its own package. If there is a directory which has the same name as said file, then
all the packages within it are its subpackages. It should be noted that there is no functional
relationship between packages and subpackages, they are merely a means to organizing code.

## Visibility Modifiers

TODO

#### Requires

- [Packages](features.md#packages)

Every item has a visibility which determines where it can be used and accessed. In Bento there are
3 types of visibility:

- package-private (`pack`): the item can only be accessed from within the same package (this does
  not include subpackages)
- module-private (`mod`): the item can only be accessed from within the same module
- public (`pub`): the item can be accessed from every module

The visibility of an item or subitem (item, field, case, imported binding) can be set using the
aforementioned keywords before their declarations. By default, all top-level items are
module-private, aside for import statements which are package-private. Subitems inherit the
visibility of their superitems.

For example:

In `foo.bt`:

```kotlin
data Foo(             // module-private record 
    a: I32,             // module-private field, inheriting viibility
    pack b: I32         // package-private field
)
```

In `bar.bt`:

```kotlin
import { foo::Foo } // package-private binding 
                    // Note: if `pub` was used here, there would be an error because the visibility of Foo is lower

pack fun bar(foo: Foo) {// package-private function
                        // Note: If `pub` was used there would be an error because a type cannot
                        // be used in a declarationw= with a higher visibility
    foo.a               // Note: Were this foo.b it would be an error since it is not accessible 
                        // within this package
}
```

## Scope Operator

TODO

#### Requires

- [Scopes](features.md#scopes)

The scope operator `::` is used for accessing an item within a namespace. A namespace can be a
package or a type. For example, if there is a property called `bar` within the `foo` package,
we can access it using `foo::bar`.

## Basic Imports

TODO

##### Requires

- [Packages](features.md#packages)
- [Scope Operator](features.md#scope-operator)

At the beginning of a file, before any items are declared, you may put an import statement. The
import statement allows you to use bindings from other packages within the package. It consists of
the `import` keyword followed by braces which contain a comma-separated list of items which are
to be imported. The visibility of the imported items cannot exceed the visibility of the items
themselves.

## Custom Types

TODO

#### Requires
- [Identifiers](features.md#identifiers)

In Bento custom types can be declared using the `data` keyword followed by the identifier the 
type is bound to. Then, depending on what follows the identifier, there are 3 forms of types:
- Singleton types
- Product Types
- Sum Types

## Product Types

TODO

#### Requires

- [Custom Types](features.md#custom-types)
- [Properties](features.md#properties)

Product types are custom types whose values contain values of other types. Thus, to define one, the type's name 
must be followed by a comma-separated list of fields in parentheses. A field is a property on values of the type, 
the mutability of which depends on the mutability of the value. Fields are declared with an identifier 
followed by type annotation.

To create a new object of a product type, you call its constructor. The constructor is just a 
function with the type's name which assigns a value to each field. Thus, the parameters and 
their order are identical to the fields and their order. Note however that the visibility of the 
constructor is the maximum between the visibility of the type and the visibilities of all their 
fields.

For example:

```kotlin
pub data User(      // module-private constructor due to id
    name: String,
    mod id: I32,
    age: I32,
)

let testUser = User("John Doe", 0, 100)
```

## Singleton Types

TODO

#### Requires
- [Custom Types](features.md#custom-types)

Singleton types are types with a single global instance. They are created by simply not putting 
anything after the type's name. An example of such a type is the `Unit` type.

## Sum Types

TODO

#### Requires
- [Custom Types](features.md#custom-types)

Sum types define a set of subtypes that their values can be of. That is done by putting all the 
subtypes in curly braces after the type's name.

To find the exact subtype of a value of a product type, the `if` operator needs to be used. It 
can be used on an expression either with a dot, or without a dot, as an infix operator. The 
keyword is followed by braces where each line contains a colon-separated pair of a type-check 
and a value. A type-check consists of the `is` keyword followed by a subtype and then optionally 
a pattern. In an `if`-match every subtype must be covered. If after having specified some of 
them, the rest of them should yield the same result, an `else` can be used in the place of a 
type-check.

Unless otherwise specified, values of subtypes will be inferred to be of the base type and not 
of their subtypes.

For example:

```kotlin
data Colour {
  Red
  Green
  Blue
  Custom(red: U8, green: U8, blue: U8)
}

fun redComponent(color: Colour): U8 {
  color if {
    is Red: 255
    is Custom c: c.red
    else: 0
  }
}
```

## Type Mutability

TODO

#### Requires
- [Product Types](features.md#product-types)

In Bento, variables, parameters and fields merely store references to values, meaning a change to
a field using one of them changes the field across all of them. However, to prevent bugs and
keep code predictable it is generally a good idea to limit mutation as much as possible.

To achieve that, in Bento there is a difference between a mutable and immutable reference. The type
names by themselves correspond to immutable references. For mutable references, the `mut` keyword
needs to be put before the type.

Mutable references are considered subtypes of immutable references of the same type, meaning you can 
pass a mutable reference where an immutable one is required, but not the other way around. A 
consequence of this is that when a type parameter is still being inferred based on a given set 
of values, regardless of how many of them are mutable, if one is immutable then it is inferred 
to be immutable.

## Impl Blocks

TODO

#### Requires

- [Scope Operator](features.md#scope-operator)

Types themselves can be used as namespaces. Once you've created a type you are able to add
members to its namespace using an `impl` block. 

For example:

```kotlin
mod data User(
  pack id: I32,
  name: String
)

mod data UserCounter(
  pack count: I32
)

impl User { 
  mod let testUser = User(0, "John Doe")
  
  mod fun new(counter: mut UserCounter, name: String): mut User {
    counter.count += 1
    User(count, name)
  }
}
```

## This Parameters

TODO

#### Requires

- [Impl Blocks](features.md#impl-blocks)

Within an `impl` block functions, getters and setters can have a `this` parameter, which is
implicitly of the given type. For the mutable variant you may specify the type or put `mut`
before this (so, it is `mut this`).

## This Type

TODO

#### Requires

- [Impl Blocks](features.md#impl-blocks)

Within an `impl` block, the type that it corresponds to can be referenced by just using the
`This` keyword.

## Member Access

TODO

#### Requires

- [This Parameters](features.md#this-parameters)

Type members which have a `this` parameter can be called directly on a value using the dot
operator, such that the value that it is being called on is passed as the `this` argument.

## Basic Type Parameters

TODO

Most item types (everything but let statements) can take type parameters, allowing the same item
to be used for many different types of values. In all items types these type parameters are defined
between angled brackets after the item's name, aside for implementation blocks, where they are
defined directly after the `impl` keyword. 

For example:

```kotlin
data Stack<T>(pack backing: mut List<T>)

impl<T> Stack<T> {
  fun isEmpty(this): Bool {
    this.backing.isEmpty()
  }
  
  fun top(this): T {
    this.backing.last()
  }
  
  fun pop(this): T {
    let result = this.top()
    this.backing.removeLast()
    result
  }
}
```

## Traits

TODO

#### Requires

- [Basic Type Parameters](features.md#basic-type-parameters)

A trait defines a set of items which a type implements. All the items within a trait must share
the same visibility as the trait itself. The declared items within a trait are meant to be
implemented per type, so their bodies are to be left out.

A trait may be implemented for a type using the `impl Trait for Type` syntax. Implementations
are always public and as a result at least the trait, the type the trait is being implemented on or
one of the type arguments passed to it must be declared in the module the implementation is in.
There may not be more than a single implementation of a trait for a type. That said, traits may have
default implementations, which can be overridden.

If a trait is imported, its members may be called on values without needing to be imported
separately. However, in the case there are multiple traits which a type implements with the same
function names, the functions cannot be called with the dot operator. In the case that there is
a member defined on the type with the same name, then it takes priority and is the one
being called.

Where traits shine however is that they can be used as constraints on type parameters, allowing
you to use the same item for every set of types which implement the given traits. When doing so,
traits members may be used on values of the type parameters that have them as constraints. To
give a type multiple trait constraints, you can join them together using the `&` operator.

For example:

```kotlin
trait Printer<Of> {
  fun print(this, o: Of)
}

trait Printable<Type> {
  fun print<P: Printer<Type>>(this, printer: P)
}

impl Foo<String> for I32 {
  fun print<P: Printer<String>>(this, printer: P) {
    p.print(this.toString())
  }
}
```

## Associated Type Parameters

TODO

#### Requires

- [Traits](features.md#traits)

Oftentimes it can be useful to have one or more trait type arguments inferred from just some of 
them. Such type parameters, which are fixed by the rest, are called associated and can be 
marked as such using the `assoc` keyword. 

A great example from the standard library is `Iterable`:

```scala
trait Iterable<assoc Element, assoc Iter: Iterator<Element>> {
  get iterator(this): mut Iter
}

fun sumZeroes(list: List<I32>): I32 {
    list.iterator.filter(I32::isZero).count()
}
```

## Infix Functions

TODO

#### Requires

- [This Parameters](features.md#this-parameters)

Instead of using a dot with an argument in parentheses, functions that take `this` and a single 
other argument can also be called as infix operators. To do so they first need to be defined as 
operators.

Every operator has a precedence group. Precedence groups define how operators interact with each 
other. A precedence group can have 3 fields specified:
- `higherThan: Group` - operators of this group take priority over operators of `Group` and 
  those of lower groups
- `lowerThan: Group` - operators of `Group` and groups above it take priority over operators of 
  this group
- `associativity` - defines whether a sequence of such operators should be grouped left-to-right 
  (`left`), right-to-left (`right`) or if such an expression should be an error (`none`)

When declaring an operator the precedence group can be specified after a colon. If it is omitted,
it gets the `DefaultGroup`.

For example:

```kotlin
group MinmaxGroup {
  higherThan: ComparisonGroup
  lowerThan: AdditiveGroup
  associativity: left
}

binop max: MinmaxGroup
binop min: MinmaxGroup

pub data Rectange(x1: I32, y1: I32, x2: I32, y2: I32)

impl Rectange {
  fun intersects(this, other: Reactangle) {
     this.x1 max other.x1 < this.x2 min other.x2 &&
        this.y1 max other.y1 < this.y2 min other.y2
  }
}
```

## Operators

TODO

#### Requires

- [Backticked Identifiers](features.md#backticked-identifiers)
- [Infix Functions](features.md#infix-functions)

Aside for infix function, sequences of special characters may be used as operators. A given 
symbolic operator `op` may be used as a prefix operator, in which case it will be mapped to `` 
`_op` ``, a postfix operator, in which case it maps to `` `op_` `` or as a binary operator (in 
which case it needs to be defined as one) which maps to `` `_op_` ``. It should be noted that 
while some default operators can be shadowed, the special operators `$`, `=`, `&`, `|`, `&&`, `<`, `>`, 
`||` and `:` cannot be used.

## If Expressions

TODO

#### Requires
- [Sum Types](features.md#sum-types)

We already looked at `if`-matching, but `if` has three more expressions it can be used in, which all revolve around the
`Bool` type. The `Bool` type is a product type with two unit subtypes `True` and `False`. All of these forms of 
expressions make use of the value of an expression of type `Bool` to determine which piece of code should be 
executed. These 3 constructs are:
- `if`-statements: where an `if` is followed by a condition in parentheses and an expression. The expression is only 
  evaluated if the value of the condition is `True`. Though it is called a statement, this may be used as an 
  expression of type `Unit`
- `if`-expressions: just like `if`-statements except there is an additional `else` followed by an expression which 
  gets evaluated if the condition is `False`. Its return type is the type of both values
- `if`-sequences: similar to an `if`-match, but instead of having a sequence of type-check - expression pairs, it 
  has a list of condition-expression pairs, where all the conditions will be evaluated until one evaluates to `True`,
  in which case its expression will be evaluated and its value will be returned. Thus, the type of such an 
  expression is the common type of all expressions.

For example:

```kotlin
fun foo(a: I32) {
    if { 
        a < 0: println(if (x == 1) "1!" else ":(")
        a > 0: if (a == 3) println("3!")
        else: println("0!")
    }    
}
```

## Escaped Expressions

TODO

#### Requires
- [Basic String Literals](features.md#basic-string-literals)
- [Escaped Characters](features.md#escaped-characters)
- [Traits](features.md#traits)

Aside for escaped characters, you are also able to insert stringified values into strings by putting them in 
curly braces after a backslash. For this to work, the type of the expressions must implement the `Display` trait.

For example:

```kotlin
fun printSum(a: I32, b: I32) {
    println("\{a} + \{b} = \{a + b}")
}
```

## Destructuring Pattern

TODO

#### Requires
- [Basic Patterns](features.md#basic-patterns)
- [Product Types](features.md#product-types)

A pattern that can be used for product types whose constructor is visible within the scope is
the destructuring pattern. In this pattern a list of patterns are put in parentheses
corresponding to each field in the constructor.

For example:

```kotlin
data Foo(a: String, b: String)

data Bar(a: Bar, b: Bar)

fun printFirst(((x, _), _): Bar) {
  println(x)
}
```

## Functions As Values

TODO

#### Requires
- [Basic Functions](features.md#basic-functions)

Functions themselves can be passed around. To achieve this, there are function types which are represented in the 
format `(Type1, Type2, ... TypeN) -> Result`.

Functions, getters and setters can be passed in by just using their path. Functions can also have the `this` parameter 
captured by using the access operator and not using parentheses.

Values of function types can be called as regular functions.

For example:

```kotlin
fun of2(fn: (I32) -> I32): I32 {
    fn(2)
}

fun add3(value: I32): I32 {
    value + 3
}

fun main() {
    println(of2(add3))
}
```

## Anonymous Functions

TODO

#### Requires

- [Functions As Values](features.md#functions-as-values)

Anonymous functions may be created by putting the parameters between pipes, optionally a type preceded by an arrow 
(`->`) and then the expression it evaluates. Depending on if the type can be inferred or not, the parameters may 
have their types left out. Note that this functions as a sort of prefix operator, meaning binary operators do not 
get captured. If the expression is just a scope, the whole function may be taken out of the list of arguments. 

For example:

```kotlin
list.iter().filter(|x| (x < 0)).map|x| { x * 2 }.fold(0)|acc, curr|{ acc * curr rem 40 }
```

## As Operator

TODO

The `as` operator can be used for specifying the type of an expression. It can be used for as a binary operator 
between an expression and type, as well as as a postfix operator as `.as<Type>`. 

For example:

```kotlin
let a = default() as I32
let b = a + default().as<I64>
```

## Type Aliases

TODO

#### Requires

- [Basic Type Parameters](features.md#basic-type-parameters)

To avoid having to rewrite long type names with many type arguments, type aliases can be used. Type aliases are 
declared using the `type` keyword followed by its name, an optional list of type arguments, an equals sign and the 
type that's being aliased. 

For example:

```kotlin
type Tensor4D<T> = List<List<List<List<T>>>>
```

## Specifying Type Arguments

TODO

#### Requires

- [Basic Type Parameters](features.md#basic-type-parameters)

The type arguments of an item can be explicitly passed by putting them in angled brackets following the scope 
operator. 

For example:

```kotlin
fun foo<T>(a: T, b: T) {
    ...
}

// in a scope
foo::<I32>(default(), default())
```

## Default Trait Member Implementations

TODO

#### Requires

- [Traits](features.md#traits)

Trait members may take default implementations which will be used if not specified. 

For example:

```kotlin
trait Foo {
    fun foo(this) {
        println("foo!")
    }
}

impl Foo for I32 {}
```

## Trait Inheritance

TODO

#### Requires

- [Traits](features.md#traits)

A trait can have a bound set on `This`, requiring `This` to implement another trait (or multiple traits joined with 
`&`). That can be done by putting said trait after a colon before the start of that trait's body. 

For example:

```kotlin
trait Foo {
  fun foo(this)
}

trait Bar: Foo { 
  fun bar(this) {
      this.foo()
  }
}

impl Foo for I32 {
  fun foo(this) {
    println("\{this}")
  }
}

impl Bar for I32 { }

// impl Bar for Bool { } - error: Bar does not implement Foo
```

## Implementation Disambiguation

TODO

#### Requires
- [Traits](features.md#traits)
- [As Operator](features.md#as-operator)

When there are multiple traits present with members that share a name, it is ambiguous which one is being called. 
For values this can be fixed by just using the `as` operator. To call the function directly on the type, however, it 
needs to be put in angled brackets and have the trait specified using the `as` operator. 

For example:

```kotlin
trait Foo {
    fun print()
}

trait Bar {
    fun print()
}

impl Foo for I32 { ... }
impl Bar for I32 { ... }

fun doPrinting() {
    // I32::print() - error: ambiguous call
    <I32 as Foo>::print()
    <I32 as Bar>::print()
}
```

## Partial Application

TODO

#### Requires

- [Functions As Values](features.md#functions-as-values)

The prefix partial application operator `$` turns the expression it's applied to into a function with implicit 
parameters of the form `$n` where `n` is an integer. To use it, the type of the expression must be known beforehand.
This can be nested, however for each level of nesting an additional `$` must be added to avoid confusion. 
Additionally, just like regular scopes, if an expression this is being used on is a scope, it may be put after the 
function.

For example:

```kotlin
fun toFunction(list: List<I32>): (I32) -> List<I32> {
    $list.iter().map$${ $0 + $$0 }.collect()
}
```

## Partial Types

TODO

#### Requires

- [Basic Type Parameters](features.md#basic-type-parameters)

Using a wildcard instead of a type makes the compiler infer the that specific type. For example:
```kotlin
fun foo(range: Range<I32>) {
    let list: List<_> = range.iter().map|x| { x * 2 }.collect()
}
```

## Generalized Sum Types

TODO

#### Requires

- [Sum Types](features.md#sum-types)

By default, subtypes of product types have no power over the constraints of the type arguments or over the values of 
the type arguments that they do not use generically in their definition. To allow this, a colon can be used after 
the type name (aside for product subtypes where it goes after the list of fields), in which case the subtype will be 
able to define its own type arguments after its name, as well as the resulting supertype after the colon. 

For example:

```kotlin
data Expr<T> {
    Value(val: I32) : Expr<I32>
    Plus(a: Expr<I32>, b: Expr<I32>) : Expr<I32>
    If(cond: Expr<Bool>, thenExpr: Expr<T>, elseExpr: Expr<T>)
    And(a: Expr<Bool>, b: Expr<Bool>) : Expr<Bool>
}
```

## Sealed Traits

TODO

#### Requires
- [Traits](features.md#traits)
- [Visibility Modifiers](features.md#visibility-modifiers)

By default, a trait may be implemented anywhere it is visible. The scope for its implementations can be set to be one 
lower than its visibility using the `sealed` keyword. This also allows the trait to have members of said visibility. 

For example:

```scala
sealed pub trait Foo {      // Foo can be access anywhere, but only implemented in this module
    fun foo(this) {         // foo can be accessed anywhere
        
    }
  
    mod fun bar(this) {     // bar can only be accessed in this module
        
    }
}
```