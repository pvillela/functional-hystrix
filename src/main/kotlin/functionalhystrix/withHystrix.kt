/*
 * The higher-order functions which provide Hystrix circuit breaker protection are defined
 * and implemented in this file.
 */

package functionalhystrix

import reactor.core.publisher.Mono
import com.netflix.hystrix.HystrixObservableCommand
import com.netflix.hystrix.HystrixObservableCommand.Setter
import com.netflix.hystrix.HystrixCommandKey
import com.netflix.hystrix.HystrixCommandGroupKey
import com.netflix.hystrix.HystrixCommandProperties
import com.netflix.hystrix.HystrixCommandProperties.Setter as PropertiesSetter
import rx.Observable
import rx.RxReactiveStreams


///////////////////
// Most-useful Hystrix configuraton defaults

private val dummyCmd: HystrixObservableCommand<Void> = object :
        HystrixObservableCommand<Void>(HystrixCommandGroupKey.Factory.asKey("dummy")) {
    protected override fun construct(): Observable<Void> {
        throw NotImplementedError()
    }
}

private val defaultProperties: HystrixCommandProperties = dummyCmd.properties

private val defaultExecutionIsolationSemaphoreMaxConcurrentRequests: Int =
        defaultProperties.executionIsolationSemaphoreMaxConcurrentRequests().get()
private val defaultExecutionTimeoutInMilliseconds: Int =
        defaultProperties.executionTimeoutInMilliseconds().get()
private val defaultFallbackIsolationSemaphoreMaxConcurrentRequests: Int =
        defaultExecutionIsolationSemaphoreMaxConcurrentRequests
private val defaultCircuitBreakerRequestVolumeThreshold: Int =
        defaultProperties.circuitBreakerRequestVolumeThreshold().get()
private val defaultCircuitBreakerSleepWindowInMilliseconds: Int =
        defaultProperties.circuitBreakerSleepWindowInMilliseconds().get()
private val defaultCircuitBreakerErrorThresholdPercentage: Int =
        defaultProperties.circuitBreakerErrorThresholdPercentage().get()

/**
 * Data structure to hold most-useful Hystrix configuration properties.  See the Hystrix
 * documentation for the meanings of these properties.
 */
data class ConfigProperties(
        var executionIsolationSemaphoreMaxConcurrentRequests: Int =
                defaultExecutionIsolationSemaphoreMaxConcurrentRequests,
        var executionTimeoutInMilliseconds: Int = defaultExecutionTimeoutInMilliseconds,
        var fallbackIsolationSemaphoreMaxConcurrentRequests: Int =
                defaultFallbackIsolationSemaphoreMaxConcurrentRequests,
        var circuitBreakerRequestVolumeThreshold: Int = defaultCircuitBreakerRequestVolumeThreshold,
        var circuitBreakerSleepWindowInMilliseconds: Int = defaultCircuitBreakerSleepWindowInMilliseconds,
        var circuitBreakerErrorThresholdPercentage: Int = defaultCircuitBreakerErrorThresholdPercentage) {
}


/**
 * For convenient instantiation of a default ConfigProperties from Java
 */
fun defaultConfigProperties(): ConfigProperties = ConfigProperties()


/*
 * Helper Hystrix command class used in the implementation of the withHystrix functions
 */
private class HystrixCmd<T, U>(private val setter: Setter,
                               private val input: T,
                               private val svc: (T) -> Mono<U>,
                               private val fallback: ((T) -> Mono<U>)? = null)
    : HystrixObservableCommand<U>(setter) {

    protected override fun construct(): Observable<U> {
        return RxReactiveStreams.toObservable(svc(input))
    }

    protected override fun resumeWithFallback(): Observable<U> {
        val ret =
                if (fallback != null) {
                    RxReactiveStreams.toObservable(fallback.invoke(input))
                } else {
                    super.resumeWithFallback()
                }
        return ret
    }
}


/**
 * Wraps Hystrix around a given function.  This variant of [withHystrix], which takes a
 * HystrixObservableCommand.Setter as one of its inputs, is the most general as well as
 * the least convenient to use.  It is used to implement the other variants and can
 * be used if Hystrix configuration is required beyond that provided by the
 * ConfigProperties data class in this package.
 *
 * @param setter HystrixObservableCommand.Setter used to configure the command.
 * @param svc the original service to be protected by Hystrix.
 * @param fallback the fallback service.
 * @return a wrapped service that is protected by Hystrix.  The service produces a pair
 * whose first element is the result of the original service call (protected by Hystrix)
 * and whose second element is the Hystrix command used in the implementation.  The
 * Hystrix command can be used to obtain information details about the execution through
 * Hystrix.
 */
