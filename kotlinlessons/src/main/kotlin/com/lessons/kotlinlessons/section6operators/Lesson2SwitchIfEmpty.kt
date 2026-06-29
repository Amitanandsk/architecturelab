package com.lessons.kotlinlessons.section6operators

import reactor.core.publisher.Flux

class Lesson2SwitchIfEmpty


    fun main() {
        Flux.range(1, 10).map {
            it * 3
        }.filter { it % 2 == 7 }
            .map {
                println(it)
            }.switchIfEmpty { println(3) }.subscribe()
    }
