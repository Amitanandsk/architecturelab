package com.lessons.kotlinlessons.section6operators

import com.github.javafaker.Faker
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.lang.RuntimeException
import java.time.Duration

class Lesson6OnError



fun main1(){

    Flux.range(1,10)
        .log()
        .map { 10/(5-it) }
        //.doOnError { println("error message"+it.message) }
        //.onErrorReturn(-2)
       // .onErrorResume { e ->  fallBack() }
        .onErrorContinue { t, u ->   }
        .subscribe{ println("value received:$it")}
}
// doOnError work only with onErrorReturn and onErrorResume
fun fallBack(): Mono<Int> {
    println("fallback:"+Thread.currentThread().name)
     Thread.sleep(10000)
    return Faker.instance().random().nextInt(100,200).toMono()
}

fun repo(): Mono<Int> {
    println("fallback:" + Thread.currentThread().name)
    Thread.sleep(1000)
    val aa = Faker.instance().random().nextInt(100, 200)
    return if (aa % 2 == 0) {
        aa.toMono()
    } else {
        Mono.empty()
    }

}
fun main(){
    main3().subscribe()
    Thread.sleep(70000)
}
fun main3():Mono<Void>{
val f1 = fallBack()

 return   Flux.range(1,100)
        .delayElements(Duration.ofMillis(1))
        .flatMap {
            println(Thread.currentThread().name)
            f1.zipWith(Mono.just(it))
        }
     .flatMap { repo().zipWith(Mono.just(it)) }
        .map{

            println("value of Momo"+it.t1+" value of flux:"+it.t2)
      }.then()
}

