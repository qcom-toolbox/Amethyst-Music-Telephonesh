package com.amethyst_music.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    private fun search(query: String, text: String) =
        SearchText.matches(text, SearchText.prepareQuery(query))

    @Test
    fun queryWithoutApostropheFindsTitleWithOne() {
        assertTrue(search("im crunk", "I'm Crunk"))
    }

    @Test
    fun queryWithApostropheRequiresOneInTheTitle() {
        assertFalse(search("i'm crunk", "Im Crunk"))
        assertTrue(search("i'm crunk", "I'm Crunk"))
    }

    @Test
    fun typographicApostrophesMatchStraightOnes() {
        assertTrue(search("i'm crunk", "I’m Crunk"))
        assertTrue(search("i’m", "I'm Crunk"))
        assertTrue(search("dont stop", "Don‘t Stop"))
        assertTrue(search("rockn roll", "Rock`n Roll"))
    }

    @Test
    fun otherPunctuationFollowsTheSameRule() {
        // Optional in the title...
        assertTrue(search("hello world", "Hello, World"))
        assertTrue(search("hiphop", "Hip-Hop"))
        assertTrue(search("acdc", "AC/DC"))
        // ...required when typed.
        assertFalse(search("hello, world", "Hello World"))
        assertFalse(search("hip-hop", "HipHop"))
        assertTrue(search("hip-hop", "Hip-Hop"))
    }

    @Test
    fun typedPunctuationMustMatchTheSamePunctuation() {
        assertFalse(search("ac,dc", "AC/DC"))
    }

    @Test
    fun spacesStillHaveToLineUp() {
        assertFalse(search("imcrunk", "I'm Crunk"))
    }

    @Test
    fun matchingIgnoresCase() {
        assertTrue(search("CRUNK", "i'm crunk"))
    }

    @Test
    fun punctuationOnlyQueryFindsOnlyTextContainingIt() {
        assertTrue(search("'", "I'm Crunk"))
        assertFalse(search("'", "Im Crunk"))
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(search("   ", "Anything"))
    }
}
