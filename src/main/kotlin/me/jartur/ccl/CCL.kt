package me.jartur.ccl

class Parser(val s: String) {
    var position: Int = 0
    var open: Int = 0

    fun parse(): AST {
        skipWS()
        if(current() == '(') {
            open++
            position++
            return parseList()
        }
        return parseAtom()
    }

    fun parseAtom(): AST {
        skipWS()
        val b = StringBuilder()
        while (!isWhitespace(current()) && current() != ')') {
            b.append(current())
            position++
        }
        val str = b.toString()
        val intValue = str.toIntOrNull()
        if(intValue != null) {
            return ASTInt(intValue)
        }
        if(str.contains(":")) {
            val split = str.split(":")
            return ASTIdTyped(split[0], split[1])
        }
        return ASTId(str)
    }

    fun parseList(): ASTList {
        skipWS()
        val children = ArrayList<AST>()
        while (current() != ')') {
            children.add(parse())
            skipWS()
        }
        open--
        if(open < 0) {
            throw IllegalStateException("Mismatched parens")
        }
        position++
        return ASTList(children)
    }

    private fun skipWS() {
        while (isWhitespace(current())) {
            position++
        }
    }

    private fun current() = s[position]

    private fun isWhitespace(char: Char) =
            CharCategory.SPACE_SEPARATOR.contains(char)
            || CharCategory.LINE_SEPARATOR.contains(char)
            || char == '\n'
            || char == '\r'
}

sealed class AST
data class ASTInt(val value: Int) : AST()
data class ASTId(val id: String) : AST()
data class ASTIdTyped(val id: String, val typ: String) : AST()
data class ASTList(val children: List<AST>) : AST()

sealed class SupportedType {
    companion object {
        private val mapRE = Regex("map<(\\w+)>")
        private val arrayRE = Regex("array<(\\w+)(,\\d+)?>")

        fun fromString(str: String): SupportedType {
            if(str == "int") {
                return IntType()
            }
            val mapMatch = mapRE.matchEntire(str)
            if(mapMatch != null) {
                val typeParam = fromString(mapMatch.groupValues[1])
                return MapType(typeParam)
            }
            val arrayMatch = arrayRE.matchEntire(str)
            if(arrayMatch != null) {
                val typeParam = fromString(arrayMatch.groupValues[1])
                val dimension = arrayMatch.groupValues[2].substring(1).toInt()
                return ArrayType(typeParam, dimension)
            }
            throw IllegalStateException("Unsupported type $str")
        }
    }
}
class IntType : SupportedType()
data class MapType(val param: SupportedType) : SupportedType()
data class ArrayType(val params: SupportedType, val dim: Int) : SupportedType()

sealed class IAst {
    data class Class(val name: String, val methods: List<Fun>) : IAst()
    data class Var(val name: String, val typ: SupportedType) : IAst()
    data class Fun(val name: String, val returnType: SupportedType, val params: List<Var>, val body: Expression) : IAst()
    sealed class Block : IAst() {
        class EmptyBlock : Block()
    }
    sealed class Statement : IAst() {
        data class StatementIf(val then: Block, val els: Block) : Statement()
    }
    sealed class ArithOp {
        class Plus : ArithOp()
        class Minus : ArithOp()
    }
    sealed class Expression : IAst() {
        data class ArithExpression(val op: ArithOp, val args: List<Expression>) : Expression()
        data class VarExpression(val id: String) : Expression()
        data class LiteralInt(val value: Int) : Expression()
    }
}

class CCL {
    fun run(): AST {
        //language=TEXT
        return Parser("(class x \n  (defn f:int (x:int y:int g:map<int> l:array<int,2>) (+ x y))\n  (defn g:map<int> (x:int) (+ x x 1)))").parse()
    }
}

class AstTransform(val ast: AST) {
    fun transform(): IAst {
        when (ast) {
            is ASTList -> {
                val head = ast.children[0]
                when(head) {
                    is ASTId -> when {
                        head.id == "defn" -> {
                            val fname = ast.children[1] as ASTIdTyped
                            val fargs = ast.children[2] as ASTList
                            val fbody = ast.children[3] as ASTList
                            val args = fargs.children.map {
                                val decl = it as ASTIdTyped
                                IAst.Var(decl.id, SupportedType.fromString(decl.typ))
                            }
                            val fn = IAst.Fun(fname.id, SupportedType.fromString(fname.typ), args, AstTransform(fbody).transform() as IAst.Expression)
                            return fn
                        }
                        head.id == "class" -> {
                            val className = ast.children[1] as ASTId
                            val methods = ast.children.subList(2, ast.children.size)
                            val methodsTransformed = methods.map { AstTransform(it).transform() as IAst.Fun }
                            return IAst.Class(className.id, methodsTransformed)
                        }
                        head.id == "+" -> {
                            val args = ast.children.subList(1, ast.children.size)
                            val argsTransformed = args.map {
                                AstTransform(it).transform() as IAst.Expression
                            }
                            return IAst.Expression.ArithExpression(IAst.ArithOp.Plus(), argsTransformed)
                        }
                    }
                }
            }
            is ASTId -> {
                return IAst.Expression.VarExpression(ast.id)
            }
            is ASTInt -> {
                return IAst.Expression.LiteralInt(ast.value)
            }
        }
        return IAst.Block.EmptyBlock()
    }
}

class JavaEmitter(val ast: IAst) {
    private val b = StringBuilder()

    fun emit(): String {
        build()
        return b.toString()
    }

    private fun build() {
        when (ast) {
            is IAst.Class -> {
                b.append("public class ")
                        .append(ast.name)
                        .append(" {\n")
                ast.methods.forEach{
                    b.append(JavaEmitter(it).emit())
                }
                b.append("}\n")
            }
            is IAst.Fun -> {
                b.append("public static ")
                        .append(supportedTypeToString(ast.returnType))
                        .append(" ")
                        .append(ast.name)
                        .append("(")
                        .append(")")
                        .append("{\n")
                        .append("}\n")
            }
        }
    }

    protected fun supportedTypeToString(typ: SupportedType): String {
        when(typ) {
            is IntType -> {
                return "Integer"
            }
            is MapType -> {
                return "Map<String,${supportedTypeToString(typ.param)}>"
            }
            else -> {
                return "nada"
            }
        }
    }
}

fun main(args: Array<String>) {
    val ast = CCL().run()
    println(ast)
    val transform = AstTransform(ast).transform()
    println(transform)
    println(JavaEmitter(transform).emit())
}
