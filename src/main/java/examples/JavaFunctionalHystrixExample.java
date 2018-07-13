package examples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import functionalhystrix.ConfigProperties;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static functionalhystrix.WithHystrixKt.withHystrix;
import static functionalhystrix.WithHystrixKt.defaultConfigProperties;


/**
 * This example class illustrates methods representing the original service call that needs to
 * be wrapped by Hystris, the fallback service used in case of failure or timeout of the original
 * service call, and the service that adds Hystrix protection to the original service call.
 */
public class JavaFunctionalHystrixExample implements Function<String, Mono<String>> {

    private Scheduler scheduler = Schedulers.elastic();
    
    /*
     * Original service call to be wrapped by Hystrix
     */
    private Mono<String> rawService(String input) {
        if (input.equals("normal")) {
            return Mono.fromSupplier(() -> {
                System.out.println("Entered " + input + ", running on " + Thread.currentThread());
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                }
                System.out.println("Exiting " + input);
                return "OK-normal";
            }).subscribeOn(scheduler);
        } else if (input.equals("slow")) {
            return Mono.fromSupplier(() -> {
                System.out.println("Entered " + input + ", running on " + Thread.currentThread());
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                }
                System.out.println("Exiting " + input);
                return "OK-slow";
            }).subscribeOn(scheduler);
        } else if (input.equals("error")) {
            return Mono.<String>fromSupplier(() -> {
                System.out.println("Entered " + input + ", running on " + Thread.currentThread());
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                }
                System.out.println("Exiting " + input);
                throw new RuntimeException("service error");
            }).subscribeOn(scheduler);
        } else throw new IllegalArgumentException(input);
    }

    /*
     * Provides fallback in case a call to rawService times-out or errors-out
     */
    private Mono<String> fallback(String input) {
        return Mono.fromSupplier(() -> {
            System.out.println("Entered fallback for " + input + ", running on " + Thread.currentThread());
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
            }
            System.out.println("Exiting fallback for " + input);
            return "fallback(input=" + input + ")";
        }).subscribeOn(scheduler);
    }

    /*
     * Returns customized Hystrix configuration properties
     */
    private ConfigProperties getConfig() {
        ConfigProperties config = defaultConfigProperties();
        config.setExecutionIsolationSemaphoreMaxConcurrentRequests(20);
        config.setFallbackIsolationSemaphoreMaxConcurrentRequests(100);  // effectively no limits
        config.setExecutionTimeoutInMilliseconds(200);
        config.setCircuitBreakerSleepWindowInMilliseconds(2000);
        config.setCircuitBreakerErrorThresholdPercentage(50);  // default
        return config;
    }

    /**
     * Wraps the original service with Hystrix protection
     */
    @Override
    public Mono<String> apply(String input) {
        return withHystrix(
                "KotlinFunctionalHystrixExample", "KotlinFunctionalHystrixExample",
                this::rawService, this::fallback, getConfig()).invoke(input);
    }

    /**
     * Alternative to above apply when using the default Hystrix cocnfiguration
     */
    public Mono<String> simpleApply(String input) {
        return withHystrix(
                "KotlinFunctionalHystrixExample", "KotlinFunctionalHystrixExample",
                this::rawService, this::fallback).invoke(input);
    }

    /**
     * Alternative to above apply when using the default Hystrix cocnfiguration and the
     * group key is equal to the command key
     */
    public Mono<String> simplestApply(String input) {
        return withHystrix(
                "KotlinFunctionalHystrixExample", this::rawService, this::fallback).invoke(input);
    }
}


/**
 * Example of FunctionalHystrix in action
 */
class Main {

    public static void main(String[] args) {

        // Instantiate the function object that provides the service including Hystrix protection
        Function<String, Mono<String>> example = new JavaFunctionalHystrixExample();

        // Create a list of inputs to exercise the above example function.  See comment below for
        // an explanation of the "wait" items.
        List<String> inputStrings = new ArrayList<String>();
        inputStrings.addAll(Collections.nCopies(25, "normal"));
        inputStrings.addAll(Collections.nCopies(2, "wait"));
        inputStrings.addAll(Collections.nCopies(10, "normal"));
        inputStrings.addAll(Collections.nCopies(2, "wait"));
        inputStrings.addAll(Collections.nCopies(20, "slow"));
        inputStrings.addAll(Collections.nCopies(2, "wait"));
        inputStrings.addAll(Collections.nCopies(20, "error"));
        inputStrings.addAll(Collections.nCopies(1, "wait"));
        inputStrings.addAll(Collections.nCopies(20, "error"));
        inputStrings.addAll(Collections.nCopies(5, "wait"));
        inputStrings.addAll(Collections.nCopies(10, "normal"));
        inputStrings.addAll(Collections.nCopies(5, "wait"));
        inputStrings.addAll(Collections.nCopies(2, "normal"));
        inputStrings.addAll(Collections.nCopies(1, "wait"));
        inputStrings.addAll(Collections.nCopies(20, "normal"));

        long startTime = System.currentTimeMillis();

        // Apply the example function to each element of the above-defined list, with some interspersed
        // "wait"s.  The "wait"s are used to separate batches of requests (otherwise all requests would
        // be lanunched concurrently) and, in the case of the 5 contiguous "wait"s, to allow the
        // configured circuit-breaker sleep window to elapse so Hystrix can allow new requests through
        // again (i.e., close the circuit) after it opens the circuit as a result of a series of errors
        // or timeouts.
        List<Mono<String>> monos = inputStrings.stream().map(it -> {
            if (it.equals("wait")) {
                long wait = 500;
                System.out.println(">>> Starting to wait "+ wait + " ms");
                try {
                    Thread.sleep(wait);
                } catch(InterruptedException e) {
                }
                System.out.println("<<< Finished waiting " + wait + " ms");
                return Mono.<String>empty();
            } else
                return example.apply(it);  // uncomment lines below to print materialized values of monos
//                    .materialize()
//                    .doOnNext { println("** Materialized result: ${it}, running on ${Thread.currentThread()} **") }
//                    .toProcessor();
        }).collect(Collectors.toList());

        // Block the main thread while any request monos are still active, until they all complete.
        Flux.concat(monos).blockLast();

        System.out.println("@@@@ Elapsed time = " + (System.currentTimeMillis() - startTime));
    }
}
