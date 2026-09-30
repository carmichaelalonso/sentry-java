package io.sentry.compose.navigation3

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RouteExtractorsTest {
  private data class HomeRoute(val id: String = "home")

  private data class ProfileRoute(val userId: String)

  @Test
  fun `mapper returns info without allocating arguments when omitted`() {
    val sut = BackStackEntryMappers(BackStackEntryMapper<HomeRoute> { BackStackEntryInfo(it.id) })

    assertThat(sut.map(HomeRoute())).isEqualTo(BackStackEntryInfo("home"))
    assertThat(sut.map(HomeRoute()).arguments).isNull()
  }

  @Test
  fun `mapper returns name and arguments together`() {
    val route = ProfileRoute("123")
    val sut =
      BackStackEntryMappers(
        BackStackEntryMapper<ProfileRoute> { entry ->
          BackStackEntryInfo("profile-${entry.userId}", mapOf("userId" to entry.userId))
        }
      )

    assertThat(sut.map(route))
      .isEqualTo(BackStackEntryInfo("profile-123", mapOf("userId" to "123")))
  }

  @Test
  fun `mapper hides reads from snapshot observation`() {
    val routeName = mutableStateOf("home")
    val sut =
      BackStackEntryMappers(BackStackEntryMapper<HomeRoute> { BackStackEntryInfo(routeName.value) })

    assertThat(observeReads { sut.map(HomeRoute()) }).isEqualTo(0)
  }

  private fun observeReads(block: () -> Unit): Int {
    var reads = 0
    val snapshot = Snapshot.takeSnapshot(readObserver = { reads++ })
    try {
      snapshot.enter(block)
    } finally {
      snapshot.dispose()
    }
    return reads
  }
}
