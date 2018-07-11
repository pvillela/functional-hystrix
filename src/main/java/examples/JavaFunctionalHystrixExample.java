package examples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import functionalhystrix.ConfigProperties;
import static functionalhystrix.WithHystrixKt.withHystrix;
import static functionalhystrix.WithHystrixKt.defaultConfigProperties;


/**
 * This class defines methods representing the original service call that needs to be wrapped
 * by Hystris, the fallback service used in case of failure or timeout of the original service
 * call, and the service that adds Hystrix protection to the original service call..
 */
public class JavaFunctionalHystrixExample implements Function<String, Mono<String>> {

    /*
     * Original service call to be wrapped by Hystrix
     */
    private Mono<String> rawService(String input) {
        if (input.equals("normal")) {
            return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
                System.out.println("Entered " + input + ", running on " + Thread.currentThread());
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                }
                System.out.println("Exiting " + input);
                return "OK-normal";
            }));
        } else if (input.equals("slow")) {
            return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
                System.out.println("Entered " + input + ", running on " + Thread.currentThread());
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                }
                System.out.println("Exiting " + input);
                return "OK-slow";
            }));
        } else if (input.equals("error")) {
            return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
                System.out.println("Entered " + input + ", running on " + Thread.currentThread());
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                }
                System.out.println("Exiting " + input);
                throw new RuntimeException("service error");
            }));
        } else throw new IllegalArgumentException(input);
    }

    /*
     * Provides fallback in case a call to rawService times-out or errors-out
     */
    private Mono<String> fallback(String input) {
        return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
            System.out.println("Entered fallback for " + input + ", running on " + Thread.currentThread());
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
            }
            System.out.println("Exiting fallback for " + input);
            return "fallback(input=" + input + ")";
        }));
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

        Function<String, Mono<String>> example = new JavaFunctionalHystrixExample();

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

        Flux.concat(monos).blockLast();

        System.out.println("@@@@ Elapsed time = " + (System.currentTimeMillis() - startTime));
    }
}
