package com.lessons.kotlinlessons

import com.github.javafaker.Faker
import com.lessons.kotlinlessons.section6operators.fallBack
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@SpringBootTest
class KotlinlessonsApplicationTests


    fun  main() {
        main3().subscribe()
    }

    fun main3():Mono<Void>{

        println(Thread.currentThread().name)
        return   Flux.range(1,100)

            .flatMap {

                fallBack().zipWith(Mono.just(it))
            }
            .map{

                println("value of Momo"+it.t1+" value of flux:"+it.t2)
            }.then()
    }

    fun fallBack(): Mono<Int> {

        Thread.sleep(10000)

        return Faker.instance().random().nextInt(100,200).toMono()
    }


