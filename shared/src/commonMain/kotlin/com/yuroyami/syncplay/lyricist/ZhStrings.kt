package com.yuroyami.syncplay.lyricist

import com.yuroyami.syncplay.utils.format

val ZhStrings = object : Strings {
    override val appName: String
        get() = TODO("Not yet implemented")
    override val yes = "是的"

    override val no = "不"

    override val okay = "好"

    override val cancel = "取消"

    override val dontShowAgain = "下次不再显示"

    override val play = "播放"

    override val pause = "暂停"

    override val delete = "删除"
    override val confirm: String
        get() = TODO("Not yet implemented")
    override val done: String
        get() = TODO("Not yet implemented")
    override val close: String
        get() = TODO("Not yet implemented")
    override val off: String
        get() = TODO("Not yet implemented")
    override val on: String
        get() = TODO("Not yet implemented")

    override val tabConnect = "连接"

    override val tabSettings = "设置"

    override val tabAbout = "关于"

    override val connectUsernameA = "输入您的用户名:"

    override val connectUsernameB = "用户名"

    override val connectUsernameC = "随意输入"

    override val connectRoomnameA = "输入房间名称:"

    override val connectRoomnameB = "房间名称"

    override val connectRoomnameC = "你和你的朋友可以观看的房间"

    override val connectServerA = "选择Syncplay服务器:"

    override val connectServerB = "服务器地址"

    override val connectServerC = "确保你和你的朋友加入同一个服务器."
    override val connectButtonJoin: String
        get() = TODO("Not yet implemented")

    override val connectButton = "加入/创建房间"
    override val connectButtonSaveshortcut: String
        get() = TODO("Not yet implemented")
    override val connectButtonCurrentEngine: (String) -> String
        get() = TODO("Not yet implemented")

    override val connectFootnote = "Syncplay的非官方Android客户端"

    override val connectUsernameEmptyError = "用户名不能为空"

    override val connectRoomnameEmptyError = "房间名称不能为空"
    override val connectAddressEmptyError: String
        get() = TODO("Not yet implemented")
    override val connectPortEmptyError: String
        get() = TODO("Not yet implemented")

    override val connectEnterCustomServer = "输入自定义服务器"

    override val connectCustomServerPassword = "密码(如不需要，则为空)"

    override val connectPort = "端口"
    override val connectNightmodeswitch: String
        get() = TODO("Not yet implemented")
    override val connectSolomode: String
        get() = TODO("Not yet implemented")

    override val roomStartupHint = "\"通过点击下面的图标按钮从手机存储插入一个新的视频\""

    override val roomSelectedVid = { p0: String -> 
        "选择的视频文件: %s"
            .format(p0)
    }

    override val roomSelectedSub = { p0: String -> 
        "选择的字幕文件: %s"
            .format(p0)
    }

    override val roomSelectedSubError = "无效的字幕文件.支持的格式有: 'SRT', 'TTML', 'ASS', 'SSA', 'VTT'"

    override val roomSubErrorLoadVidFirst = "第一次加载视频"

    override val roomTypeMessage = "输入你要说的内容…"

    override val roomSendButton = "发送"

    override val roomReady = "准备"

    override val roomDetails = "显示详细信息"

    override val roomPingConnected = { p0: String -> 
        "连接 - 延迟: %s ms"
            .format(p0)
    }

    override val roomPingDisconnected = "断开连接"

    override val roomOverflowSub = "加载字幕文件…"

    override val roomOverflowCutout = "断路器(切口)模式"

    override val roomOverflowFf = "快速查找按钮"

    override val roomOverflowMsghistory = "历史信息"

    override val roomOverflowSettings = "设置"

    override val roomEmptyMessageError = "发送内容为空!"

    override val roomAttemptingConnect = "试图连接到 %1s:%2s"

    override val roomConnectedToServer = "已连接到服务器."

    override val roomConnectionFailed = "未能连接到服务器."

    override val roomAttemptingReconnection = "与服务器失去连接,正在尝试重新连接…"

    override val roomGuyPlayed = { p0: String -> 
        "%s 恢复播放"
            .format(p0)
    }

    override val roomGuyPaused = "%1s 暂停 在 %2s"

    override val roomSeeked = "%1s 跳转 从 %2s 到 %3s"

    override val roomRewinded = { p0: String -> 
        "由于播放时间差距大而倒退 %s"
            .format(p0)
    }

    override val roomGuyLeft = { p0: String -> 
        "%s 离开房间."
            .format(p0)
    }

    override val roomGuyJoined = { p0: String -> 
        "%s 您已经加入聊天室."
            .format(p0)
    }

    override val roomIsplayingfile = "%1s 正在播放 '%2s' (%3s)"

    override val roomYouJoinedRoom = { p0: String -> 
        "你已经加入聊天室: %s"
            .format(p0)
    }

    override val roomScalingFitScreen = "调整尺寸: 适合屏幕"

    override val roomScalingFixedWidth = "调整尺寸: 固定宽度"

    override val roomScalingFixedHeight = "调整尺寸: 固定高度"

    override val roomScalingFillScreen = "调整尺寸: 铺满屏幕"

    override val roomScalingZoom = "调整尺寸: 裁剪放大"

    override val roomSubTrackChanged = { p0: String -> 
        "字幕轨道更改为: %s"
            .format(p0)
    }

    override val roomAudioTrackChanged = { p0: String -> 
        "音轨更改为: %s"
            .format(p0)
    }

    override val roomAudioTrackNotFound = "没有发现音频 !"

    override val roomSubTrackDisable = "禁用字幕"

    override val roomTrackTrack = "轨道"

    override val roomSubTrackNotfound = "没有发现字幕 !"

    override val roomDetailsCurrentRoom = { p0: String -> 
        "当前的房间: %s"
            .format(p0)
    }

    override val roomDetailsNofileplayed = "(没有播放视频)"

    override val roomDetailsFileProperties = "时长: %1s - 大小: %2s MBs"

    override val disconnectedNoticeHeader = "断开连接"

    override val disconnectedNoticeA = "您已与服务器断开连接. 这可能是由于您的网络连接的问题或您正在连接的服务器的问题.如果问题仍然存在,请更换服务器."

    override val disconnectedNoticeB = "尝试重新连接…"

    override val roomFileMismatchWarningCore = { p0: String -> 
        "\"你的文件不同于 %s's 请按照以下方式进行归档: \""
            .format(p0)
    }

    override val roomFileMismatchWarningName = "\"名称. \""

    override val roomFileMismatchWarningDuration = "\"时长. \""

    override val roomFileMismatchWarningSize = "\"大小. \""

    override val roomSharedPlaylist = "房间的分享播放列表"

    override val roomSharedPlaylistBrief = "导入一个文件或一个目录，包括文件名到播放列表. 单击文件链接，让所有用户播放它."

    override val roomSharedPlaylistUpdated = { p0: String -> 
        "%s 更新播放列表"
            .format(p0)
    }

    override val roomSharedPlaylistChanged = { p0: String -> 
        "%s 更改了当前选择的播放列表"
            .format(p0)
    }

    override val roomSharedPlaylistNoDirectories = "您没有为共享播放列表指定任何媒体目录. 手动添加文件."

    override val roomSharedPlaylistNotFound = "Syncplay找不到当前正在媒体目录中的共享播放列表中播放的文件."

    override val roomSharedPlaylistNotFeatured = "此房间或服务器没有共享播放列表功能."
    override val roomSharedPlaylistButtonAddFile: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonAddFolder: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonAddUrl: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonShuffle: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonShuffleRest: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonOverflow: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonPlaylistImport: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonPlaylistImportNShuffle: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonPlaylistExport: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonSetMediaDirectories: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonSetTrustedDomains: String
        get() = TODO("Not yet implemented")
    override val roomSharedPlaylistButtonUndo: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescAspectRatio: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescSharedPlaylist: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescAudioTracks: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescSubtitleTracks: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescRewind: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescToggle: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescPlay: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescPause: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescFfwd: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescAdd: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescLock: String
        get() = TODO("Not yet implemented")
    override val roomButtonDescMore: String
        get() = TODO("Not yet implemented")
    override val roomAddmediaOffline: String
        get() = TODO("Not yet implemented")
    override val roomAddmediaOnline: String
        get() = TODO("Not yet implemented")
    override val roomAddmediaOnlineUrl: String
        get() = TODO("Not yet implemented")

    override val mediaDirectories = "共享播放列表的媒体目录"

    override val mediaDirectoriesBrief = "Syncplay将搜索您在这里指定的任何媒体目录，以便找到共享播放列表正在播放视频的名称. 如果指定较小的文件目录会更好，因为搜索操作可能会受限并非常慢."

    override val mediaDirectoriesSettingSummary = "Syncplay将搜索您在这里指定的任何媒体目录，以便找到共享播放列表正在播放视频的名称."

    override val mediaDirectoriesSave = "保存&退出"

    override val mediaDirectoriesClearAll = "全部清除"

    override val mediaDirectoriesClearAllConfirm = "你确定要清除列表吗 ?"

    override val mediaDirectoriesAddFolder = "添加文件夹"

    override val mediaDirectoriesDelete = "从列表中删除"

    override val mediaDirectoriesShowFullPath = "显示全部选项"

    override val settingsCategGeneral = "通用设置"

    override val settingsCategPlayer = "播放器设置"

    override val settingsCategRoom = "房间设置"

    override val settingsCategVideo = "视频设置"

    override val settingsCategMisc = "其他参数"
    override val settingNightModeTitle: String
        get() = TODO("Not yet implemented")
    override val settingNightModeSummary: String
        get() = TODO("Not yet implemented")

    override val settingRememberJoinInfoTitle = "记住加入过的服务器"

    override val settingRememberJoinInfoSummary = "默认启用. 这将允许SyncPlay保存你最后的用户名, 房间名, 以及你最后一次使用的Syncplay服务器."

    override val settingDisplayLanguageTitle = "显示语言"

    override val settingDisplayLanguageSummry = "选择Syncplay所显示的语言."

    override val settingDisplayLanguageToast = { p0: String -> 
        "改变语言: %s. 重启app使设置生效."
            .format(p0)
    }
    override val settingAudioDefaultLanguageTitle: String
        get() = TODO("Not yet implemented")
    override val settingAudioDefaultLanguageSummry: String
        get() = TODO("Not yet implemented")
    override val settingCcDefaultLanguageTitle: String
        get() = TODO("Not yet implemented")
    override val settingCcDefaultLanguageSummry: String
        get() = TODO("Not yet implemented")

    override val settingUseBufferTitle = "使用自定义缓冲区大小"

    override val settingUseBufferSummary = "如果您对播放器在播放前和播放期间的视频加载时间不满意, 您可以使用自定义缓冲区大小 (使用风险自负)."

    override val settingMaxBufferTitle = "自定义最大缓冲区大小"

    override val settingMaxBufferSummary = "默认为 50 (50000 毫秒). 这决定了开始播放视频之前的最大缓冲区大小. 如果你不知道这是什么, 不要更改."

    override val settingMinBufferSummary = "默认为 15 (15000 毫秒). 降低这个值以更快地播放视频，但有可能播放器会失败甚至崩溃。改变，风险自负."

    override val settingMinBufferTitle = "自定义最小缓冲区大小"

    override val settingPlaybackBufferSummary = "默认为 2500 毫秒. 这表示寻求或取消暂停视频时的缓冲区大小. 整改这个，如果你不满意的小延迟寻求视频."

    override val settingPlaybackBufferTitle = "自定义播放缓冲区大小 (ms)"

    override val settingReadyFirsthandSummary = "如果您想在进入房间后自动设置为准备好，请启用此选项."

    override val settingReadyFirsthandTitle = "自动将我设为已准备"

    override val settingRewindThresholdSummary = "进度过快时,倒退视频与其他人同步."

    override val settingRewindThresholdTitle = "倒退阈值"

    override val uisettingSubtitleSizeSummary = "默认为 18. 这将改变字幕的大小."

    override val uisettingSubtitleSizeTitle = "字幕大小"
    override val uisettingSubtitleDelaySummary: String
        get() = TODO("Not yet implemented")
    override val uisettingSubtitleDelayTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingAudioDelaySummary: String
        get() = TODO("Not yet implemented")
    override val uisettingAudioDelayTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingSeekForwardJumpSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingSeekForwardJumpTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingSeekBackwardJumpSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingSeekBackwardJumpTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingPipSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingPipTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingReconnectIntervalSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingReconnectIntervalTitle: String
        get() = TODO("Not yet implemented")

    override val settingTlsSummary = "如果服务器支持TLS安全连接, Android将尝试通过TLS连接到它。(未提供)"

    override val settingTlsTitle = "使用安全连接(TLSv1.3)[即将推出]"

    override val settingResetdefaultTitle = "调回默认设置"

    override val settingResetdefaultSummary = "将所有内容重置为默认值(推荐)"
    override val settingResetdefaultDialog: String
        get() = TODO("Not yet implemented")

    override val settingPauseIfSomeoneLeftTitle = "如果有人离开，暂停"

    override val settingPauseIfSomeoneLeftSummary = "如果你想要播放暂停/停止，如果有人离开房间，而你正在观看，启用此功能."

    override val settingWarnFileMismatchTitle = "播放的视频文件不匹配时警告"

    override val settingWarnFileMismatchSummary = "默认为开启. 如果加载的文件与组中的用户不同(根据名称、持续时间或大小，而不是全部)，则会发出警告。."

    override val settingFileinfoBehaviourNameTitle = "是否共享文件名"

    override val settingFileinfoBehaviourNameSummary = "选择将添加的媒体文件名共享其他用户."

    override val settingFileinfoBehaviourSizeTitle = "是否共享文件大小"

    override val settingFileinfoBehaviourSizeSummary = "选择向其他用户共享显示添加的媒体文件大小."

    override val uisettingApply = "应用"

    override val uisettingTimestampSummary = "禁用此选项可以隐藏聊天消息开头的时间戳."

    override val uisettingTimestampTitle = "是否开启时间戳"
    override val uisettingMsgoutlineSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingMsgoutlineTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingMsgshadowSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingMsgshadowTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingMsgboxactionSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingMsgboxactionTitle: String
        get() = TODO("Not yet implemented")

    override val uisettingOverviewAlphaSummary = "默认值为30(几乎透明), 如果你想增加房间细节面板的不透明度，请更改此选项."

    override val uisettingOverviewAlphaTitle = "房间细节背景不透明度"

    override val uisettingMessageryAlphaSummary = "默认为 0 (透明的). 最大为 255 (100% 不透明). 通过使背景更加不透明来增加消息的可读性."

    override val uisettingMessageryAlphaTitle = "消息不透明度"

    override val uisettingMsgsizeSummary = "更改消息文本大小.默认的是11."

    override val uisettingMsgsizeTitle = "消息字体大小"

    override val uisettingMsgcountSummary = "默认为12. 将消息数计数限制为此值."

    override val uisettingMsgcountTitle = "消息最大数量"

    override val uisettingMsglifeSummary = "当收到聊天信息或房间信息时，它将在下面设置的时间内慢慢消失."

    override val uisettingMsglifeTitle = "聊天消息显示时长"

    override val uisettingTimestampColorSummary = "自定义消息时间戳的文本颜色(默认为粉红色)"

    override val uisettingTimestampColorTitle = "短信时间戳文字颜色"

    override val uisettingSelfColorSummary = "自定义名称标签的文本颜色(默认为红色)"

    override val uisettingSelfColorTitle = "自我标签颜色"

    override val uisettingFriendColorSummary = "自定义朋友姓名标签的文本颜色(默认为蓝色)"

    override val uisettingFriendColorTitle = "朋友标签文字颜色"

    override val uisettingSystemColorSummary = "自定义系统房间信息的文字颜色(默认为白色)"

    override val uisettingSystemColorTitle = "系统消息文本颜色"

    override val uisettingHumanColorSummary = "自定义用户消息的文本颜色(默认为白色)"

    override val uisettingHumanColorTitle = "用户信息文字颜色"
    override val uisettingErrorColorSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingErrorColorTitle: String
        get() = TODO("Not yet implemented")

    override val uisettingResetdefaultSummary = "将上面所有设置重置为默认设置."

    override val uisettingResetdefaultTitle = "重置为默认设置"

    override val settingFileinfoBehaviorA = "反馈信息"

    override val settingFileinfoBehaviorB = "发送方式"

    override val settingFileinfoBehaviorC = "取消发送"
    override val settingNightModeA: String
        get() = TODO("Not yet implemented")
    override val settingNightModeB: String
        get() = TODO("Not yet implemented")
    override val settingNightModeC: String
        get() = TODO("Not yet implemented")
    override val en: String
        get() = TODO("Not yet implemented")
    override val ar: String
        get() = TODO("Not yet implemented")
    override val zh: String
        get() = TODO("Not yet implemented")
    override val fr: String
        get() = TODO("Not yet implemented")
}