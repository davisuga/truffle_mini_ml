package simplelang

val src = """
// true
// false
let add x y = x + y in add 1 2
"""

import simplelang.Parser.parseInput
@main def hello =
  val lines = scala.io.Source.fromFile("ex.miniml").mkString
  print(parseInput(src))
