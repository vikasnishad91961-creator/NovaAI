package com.nova.ai

import kotlin.math.pow

/** Chhota math parser: + - * / ^ ( ) */
class Calc(private val s: String) {
    private var pos = -1
    private var ch = 0

    private fun next() { ch = if (++pos < s.length) s[pos].code else -1 }
    private fun eat(c: Char): Boolean {
        while (ch == ' '.code) next()
        if (ch == c.code) { next(); return true }
        return false
    }

    fun eval(): Double {
        next()
        val x = expr()
        if (pos < s.length) throw RuntimeException("bad expression")
        return x
    }

    private fun expr(): Double {
        var x = term()
        while (true) {
            x = if (eat('+')) x + term() else if (eat('-')) x - term() else return x
        }
    }

    private fun term(): Double {
        var x = factor()
        while (true) {
            x = if (eat('*')) x * factor() else if (eat('/')) x / factor() else return x
        }
    }

    private fun factor(): Double {
        if (eat('+')) return factor()
        if (eat('-')) return -factor()
        var x: Double
        val start = pos
        if (eat('(')) {
            x = expr(); eat(')')
        } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
            while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) next()
            x = s.substring(start, pos).toDouble()
        } else throw RuntimeException("bad expression")
        if (eat('^')) x = x.pow(factor())
        return x
    }

    companion object {
        fun fmt(d: Double): String =
            if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString()
            else String.format("%.6f", d).trimEnd('0').trimEnd('.')
    }
}