fun <T, U> withHystrixExt(setter: Setter,
                          svc: (T) -> Mono<U>,
                          fallback: ((T) -> Mono<U>)? = null)
        : (T) -> Pair<Mono<U>, HystrixObservableCommand<U>> =
        { input ->
            val cmd = HystrixCmd(setter, input, svc, fallback)
            val observable = cmd.observe()
            Pair(Mono.from(RxReactiveStreams.toPublisher(observable)), cmd)
        }


/**
 * Wraps Hystrix around a given function.  This variant of [withHystrix], which takes a
 * HystrixObservableCommand.Setter as one of its inputs, is very general but not
 * very convenient to use.  It can be used if Hystrix configuration is required beyond
 * that provided by the [ConfigProperties] data class in this package.
 *
 * @param setter HystrixObservableCommand.Setter used to configure the command.
 * @param svc the original service to be protected by Hystrix.
 * @param fallback the fallback service.
 * @return a wrapped service that is protected by Hystrix.
 */
fun <T, U> withHystrix(setter: Setter,
                       svc: (T) -> Mono<U>,
                       fallback: ((T) -> Mono<U>)? = null)
        : (T) -> Mono<U> =
        { withHystrixExt(setter, svc, fallback)(it).first }


/**
 * Wraps Hystrix around a given function.  This variant of [withHystrix] takes a
 * [ConfigProperties] object as an input, allowing convenient customization of Hystrix
 * properties, without requiring the use of the HystrixObservableCommand.Setter class.
 *
 * @param svcId string uniquely identifying the original service.  This crucial
 * parameter, which corresponds to the Hystrix command key, is used by Hystrix to
 * collect statistics about the service, keep track of its health, and actuate the
 * circuit breaker as appropriate.
 * @param svcGroupId string identifying a group of services to which this service
 * belongs.  It corresponds to the Hystrix command group key, which is used by
 * Hystrix to group information for reporting purposes.  This parameter does not
 * impact the behaviour of the circuit breaker.
 * @param svc the original service to be protected by Hystrix.
 * @param fallback the fallback service.
 * @param config a [ConfigProperties] object used to customize Hystrix
 * configuration.
 * @return a wrapped service that is protected by Hystrix.  The service produces a pair
 * whose first element is the result of the original service call (protected by Hystrix)
 * and whose second element is the Hystrix command used in the implementation.  The
 * Hystrix command can be used to obtain information details about the execution through
 * Hystrix.
 */
fun <T, U> withHystrixExt(svcId: String,
                          svcGroupId: String,
                          svc: (T) -> Mono<U>,
                          fallback: ((T) -> Mono<U>)? = null,
                          config: ConfigProperties = ConfigProperties())
        : (T) -> Pair<Mono<U>, HystrixObservableCommand<U>> =
        { input ->
            withHystrixExt(
                    Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(svcGroupId))
                            .andCommandKey(HystrixCommandKey.Factory.asKey(svcId))
                            .andCommandPropertiesDefaults(PropertiesSetter()
                                    .withExecutionIsolationSemaphoreMaxConcurrentRequests(
                                            config.executionIsolationSemaphoreMaxConcurrentRequests)
                                    .withExecutionTimeoutInMilliseconds(
                                            config.executionTimeoutInMilliseconds)
                                    .withFallbackIsolationSemaphoreMaxConcurrentRequests(
                                            config.fallbackIsolationSemaphoreMaxConcurrentRequests)
                                    .withCircuitBreakerRequestVolumeThreshold(
                                            config.circuitBreakerRequestVolumeThreshold)
                                    .withCircuitBreakerSleepWindowInMilliseconds(
                                            config.circuitBreakerSleepWindowInMilliseconds)
                                    .withCircuitBreakerErrorThresholdPercentage(
                                            config.circuitBreakerErrorThresholdPercentage)),
                    svc,
                    fallback)(input)
        }


