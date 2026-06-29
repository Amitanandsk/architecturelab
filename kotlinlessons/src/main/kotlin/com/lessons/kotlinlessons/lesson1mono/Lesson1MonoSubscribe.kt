package com.lessons.kotlinlessons.lesson1mono

import reactor.core.publisher.Mono


class Ls1MonoSubscribe()

fun main(){

   val result =  Mono.just("ball")
        .map {
          it.length
        }
        .map {
          l -> l/1
        }


    result.subscribe ({it -> println(it)},{err -> println(err)})
}