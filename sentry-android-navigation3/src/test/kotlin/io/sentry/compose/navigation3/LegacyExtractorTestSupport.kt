package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import io.sentry.IScopes

fun interface RouteNameExtractor<T : Any> {
  fun extract(backStackEntry: T): String
}

fun interface RouteArgumentsExtractor<T : Any> {
  fun extract(backStackEntry: T): Map<String, Any?>
}

internal class RouteExtractors<T : Any>(
  private val nameExtractor: RouteNameExtractor<T>,
  private val argumentsExtractor: RouteArgumentsExtractor<T>?,
) :
  BackStackEntryMappers<T>(
    BackStackEntryMapper { entry ->
      BackStackEntryInfo(nameExtractor.extract(entry), argumentsExtractor?.extract(entry))
    }
  )

@Composable
internal fun <T : Any> SentryNavEffect(
  backStack: List<T>,
  nameExtractor: RouteNameExtractor<T>,
  argumentsExtractor: RouteArgumentsExtractor<T>? = null,
  options: SentryNavOptions = SentryNavOptions(),
  scopes: IScopes,
) {
  SentryNavEffect(
    backStack = backStack,
    backStackEntryMapper =
      BackStackEntryMapper { entry ->
        BackStackEntryInfo(nameExtractor.extract(entry), argumentsExtractor?.extract(entry))
      },
    options = options,
    scopes = scopes,
  )
}
