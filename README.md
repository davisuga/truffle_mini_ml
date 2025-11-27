# MiniML - A Small OCaml Subset on Truffle

A small functional language implemented using the GraalVM Truffle framework and Scala 3. This project demonstrates manual Truffle node implementation without relying on the Java annotation processor (Truffle DSL).

## Features

### Language Features
- **Integer, String, and Boolean literals**: `42`, `"hello"`, `true`, `false`
- **Arithmetic operations**: `+`, `-`, `*`, `/`
- **Comparison operations**: `==`, `<`, `>`
- **Let bindings**: `let x = 10 in x + 1`
- **Function definitions**: `let add x y = x + y in add 3 4`
- **Lambda expressions**: `(x) => x + 1`
- **Higher-order functions**: `let apply f x = f x in apply double 10`
- **Conditionals**: `if x > 0 then x else 0`
- **Closures**: Functions capture their lexical environment

### Cross-Language Interoperability
MiniML supports cross-language interop via Truffle's InteropLibrary:

- **polyglotImport**: Import values from polyglot bindings
- **polyglotExport**: Export values to polyglot bindings  
- **javaType**: Lookup Java classes by name (requires host access)
- **invoke**: Call methods on foreign objects
- **getMember**: Read fields/properties from foreign objects
- **setMember**: Write fields/properties on foreign objects
- **newInstance**: Create instances of foreign classes

## Project Structure

```
src/main/scala/
├── Ast.scala           # AST definition
├── Parser.scala        # FastParse 3 parser
├── Truffle.scala       # Truffle node implementations
├── AstToTruffle.scala  # AST to Truffle node conversion
├── MiniMLLanguage.scala # Truffle language registration & interop
├── MiniMLRunner.scala  # Polyglot runner utilities
└── Main.scala          # Example runner

src/test/scala/
├── ParserTest.scala    # Parser tests
├── TruffleNodeTest.scala # Truffle node execution tests
└── InteropTest.scala   # Interop tests
```

## Building & Testing

This is a standard sbt project:

```bash
# Compile
sbt compile

# Run tests
sbt test

# Run example
sbt "runMain simplelang.runMiniML"
```

## Examples

### Simple Arithmetic
```ml
let x = 10 in let y = 20 in x + y
(* Result: 30 *)
```

### Function Definition
```ml
let add x y = x + y in add 3 5
(* Result: 8 *)
```

### Higher-Order Functions
```ml
let apply f x = f x in
let double = (x) => x * 2 in
apply double 21
(* Result: 42 *)
```

### Closures
```ml
let multiplier = 10 in
let mult = (x) => x * multiplier in
mult 5
(* Result: 50 *)
```

### Conditionals
```ml
if 5 > 3 then 100 else 200
(* Result: 100 *)
```

## Architecture

### Parser (FastParse 3)
The parser uses FastParse 3 combinators to transform source text into an AST. Key features:
- Whitespace handling via `ScalaWhitespace`
- Operator precedence via tiered parsers
- Keyword filtering to prevent identifier clashes

### Truffle Nodes (Manual Implementation)
Since the Truffle DSL annotation processor doesn't work well with Scala, all nodes are implemented manually:
- `SimpleLangNode` - Base trait for all AST nodes
- `IntLitNode`, `StrLitNode`, `BoolLitNode` - Literal values
- `IfNode` - Conditional expressions
- `LetNode` - Variable bindings
- `IdentNode` - Variable lookup
- `FnNode` - Function definitions
- `CallNode` - Function/operator application

### Environment
Lexical scoping is implemented via an `Environment` class that chains scopes. Each scope maintains a map of variable bindings and a reference to its parent scope.

### Interop Layer
The interop layer provides:
- `InteropFunction` - Wraps MiniML functions for external consumption
- `MiniMLScope` - Exposes the global scope to other languages
- Builtin functions for interacting with foreign objects

## Dependencies

- Scala 3.2.2
- GraalVM Truffle API 22.3.1
- FastParse 3.0.1
- MUnit 0.7.29 (testing)

## References

- [GraalVM Truffle Documentation](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)
- [FastParse 3 Documentation](https://com-lihaoyi.github.io/fastparse/)
- [Truffle Interop Protocol](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/InteropMigration/)

