package com.lessons.kotlinlessons

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import reactor.core.publisher.Flux
import java.util.*


@SpringBootApplication
class KotlinlessonsApplication

fun main(args: Array<String>) {
	abc()
	runApplication<KotlinlessonsApplication>(*args)
}

fun abc(){
	Flux.range(1,10)
		.map {
			println(it)
		}.subscribe()

	val coldPublisher = Flux.defer {
		println("Generating new items")
		Flux.just(UUID.randomUUID().toString())
	}
	println("No data was generated so far")
	coldPublisher.subscribe { e: String? -> println(e) }
	coldPublisher.subscribe { e: String? -> println( e) }
	println("Data was generated twice for two subscribers")

}