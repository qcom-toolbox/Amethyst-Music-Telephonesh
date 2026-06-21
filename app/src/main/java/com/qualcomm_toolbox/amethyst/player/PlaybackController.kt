package com.qualcomm_toolbox.amethyst.player

interface PlaybackController {
    fun onSkipNext()
    fun onSkipPrevious()
}

object PlaybackHolder {
    var controller: PlaybackController? = null
}
