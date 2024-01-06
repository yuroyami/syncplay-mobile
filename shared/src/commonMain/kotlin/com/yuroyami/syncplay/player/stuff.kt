package com.yuroyami.syncplay.player

//TODO: mpv chapter control
//MenuItem(R.id.chapterBtn) {
//    val chapters = player.loadChapters()
//    if (chapters.isEmpty())
//        return@MenuItem true
//    val chapterArray = chapters.map {
//        val timecode = Utils.prettyTime(it.time.roundToInt())
//        if (!it.title.isNullOrEmpty())
//            getString(R.string.ui_chapter, it.title, timecode)
//        else
//            getString(R.string.ui_chapter_fallback, it.index+1, timecode)
//    }.toTypedArray()
//    val selectedIndex = MPVLib.getPropertyInt("chapter") ?: 0
//    with (AlertDialog.Builder(this)) {
//        setSingleChoiceItems(chapterArray, selectedIndex) { dialog, item ->
//            MPVLib.setPropertyInt("chapter", chapters[item].index)
//            dialog.dismiss()
//        }
//        setOnDismissListener { restoreState() }
//        create().show()
//    }; false
//},
//MenuItem(R.id.chapterPrev) {
//    MPVLib.command(arrayOf("add", "chapter", "-1")); true
//},
//MenuItem(R.id.chapterNext) {
//    MPVLib.command(arrayOf("add", "chapter", "1")); true
//},

//TODO Stats: MPVLib.command(arrayOf("script-binding", "stats/display-stats-toggle"))