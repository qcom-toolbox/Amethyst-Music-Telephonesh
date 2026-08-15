package com.amethyst_music.player

interface PlaybackController {
    fun onSkipNext()
    fun onSkipPrevious()
}

object PlaybackHolder {
    var controller: PlaybackController? = null
}
