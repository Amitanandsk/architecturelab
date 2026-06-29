package com.lessons.kotlinlessons.section6operators

import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import reactor.core.scheduler.Schedulers.boundedElastic
import java.time.Duration


class Lesson3DefaultIfEmpty

fun main(){
    Flux.range(1,10)
        .filter {
            it >10
        }.defaultIfEmpty(100).subscribe { print(it)}



}