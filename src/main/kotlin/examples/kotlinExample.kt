package examples

import functionalhystrix.ConfigProperties
import functionalhystrix.withHystrix
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture


/**
 * This class defines methods representing the original service call that needs to be wrapped
 * by Hystris, the fallback service used in case of failure or timeout of the original service
 * call, and the service that adds Hystrix protection to the original service call..
 */
class KotlinFunctionalHystrixExample : (Pair<Int, String>) -> Mono<String> {

    /*
     * Original service call to be wrapped by Hystrix
     */
    private fun rawService(input: Pair<Int, String>): Mono<String> {
        val (idx, str) = input
        return when (str) {
            "normal" -> Mono.fromFuture(CompletableFuture.supplyAsync {
                println("Entered $input, running on ${Thread.currentThread()}")
                Thread.sleep(40)
                println("Exiting $input")
                "OK-normal[index=$idx]"
            })
            "slow" -> Mono.fromFuture(CompletableFuture.supplyAsync {
                println("Entered $input, running on ${Thread.currentThread()}")
                Thread.sleep(400)
                println("Exiting $input")
                "OK-slow[index=$idx]"
            })
            "error" -> Mono.fromFuture(CompletableFuture.supplyAsync {
                println("Entered $input, running on ${Thread.currentThread()}")
                Thread.sleep(2)
                println("Exiting $input")
                throw RuntimeException("service error, index=$idx")
            })
            else -> throw IllegalArgumentException(str)
        }
    }

    /*
     * Provides fallback in case a call to rawService times-out or errors-out
     */
    private fun fallback(input: Pair<Int, String>): Mono<String> =
            Mono.fromFuture(CompletableFuture.supplyAsync {
                println("Entered fallback for $input, running on ${Thread.currentThread()}")
                Thread.sleep(2)
                println("Exiting fallback for $input")
                "fallback(input=$input)"
            })

    /*
     * Customized Hystrix configuration properties
     */
    private val config: ConfigProperties =
            ConfigProperties(
                    executionIsolationSemaphoreMaxConcurrentRequests = 20,
                    fallbackIsolationSemaphoreMaxConcurrentRequests = 100,  // effectively no limits
                    executionTimeoutInMilliseconds = 260,
                    circuitBreakerSleepWindowInMilliseconds = 2000,
                    circuitBreakerErrorThresholdPercentage = 50 /*default*/)
    /**
     * Wraps the original service with Hystrix protection
     */
    override fun invoke(input: Pair<Int, String>): Mono<String> =
            withHystrix(
                    "KotlinFunctionalHystrixExample", "KotlinFunctionalHystrixExample",
                    ::rawService, ::fallback, config)(input)

    /**
     * Alternative to above apply when using the default Hystrix cocnfiguration
     */
    fun simpleInvoke(input: Pair<Int, String>): Mono<String> =
            withHystrix(
                    "KotlinFunctionalHystrixExample", "KotlinFunctionalHystrixExample",
                    ::rawService, ::fallback)(input)

    /**
     * Alternative to above apply when using the default Hystrix cocnfiguration and the
     * group key is equal to the command key
     */
    fun simplestInvoke(input: Pair<Int, String>): Mono<String> =
            withHystrix(
                    "KotlinFunctionalHystrixExample", ::rawService, ::fallback)(input)
}


/**
 * Example of FunctiionalHystrix in action
 */
fun main(args: Array<String>) {
    val example: (Pair<Int, String>) -> Mono<String> = KotlinFunctionalHystrixExample()

    val inputStrings =
            List(25) { "normal" } +
                    List(2) { "wait" } +
                    List(10) { "normal" } +
                    List(2) { "wait" } +
                    List(20) { "slow" } +
                    List(2) { "wait" } +
                    List(20) { "error" } +
                    List(1) { "wait" } +
                    List(20) { "error" } +
                    List(5) { "wait" } +
                    List(10) { "normal" } +
                    List(5) { "wait" } +
                    List(2) { "normal" } +
                    List(1) { "wait" } +
                    List(20) { "normal" }

    val inputs = inputStrings.indices.zip(inputStrings)

    val startTime = System.currentTimeMillis()

    val monos = inputs.map {
        if (it.second == "wait") {
            val wait: Long = 500
            println(">>> Starting to wait $wait ms")
            Thread.sleep(wait)
            println("<<< Finished waiting $wait ms")
            Mono.empty()
        } else
            example(it)  // uncomment lines below to print materialized values of monos
//                    .materialize()
//                    .doOnNext { println("** Materialized result: ${it}, running on ${Thread.currentThread()} **") }
//                    .toProcessor()
    }

    Flux.concat(monos).blockLast()

    println("@@@@ Elapsed time = ${System.currentTimeMillis() - startTime}")
}
