package com.lessons.kotlinlessons.section6operators

import reactor.core.publisher.Flux
import reactor.util.concurrent.Queues
import java.time.Duration

class Lesson5Delay

fun main(){
    System.setProperty("reactor.bufferSize.x","50")

        Flux.range(1, 100)
            .log()
            .delayElements(Duration.ofMillis(10))
            .subscribe { println("Received:${Thread.currentThread().name}") }
        Thread.sleep(50000)
}

