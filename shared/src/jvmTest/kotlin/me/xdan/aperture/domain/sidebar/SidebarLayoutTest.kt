package me.xdan.aperture.domain.sidebar

import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarLayoutTest {
    private val defaults = listOf("search", "library:a", "library:b", "my_list")

    @Test
    fun noLayoutKeepsTheDefaultOrder() {
        assertEquals(defaults, SidebarLayout().arrange(defaults))
    }

    @Test
    fun followsTheSavedOrderAndHidesHiddenEntries() {
        val layout = SidebarLayout(order = listOf("library:b", "my_list", "search", "library:a"), hidden = setOf("search"))
        assertEquals(listOf("library:b", "my_list", "library:a"), layout.arrange(defaults))
        assertEquals(listOf("library:b", "my_list", "search", "library:a"), layout.arrangeAll(defaults))
    }

    @Test
    fun aNewLibraryGoesAfterItsDefaultPredecessor() {
        val layout = SidebarLayout(order = listOf("my_list", "search", "library:a"))
        val withNew = listOf("search", "library:a", "library:new", "my_list")
        assertEquals(listOf("my_list", "search", "library:a", "library:new"), layout.arrange(withNew))
        // With no placed predecessor, it goes first.
        assertEquals(listOf("first", "my_list", "search", "library:a"), layout.arrange(listOf("first") + layout.order))
    }

    @Test
    fun entriesThatNoLongerExistAreDropped() {
        val layout = SidebarLayout(order = listOf("movies", "search", "shows", "my_list"))
        assertEquals(listOf("search", "my_list"), layout.arrange(listOf("search", "my_list")))
    }

    @Test
    fun encodesAndDecodes() {
        val layout = SidebarLayout(order = listOf("search", "library:abc"), hidden = setOf("my_list"))
        assertEquals(layout, SidebarLayout.decode(layout.encode()))
        assertEquals(SidebarLayout(), SidebarLayout.decode(null))
        assertEquals(SidebarLayout(order = listOf("search")), SidebarLayout.decode("search|"))
    }
}
