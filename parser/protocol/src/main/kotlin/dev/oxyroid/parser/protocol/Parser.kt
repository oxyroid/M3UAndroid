package dev.oxyroid.parser.protocol

interface Parser<in Input, out Output> {
    fun parse(input: Input): Sequence<Output>
}
