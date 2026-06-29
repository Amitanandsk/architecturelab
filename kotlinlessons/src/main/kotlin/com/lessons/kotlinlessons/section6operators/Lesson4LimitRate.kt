package com.lessons.kotlinlessons.section6operators

import reactor.core.publisher.Flux

class Lesson4LimitRate

fun main(){
    Flux.range(1,1000)
        .log()
        .limitRate(100,0)
        . subscribe()
}