/**
 * Wraps Hystrix around a given function.  This variant of [withHystrix] uses a
 * default [ConfigProperties] object, so it can be used when there is no need
 * to customize Hystrix properties.
 * This is a convenience signature for calls from Java
 *
 * @param svcId string uniquely identifying the original service.  This crucial
 * parameter, which corresponds to the Hystrix command key, is used by Hystrix to
 * collect statistics about the service, keep track of its health, and actuate the
 * circuit breaker as appropriate.
 * @param svcGroupId string identifying a group of services to which this service
 * belongs.  It corresponds to the Hystrix group key, which is used by
 * Hystrix to group information for reporting purposes.  This parameter does not
 * impact the behaviour of the circuit breaker.
 * @param svc the original service to be protected by Hystrix.
 * @param fallback the fallback service.
 * @return a wrapped service that is protected by Hystrix.  The service produces a pair
 * whose first element is the result of the original service call (protected by Hystrix)
 * and whose second element is the Hystrix command used in the implementation.  The
 * Hystrix command can be used to obtain information details about the execution through
 * Hystrix.
 */
fun <T, U> withHystrixExt(svcId: String,
                          svcGroupId: String,
                          svc: (T) -> Mono<U>,
                          fallback: ((T) -> Mono<U>)? = null)
        : (T) -> Pair<Mono<U>, HystrixObservableCommand<U>> =
        { withHystrixExt(svcId, svcGroupId, svc, fallback, ConfigProperties())(it) }


/**
 * Wraps Hystrix around a given function.  This variant of [withHystrix] takes a
 * [ConfigProperties] object as an input, allowing convenient customization of Hystrix
 * properties, without requiring the use of the HystrixObservableCommand.Setter class.
 *
 * @param svcId string uniquely identifying the original service.  This crucial
 * parameter, which corresponds to the Hystrix command key, is used by Hystrix to
 * collect statistics about the service, keep track of its health, and actuate the
 * circuit breaker as appropriate.
 * @param svcGroupId string identifying a group of services to which this service
 * belongs.  It corresponds to the Hystrix group key, which is used by
 * Hystrix to group information for reporting purposes.  This parameter does not
 * impact the behaviour of the circuit breaker.
 * @param svc the original service to be protected by Hystrix.
 * @param fallback the fallback service.
 * @param config a [ConfigProperties] object used to customize Hystrix
 * configuration.
 * @return a wrapped service that is protected by Hystrix.
 */
fun <T, U> withHystrix(svcId: String,
                       svcGroupId: String,
                       svc: (T) -> Mono<U>,
                       fallback: ((T) -> Mono<U>)? = null,
                       config: ConfigProperties = ConfigProperties())
        : (T) -> Mono<U> =
        { withHystrixExt(svcId, svcGroupId, svc, fallback, config)(it).first }


/**
 * Wraps Hystrix around a given function.  This variant of [withHystrix] uses a
 * default [ConfigProperties] object, so it can be used when there is no need
 * to customize Hystrix properties.
 * This is a convenience signature for calls from Java
 *
 * @param svcId string uniquely identifying the original service.  This crucial
 * parameter, which corresponds to the Hystrix command key, is used by Hystrix to
 * collect statistics about the service, keep track of its health, and actuate the
 * circuit breaker as appropriate.
 * @param svcGroupId string identifying a group of services to which this service
 * belongs.  It corresponds to the Hystrix command group key, which is used by
 * Hystrix to group information for reporting purposes.  This parameter does not
 * impact the behaviour of the circuit breaker.
 * @param svc the original service to be protected by Hystrix.
 * @param fallback the fallback service.
 * @return a wrapped service that is protected by Hystrix.
 */
fun <T, U> withHystrix(svcId: String,
                       svcGroupId: String,
                       svc: (T) -> Mono<U>,
                       fallback: ((T) -> Mono<U>)? = null)
        : (T) -> Mono<U> =
        { withHystrixExt(svcId, svcGroupId, svc, fallback)(it).first }


/**
 * Wraps Hystrix around a given function.  This variant of [withHystrix] uses a
 * default [ConfigProperties] object, so it can be used when there is no need
 * to customize Hystrix properties, and it assumes that the command key (svcId) is
 * the same as the group key (svcGroupId).
 *
 * @param svcId string uniquely identifying the original service.  This crucial
 * parameter, which corresponds to the Hystrix command key, is used by Hystrix to
 * collect statistics about the service, keep track of its health, and actuate the
 * circuit breaker as appropriate.
 * @param svc the original service to be protected by Hystrix.
 * @param fallback the fallback service.
 * @return a wrapped service that is protected by Hystrix.
 */
fun <T, U> withHystrix(svcId: String,
                       svc: (T) -> Mono<U>,
                       fallback: ((T) -> Mono<U>)? = null)
        : (T) -> Mono<U> =
        { withHystrix(svcId, svcId, svc, fallback)(it) }
