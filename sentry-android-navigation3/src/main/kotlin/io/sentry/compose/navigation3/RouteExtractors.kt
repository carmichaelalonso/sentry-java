package io.sentry.compose.navigation3

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.annotations.ApiStatus

/**
 * The name and optional diagnostic arguments Sentry records for one back stack item.
 *
 * Leave [arguments] `null` when no arguments are wanted. This avoids allocating an empty map for
 * destinations that only need a name.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public data class BackStackEntryInfo(
  public val name: String,
  public val arguments: Map<String, Any?>? = null,
)

/**
 * Maps a back stack entry to the name and optional diagnostic arguments Sentry records.
 *
 * **Privacy / PII**
 *
 * Values returned from [map] are ***not*** scrubbed by the Sentry SDK before being sent to Sentry.
 * Only return names and arguments that are known to be safe or have been pre-scrubbed.
 *
 * **Choosing appropriate route names**
 *
 * Implementations should return stable, low-cardinality names that don't depend on object identity,
 * argument values, or runtime class-name preservation. E.g., `Home`, `DetailScreen`, etc.
 *
 * In particular, avoid `::class.simpleName` in release builds, as R8 obfuscates class names and may
 * map them to different symbols across builds.
 *
 * Extractors are invoked synchronously from [SentryNavEffect] on the same apply thread that runs
 * the effect. Avoid non-performant extraction logic.
 *
 * **Falls back to "/unknown"**
 *
 * If [map] throws or returns a blank route name, Sentry records the destination as "/unknown".
 * Doing so signals that name extraction needs to be fixed while avoiding misleading gaps in
 * navigation data.
 *
 * For instance, if a user navigates from `/home -> /detail -> /settings`, but the mapper for
 * `/detail` throws, the back stack record will be `/home -> /unknown -> /settings` rather than
 * `/home -> /settings`.
 *
 * **Choosing appropriate route arguments**
 *
 * Return only a small subset of route data useful for diagnostics. Data should be stable enough to
 * inspect in Sentry.
 *
 * Extractors are invoked synchronously from [SentryNavEffect] on the same apply thread that runs
 * the effect. For performance reasons, implementations should avoid large structures. Cyclic or
 * deeply nested containers will be skipped. (See `RouteTranslator` for more details.)
 *
 * **Accepted value types**
 *
 * Values may be any of the following scalar types:
 *
 * - [String]
 * - [CharSequence]
 * - [Char]
 * - [Boolean]
 * - any [Number]
 * - enums (via [Enum.name])
 * - `null`
 *
 * Or any of the following container types:
 *
 * - [Array]s
 * - primitive arrays
 * - [Map]s
 * - [Collection]s
 *
 * Container values may be nested, and they must bottom out in supported scalar types.
 *
 * **Falls back to `toString()` or nothing**
 *
 * All non-supported types are stringified via `toString()`. If [map] throws, no arguments are
 * recorded for the destination.
 *
 * **Using kotlinx.serialization**
 *
 * If your back stack contains `@Serializable` route types, consider mapping each route type to a
 * stable serializer name. For instance:
 * ```kotlin
 * val backStackItemMapper = BackStackEntryMapper<Any> { route ->
 *   when (route) {
 *     is HomeRoute -> BackStackEntryInfo(HomeRoute.serializer().descriptor.serialName)
 *     is ProfileRoute -> BackStackEntryInfo(
 *       name = ProfileRoute.serializer().descriptor.serialName,
 *       arguments = mapOf("userId" to route.userId, "tab" to route.tab),
 *     )
 *     is SettingsRoute -> BackStackEntryInfo(
 *       name = SettingsRoute.serializer().descriptor.serialName,
 *       arguments = mapOf("section" to route.section),
 *     )
 *   }
 * }
 * ```
 *
 * Doing so gives each route type a stable, non-obfuscated name while leaving per-route arguments to
 * [BackStackEntryInfo].
 *
 * If your back stack contains `@Serializable` route types, avoid returning the entire route object
 * when it may be large, nested, or privacy-sensitive. Prefer a small set of diagnostic arguments
 * instead.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public fun interface BackStackEntryMapper<T : Any> {
  public fun map(backStackEntry: T): BackStackEntryInfo
}

internal open class BackStackEntryMappers<T : Any>(val mapper: BackStackEntryMapper<T>) {
  fun map(backStackEntry: T): BackStackEntryInfo = Snapshot.withoutReadObservation {
    mapper.map(backStackEntry)
  }
}

/**
 * Returns the [BackStackEntryMappers] currently in effect for the host app's back stack.
 *
 * Using a lazily evaluated provider lets us separate two concerns:
 *
 * 1. the lifetime of a consumer that tracks navigation state over time (e.g., [BackStackObserver]);
 *    and
 * 2. the lifetime of the host app-defined mapper used to map back stack entries to displayable
 *    Sentry data.
 *
 * Without that separation, a long-lived consumer would have to choose between holding stale mapping
 * logic or recreating its own state whenever the mapper changed.
 */
internal fun interface BackStackEntryMappersProvider<T : Any> {
  fun get(): BackStackEntryMappers<T>
}
