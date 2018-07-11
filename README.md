# functional-hystrix

Higher-order functions that protect services with a Hystrix circuit breaker.

This simple library makes it easy to use Hystrix by simply calling the provided higher-order functions with the original service as an argument.

- These functions were developed to support non-blocking programming with [Monos](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html) from [Project Reactor](https://projectreactor.io/).  
- The functions were developed in Kotlin.  
- Usage examples are provded for both Kotlin and Java.

The approach used here can be easily adapted to other futures frameworks like Java CompletableFutures and Scala Futures.

Higher-order functions like these could also be implemented to support blocking programming -- by why bother :-).
