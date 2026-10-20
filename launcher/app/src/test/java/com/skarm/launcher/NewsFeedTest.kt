package com.skarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests link the reference org.json, which tolerates trailing commas, so a parse
 * test alone would pass without the fix that Android's org.json needs. The sanitizer is
 * therefore checked on its own output.
 */
class NewsFeedTest {

    // The live Flamingo shape (trimmed), including its trailing commas in `events`.
    private val payload = """
        {"serverlist":[{"name":"Official",
        "defaultEventBanner":"https://cdn.example/v8fubp28.png|0.1",
        "defaultEventContent":"Breach The Vault|Hidden in the Clockworks\nare Treasure Vaults.",
        "defaultEventLink":"https://forums.spiralknights.com/en/node/122163",
        "defaultEventStartsAt":"0","defaultEventEndsAt":"0",
        "events":[
          {"type":"0","banner":"https://cdn.example/9dbipu42.png|0.1",
           "content":"Tortodrone Migration|The fiendish denizens\nare up to no good...",
           "link":"https://wiki.spiralknights.com/March_of_the_Tortodrones",
           "startsAt":"1791215181641","endsAt":"1792134000000",},
          {"type":"1","banner":"https://cdn.example/a039lhgd.png|0.1",
           "content":"Falling Into Winter|Autumn Prize Boxes",
           "link":"null","startsAt":"1791216238529","endsAt":"1792177200000",}
        ]}]}
    """.trimIndent()

    @Test
    fun stripsOnlyStructuralTrailingCommas() {
        val input = """{"a":"keep ,} and ,] here","b":[1,2, ],"c":{"d":"\",}",},}"""
        assertEquals("""{"a":"keep ,} and ,] here","b":[1,2 ],"c":{"d":"\",}"}}""",
            NewsFeed.stripTrailingCommas(input))
        assertFalse(Regex(""",\s*[}\]]""").containsMatchIn(NewsFeed.stripTrailingCommas(payload)))
    }

    @Test
    fun listsRunningAndUpcomingEventsInFeedOrder() {
        val events = NewsFeed.parse(payload, now = 1791300000000)
        assertEquals(listOf("Tortodrone Migration", "Falling Into Winter"), events.map { it.title })
        assertEquals("The fiendish denizens\nare up to no good...", events[0].body)
        assertEquals("https://cdn.example/9dbipu42.png", events[0].imageUrl)
        assertNull("a \"null\" link is no link", events[1].link)
    }

    @Test
    fun fallsBackToTheDefaultEventOnceEverythingHasEnded() {
        val events = NewsFeed.parse(payload, now = 1800000000000)
        assertEquals(listOf("Breach The Vault"), events.map { it.title })
    }

    @Test
    fun dropsNonHttpsUrlsButKeepsTheEvent() {
        val events = NewsFeed.parse(
            """{"serverlist":[{"name":"Official","events":[{"banner":"http://x/a.png|0.1",
               "content":"Plain","link":"http://x","startsAt":"0","endsAt":"0"}]}]}""",
        )
        assertEquals("Plain", events.single().title)
        assertEquals("", events.single().body)
        assertNull(events.single().imageUrl)
        assertNull(events.single().link)
    }

    @Test
    fun endingSoonMeansRunningWithUnderTwoDaysLeft() {
        val day = 24 * 60 * 60 * 1000L
        val event = NewsFeed.Event("t", "", null, null, startsAt = 0L, endsAt = 10 * day)
        assertTrue(event.isEndingSoon(now = 9 * day))
        assertFalse(event.isEndingSoon(now = 7 * day))
        assertFalse(event.isEndingSoon(now = 11 * day))
    }
}
