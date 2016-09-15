// !CHECK_TYPE
// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE
data class A(val x: Int, val y: String)

fun foo(block: (A) -> Unit) { }

fun bar(a: Double) {
    val b = 1.toShort()
    foo { (<!NAME_SHADOWING!>a<!>, <!NAME_SHADOWING!>b<!>) ->
        a checkType { _<Int>() }
        b checkType { _<String>() }
    }

    foo { (c, d) ->
        c checkType { _<Int>() }
        d checkType { _<String>() }

        foo { (<!NAME_SHADOWING!>a<!>, <!NAME_SHADOWING!>c<!>) ->
            a checkType { _<Int>() }
            c checkType { _<String>() }
            d checkType { _<String>() }
        }
    }
}
