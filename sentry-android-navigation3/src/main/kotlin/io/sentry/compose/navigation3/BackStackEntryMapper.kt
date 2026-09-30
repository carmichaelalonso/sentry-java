package io.sentry.compose.navigation3

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.annotations.ApiStatus

/** Info about a given back stack entry for display in Sentry. */
@ApiStatus.Experimental
@ApiStatus.Internal
// TODO ADAM: Rename SentryBackStackEntry
public class BackStackEntryInfo(
  /**
   * A host-app defined name for a given back stack entry.
   *
   * Sentry interprets the name as a navigation destination. For instance, the following
   * `BackStackEntryInfo` instances:
   * ```kotlin
   * BackStackEntryInfo(name = "Home")
   * BackStackEntryInfo(name = "ProductDetail", properties = mapOf("product_id" to 1234))
   * ```
   *
   * will produce a breadcrumb like this:
   * ```json
   * {
   *   "from": "/Home",
   *   "to": "/ProductDetail",
   *   "to_properties": {
   *     "product_id": 1234
   *   }
   * }
   * ```
   *
   * Note that the Sentry SDK normalizes `name` to include a "/" prefix.
   */
  public val name: String,
  /**
   * Properties from a given back stack entry, as selected by the host app.
   *
   * Useful for capturing any properties in the back stack key that have diagnostic value.
   *
   * Sentry treats these as metadata to be displayed alongside [name] in appropriate contexts. (See
   * the `name` KDoc for an example.)
   */
  public val properties: Map<String, Any?>? = null,
) {

  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is BackStackEntryInfo && name == other.name && properties == other.properties)

  override fun hashCode(): Int = 31 * name.hashCode() + (properties?.hashCode() ?: 0)

  /** Omits properties because they may contain sensitive host-app data. */
  override fun toString(): String = "BackStackEntryInfo(name=$name)"
}

/**
 * Maps a back stack entry to a displayable name and optional diagnostic properties.
 *
 * **Privacy / PII**
 *
 * Values returned from [map] are ***not*** scrubbed by the Sentry SDK before being sent to Sentry.
 * Only return names and arguments that are known to be safe or have been pre-scrubbed.
 *
 * **Performance**
 *
 * This mapper is invoked synchronously from [SentryNavEffect] on the same apply thread that runs
 * the effect. Avoid non-performant mappings.
 *
 * With respect to [entry properties][BackStackEntryInfo.properties], prefer returning a small
 * subset of route data useful for diagnostics rather than serializing a back stack key in its
 * entirety in cases where the key may be large, nested, or privacy-sensitive.
 *
 * **Choosing appropriate names**
 *
 * Return stable, low-cardinality names that don't depend on object identity, argument values, or
 * runtime class-name preservation. E.g., `Home`, `DetailScreen`, etc.
 *
 * In particular, avoid `::class.simpleName`, as R8 obfuscates class names and may associate them
 * with different symbols across builds.
 *
 * **Names fall back to "/unknown"**
 *
 * If [map] throws or returns a blank [name][BackStackEntryInfo.name], Sentry records the
 * destination as "/unknown". Doing so signals that name extraction needs to be fixed while avoiding
 * misleading gaps in navigation data.
 *
 * For instance, if a user navigates from `/home -> /detail -> /settings`, but the mapper for
 * `/detail` throws, the back stack record will be `/home -> /unknown -> /settings` rather than
 * `/home -> /settings`.
 *
 * **Choosing appropriate properties**
 *
 * Property values may be any of the following scalar types:
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
 * Container values may be nested, but they must bottom out in supported scalar types. Cyclic or
 * deeply nested containers will be skipped.
 *
 * **Properties fall back to `toString()` or nothing**
 *
 * All non-supported property types are stringified via `toString()`. If [map] throws, no properties
 * are recorded for that back stack entry.
 *
 * **Using kotlinx.serialization**
 *
 * If your back stack contains `@Serializable` route types, consider mapping each to the type's
 * serialName. For performance reasons, don't serialize back stack keys in their entirety into
 * `properties` if they might be large, deeply nested, or privacy-sensitive.
 *
 * For instance:
 * ```kotlin
 * val backStackItemMapper = BackStackEntryMapper<Any> { route ->
 *   when (route) {
 *     is HomeRoute ->
 *       BackStackEntryInfo(HomeRoute.serializer().descriptor.serialName)
 *     is ProductDetailRoute -> BackStackEntryInfo(
 *       name = ProductDetailRoute.serializer().descriptor.serialName,
 *       // Select a subset of diagnostic properties when serialization is unsafe
 *       // or non-performant.
 *       properties = mapOf("product_id" to route.productId, "tab" to route.tab)
 *     )
 *   }
 * }
 * ```
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public fun interface BackStackEntryMapper<T : Any> {
  public fun map(backStackEntry: T): BackStackEntryInfo
}

/**
 * A Compose-compatible version of [BackStackEntryMapper] that generates [BackStackEntryInfo] by
 * forwarding the request to the host app mapper in effect at the time [map] is called.
 *
 * Dynamically determining the current mapper lets us separate two concerns:
 *
 * 1. the lifetime of a consumer that tracks navigation state over time (e.g., [BackStackObserver]);
 *    and
 * 2. the lifetime of the mapper used to generate `BackStackEntryInfo`.
 *
 * Without that separation, a long-lived consumer would have to choose between holding stale mapping
 * logic or recreating its own state whenever the mapper changed.
 */
internal class ForwardingBackStackEntryMapper<T : Any>(
  private val currentMapper: () -> BackStackEntryMapper<T>
) {
  fun map(backStackEntry: T): BackStackEntryInfo = Snapshot.withoutReadObservation {
    currentMapper().map(backStackEntry)
  }
}
