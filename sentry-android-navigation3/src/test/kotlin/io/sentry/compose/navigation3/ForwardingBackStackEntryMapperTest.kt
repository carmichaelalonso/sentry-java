package io.sentry.compose.navigation3

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForwardingBackStackEntryMapperTest {
  private data class HomeRoute(val id: String = "home")

  private data class ProfileRoute(val userId: String)

  @Test
  fun `mapper returns info without allocating arguments when omitted`() {
    val sut = ForwardingBackStackEntryMapper {
      BackStackEntryMapper<HomeRoute> { BackStackEntryInfo(it.id) }
    }

    assertThat(sut.map(HomeRoute())).isEqualTo(BackStackEntryInfo("home"))
    assertThat(sut.map(HomeRoute()).properties).isNull()
  }

  @Test
  fun `mapper returns name and arguments together`() {
    val route = ProfileRoute("123")
    val sut =
      ForwardingBackStackEntryMapper({
        BackStackEntryMapper<ProfileRoute> { entry ->
          BackStackEntryInfo("profile-${entry.userId}", mapOf("userId" to entry.userId))
        }
      })

    assertThat(sut.map(route))
      .isEqualTo(BackStackEntryInfo("profile-123", mapOf("userId" to "123")))
  }

  @Test
  fun `equal entry info instances share the same hash code`() {
    val first = BackStackEntryInfo("profile", mapOf("userId" to "123"))
    val second = BackStackEntryInfo("profile", mapOf("userId" to "123"))

    assertThat(first).isEqualTo(second)
    assertThat(first.hashCode()).isEqualTo(second.hashCode())
  }

  @Test
  fun `equals and hash code include every property`() {
    val base = BackStackEntryInfo("profile", mapOf("userId" to "123"))

    val changedName = BackStackEntryInfo("settings", base.properties)
    val changedArguments = BackStackEntryInfo(base.name, mapOf("userId" to "456"))

    assertThat(changedName).isNotEqualTo(base)
    assertThat(changedName.hashCode()).isNotEqualTo(base.hashCode())
    assertThat(changedArguments).isNotEqualTo(base)
    assertThat(changedArguments.hashCode()).isNotEqualTo(base.hashCode())
  }

  @Test
  fun `toString includes the name and redacts arguments`() {
    val info = BackStackEntryInfo("profile", mapOf("userId" to "123"))

    assertThat(info.toString()).isEqualTo("BackStackEntryInfo(name=profile)")
    assertThat(info.toString()).doesNotContain("userId")
    assertThat(info.toString()).doesNotContain("123")
  }

  @Test
  fun `mapper hides reads from snapshot observation`() {
    val routeName = mutableStateOf("home")
    val sut = ForwardingBackStackEntryMapper {
      BackStackEntryMapper<HomeRoute> { BackStackEntryInfo(routeName.value) }
    }

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
