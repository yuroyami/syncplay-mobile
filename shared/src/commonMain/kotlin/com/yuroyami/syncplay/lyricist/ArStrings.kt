package com.yuroyami.syncplay.lyricist

import com.yuroyami.syncplay.utils.format

val ArStrings = object : Strings {
      override val disconnectedNoticeHeader = "انقطع الاتصال"

    override val connectUsernameA = "اكتب اسم مستخدمك:"

    override val yes = "نعم"

    override val no = "لا"

    override val okay = "حسناً"

    override val cancel = "إلـغاء"

    override val dontShowAgain = "لا تخبرني مجدداً"

    override val play = "تشغيل"

    override val pause = "إيقاف"

    override val delete = "حذف"

    override val tabConnect = "اتصال"

    override val tabSettings = "إعدادات"

    override val tabAbout = "حول"

    override val connectUsernameB = "اسم المستخدم"

    override val connectUsernameC = "اسمٌ من اختيارك"

    override val connectRoomnameA = "أدخِل اسم الغرفة :"

    override val connectRoomnameB = "اسم الغرفة"

    override val connectRoomnameC = "الغرفة التي تشاهد مع أصدقائك فيها"

    override val connectServerA = "اختر سيرفر Syncplay :"

    override val connectServerB = "عنوان السيرفر"

    override val connectServerC = "تأكد من أنك و أصدقاؤك في نفس السيرفر"

    override val connectButton = "الدخول إلى الغرفة"

    override val connectFootnote = "تطبيق الأندرويد غير الرسمي لـSyncplay"

    override val connectUsernameEmptyError = "اسم المستخدم لا يجب أن يكون فارغاً"

    override val connectRoomnameEmptyError = "اسم الغرفة لا يجب أن يكون فارغاً"

    override val connectEnterCustomServer = "أدخل سيرفر مميز"

    override val connectCustomServerPassword = "كلمة السر (إن تواجدت)"

    override val connectPort = "منفذ"

    override val roomStartupHint = "اضغط على الزر الذي يشبه الشكل أسفله لإدخال فيديو جديد"

    override val roomSelectedVid = { p0: String -> 
        "تم اختيار ملف الفيديو : %s"
            .format(p0)
    }

    override val roomSelectedSub = { p0: String -> 
        "تم إدخال ملف الترجمة : %s"
            .format(p0)
    }

    override val roomSelectedSubError = "ملف الترجمة غير صالح. الامتدادات الصالحة هي : SSA, ASS, VTT, SRT, TTML"

    override val roomSubErrorLoadVidFirst = "أَدخل فيديو أولاً"

    override val roomTypeMessage = "اكتب رسالتك …"

    override val roomSendButton = "إرسال"

    override val roomReady = "مستعد"

    override val roomDetails = "إظهار التفاصيل"

    override val roomPingConnected = { p0: String -> 
        "متصل - تأخر: %s ميليثانية"
            .format(p0)
    }

    override val roomPingDisconnected = "غير متصل"

    override val roomOverflowSub = "إدخال ملف ترجمة…"

    override val roomOverflowCutout = "وضع توسيع ثلمة الكاميرا"

    override val roomOverflowFf = "أزرار التحرك السريع"

    override val roomOverflowMsghistory = "أرشيف الرسائل"

    override val roomOverflowSettings = "الإعدادات"

    override val disconnectedNoticeA = "انقطع الاتصال بالسيرفر بسبب مشكل في الشبكة"

    override val disconnectedNoticeB = "إعادة محاولة الاتصال…"

    override val settingRememberJoinInfoTitle = "تذكـّر معلومات الانضمام"

    override val settingDisplayLanguageTitle = "اللغة"

    override val settingRememberJoinInfoSummary = "مفعلة افتراضياً. هذا سيسمح لـSyncplay بالاحتفاظ بمعلومات انضمامك في المرة القادمة التي تفتح فيها التطبيق."

    override val settingDisplayLanguageSummry = "اختر لغتك المفضلة التي يظهر بـها Syncplay"

    override val settingDisplayLanguageToast = { p0: String -> 
        "تم تغيير اللغة إلى: %s. أعد تشغيل التطبيق لينطبق الإعداد تماماً."
            .format(p0)
    }

    override val settingUseBufferTitle = "استعمال أحجام عرض مؤقت خاصة"

    override val settingUseBufferSummary = "إذا لاحظتَ أن مشغل الفيديو يستغرق وقتاً طويلاً من أجل تغيير النقطة الزمنية ، يمكنك استعمال أحجام عرض مؤقت خاصة (لا ينصح بتشغيلها)"

    override val settingMaxBufferTitle = "حجم العرض المؤقت الأقصى الخاص"

    override val settingMaxBufferSummary = "القيمة الافتراضية هي 50 (50000 ميلي ثانية). هذه القيمة تحدد كم مدة الفيديو الأكبر التي ستعالج و تحمل مؤقتاً."

    override val settingMinBufferSummary = "القيمة الافتراضية هي 15 (15 الف ميلي ثانية). أخفِض هذه القيمة لبدء تشغيل الفيديو بشكل أسرع دون تحميل الكثير مسبقاً. قد يسبب هذا أعطاباً في المشغل أو التطبيق."

    override val settingMinBufferTitle = "حجم العرض المؤقت الأدنى الخاص"

    override val settingPlaybackBufferSummary = "القيمة الافتراضية هي 2.5 ثانية (2500 ميلي ثانية). هذه تمثل حجم العرض المؤقت لحظة بدء تشغيل الفيديو أو تغيير النقطة الزمنية."

    override val settingPlaybackBufferTitle = "حجم عرض بدء التشغيل المؤقت الخاص"

    override val settingReadyFirsthandTitle = "التحديد كـمستعد فورياً"

    override val settingReadyFirsthandSummary = "شغِّل هذا الخيار إذا أردت أن تُحدد مستعداً اوتوماتيكياً فورَ انضمامك لغرفةٍ مـا"

    override val settingRewindThresholdTitle = "أرجِعني للوراء إن كان أحدهم متأخراً بـ x ثانية"

    override val settingRewindThresholdSummary = "إذا ما كان أحدهم متأخراً بالقيمة المحددة هنا ، سيعود الفيديو خاصتك ليزامنه."

    override val uisettingSubtitleSizeTitle = "حجم الترجمة"

    override val uisettingSubtitleSizeSummary = "ستغير هذه القيمة حجم خط ترجمة الفيديوهات. القيمة الافتراضية هي 18."

    override val settingTlsTitle = "\" اتصال آمن (TLSv1,3) -متوفر قريباً- \""

    override val settingTlsSummary = "هذا سيخبر Syncplay من استعمال بروتوكول اتصال أكثر آماناً بدل جيوب اتصال عارية و بحتة. هذه الميزة غير مفعلة حالياً و سيتم إضافتها عن قريب."

    override val settingResetdefaultTitle = "استرداد الحالة الافتراضية للإعدادات"

    override val settingResetdefaultSummary = "إعادة كل شيء إلى قيمته الافتراضية."

    override val settingPauseIfSomeoneLeftTitle = "جـمـِّـد الفيديو إن غادر أحدهم"

    override val settingPauseIfSomeoneLeftSummary = "شغِّل هذه الخاصية إن أردتَ إيقاف الفيديو إذ ما غادر أحدهم الغرفة"

    override val settingWarnFileMismatchTitle = "إنـذار عن عدم توافق الملفات بين المستخدمين"

    override val settingWarnFileMismatchSummary = "مشغلة إفتراضاً. هاته الخاصية تنذرك إذا ما تواجدت اختلافات بين ملفات المستخدمين من حيث الاسم ، الحجم أو المدة (و ليس جميعهم معاً)"

    override val settingFileinfoBehaviourNameTitle = "إرسال معلومات اسم الملف"

    override val settingFileinfoBehaviourNameSummary = "اختر الطريقة التي تريد بها إظهار اسم الملف للمستخدمين الآخرين."

    override val settingFileinfoBehaviourSizeTitle = "إرسال معلومات حجم الملف"

    override val settingFileinfoBehaviourSizeSummary = "اختر الطريقة التي تريد بها إظهار حجم الملف للمستخدمين الآخرين."

    override val uisettingApply = "تطبيق"

    override val uisettingTimestampTitle = "الطوابع الزمنية للرسائل"

    override val uisettingTimestampSummary = "عطِّل هذه الميزة لإخفاء الطوابع الزمنية في بداية رسائل الغرفة."

    override val uisettingOverviewAlphaSummary = "القيمة الافتراضية هي 40(شبه شفاف). غير هذه القيمة لتغيير شفافية خلفية تفاصيل الغرفة."

    override val uisettingOverviewAlphaTitle = "شفافية خلفية تفاصيل الغرفة"

    override val uisettingMessageryAlphaTitle = "شفافية خلفية الرسائل"

    override val uisettingMessageryAlphaSummary = "افتراضياً قيمتها 40 (شبه شفاف). أقصى قيمة ممكنة هي 255 (غير شفاف تماماً). ارفع من هذه القيمة لخفض شفافية خلفية الرسائل من أجل جعلها أكثر قابلية للقراءة"

    override val uisettingMsgsizeSummary = "يغير هذا الإعداد من حجم خط الرسائل. افتراضياً 11."

    override val uisettingMsgsizeTitle = "حجم خط الرسائل"

    override val uisettingMsgcountSummary = "افتراضياً 12. يحدُّ هذا الإعداد من عدد الرسائل الممكن إظهارها في نفس الوقت."

    override val uisettingMsgcountTitle = "العدد الأقصى للرسائل"

    override val uisettingMsglifeTitle = "مدة عرض رسائل الدردشة"

    override val uisettingMsglifeSummary = "عند استلام رسالة غرفة أو دردشة ، ستبدأ بالتلاشي تدريجياً لمدة زمنية هي التي تحددها هنا."

    override val uisettingResetdefaultTitle = "استرداد الإعدادات الافتراضية"

    override val uisettingResetdefaultSummary = "إعادة كل الإعدادات أعلاه إلى حالتها الافتراضية."

    override val roomSharedPlaylist = "قائمة التشغيل المشتركة للغرفة"

    override val roomSharedPlaylistBrief = "أدخل ملفاً أو مجلداً لإرفاق أسماء الملفات إلى القائمة"

    override val roomSharedPlaylistUpdated = { p0: String -> 
        "%s جدّدَ القئمة"
            .format(p0)
    }

    override val roomSharedPlaylistChanged = { p0: String -> 
        "%s غيَّـر موضع القائمة"
            .format(p0)
    }

    override val roomSharedPlaylistNoDirectories = "لم تحدد أي مسارات ميديا لقوائم التشغيل المشتركة. يرجى إضافة الملف يدوياً."

    override val roomSharedPlaylistNotFound = "لم يستطع البرنامج إيجاد الملف الجاري تشغيله في القائمة."

    override val roomSharedPlaylistNotFeatured = "الغرفة أو السيرفر لا يدعمان خاصية قوائم التشغيل المشتركة."

    override val mediaDirectories = "مسارات الميديا لـقوائم التشغيل المشتركة"

    override val mediaDirectoriesBrief = "سيبحث التطبيق في مسارات الميديا التي تعينها هنا من أجل إيجاد اسم ملف في قائمة التشغيل. اتنبه أن العملية قد تطول إذا ما حددت مجلدات ذات ملفات كثيرة."

    override val mediaDirectoriesSettingSummary = "سيبحث التطبيق في مسارات الميديا التي تعينها هنا من أجل إيجاد اسم ملف في قائمة التشغيل."

    override val mediaDirectoriesSave = "حفظ و خروج"

    override val mediaDirectoriesClearAll = "مسح الكـل"

    override val mediaDirectoriesClearAllConfirm = "هل أنت متأكد من مسح القائمة ؟"

    override val mediaDirectoriesAddFolder = "إضافة مجلد"

    override val mediaDirectoriesDelete = "حذف من القائمة"

    override val mediaDirectoriesShowFullPath = "إظـهار المسـار الكامل"

    override val settingsCategGeneral = "إعدادات عـامة"

    override val settingsCategPlayer = "إعدادات المشغل"

    override val settingsCategRoom = "إعدادات الغرفة"

    override val settingsCategVideo = "إعدادات الفيديو"

    override val settingsCategMisc = "إعدادات مميزة"

    override val settingFileinfoBehaviorA = "إرسالها أصلية"

    override val settingFileinfoBehaviorB = "إرسالها مشفرة"

    override val settingFileinfoBehaviorC = "لا ترسل"

    override val en = "الانجليزية"

    override val ar = "العربية"

    override val roomEmptyMessageError = "اكتب شيئاً !"

    override val roomAttemptingConnect = "تتم محاولة الاتصال بـ %1s:%2s"

    override val roomConnectedToServer = "تم الاتصال بالسيرفر."

    override val roomConnectionFailed = "فشل الاتصال."

    override val roomAttemptingReconnection = "انقطع الاتصال بالسيرفر. يتم إعادة المحاولة…"

    override val roomGuyPlayed = { p0: String -> 
        "واصل %s التشغيل"
            .format(p0)
    }

    override val roomGuyPaused = { p0: String -> 
        "جمد %s التشغيل"
            .format(p0)
    }

    override val roomSeeked = "قفز %1s من %2s إلى %3s"

    override val roomRewinded = { p0: String -> 
        "الرجوع إلى الوراء بسبب الفرق الزمني مع %s"
            .format(p0)
    }

    override val roomGuyLeft = { p0: String -> 
        "غادر %s الغرفة"
            .format(p0)
    }

    override val roomGuyJoined = { p0: String -> 
        "انضم %s للغرفة"
            .format(p0)
    }

    override val roomIsplayingfile = "يلعب %1s الفيديو %2s ذو المدة (%3s) الآن"

    override val roomYouJoinedRoom = { p0: String -> 
        "لقد انضممت إلى الغرفة: %s"
            .format(p0)
    }

    override val roomDetailsFileProperties = "المدة: %1s - الحجم : %2s ميغا"

    override val roomDetailsNofileplayed = "(ما مـِن ملف جاري تشغيله)"

    override val roomDetailsCurrentRoom = { p0: String -> 
        "الغرفة الحـالية : %s"
            .format(p0)
    }

    override val roomSubTrackNotfound = "ما من ترجمة في الملف !"

    override val roomTrackTrack = "مقطع"

    override val roomSubTrackDisable = "تعطيل الترجمة"

    override val roomAudioTrackNotFound = "ما من صوت في الملف"

    override val roomAudioTrackChanged = { p0: String -> 
        "تم تغيير مقطع الصوت إلى : %s"
            .format(p0)
    }

    override val roomSubTrackChanged = { p0: String -> 
        "تم تغيير مقطع الترجمة إلى : %s"
            .format(p0)
    }

    override val roomScalingZoom = "وضع التكبير : زووم"

    override val roomScalingFillScreen = "وضع التكبير : ملء الشاشة"

    override val roomScalingFixedHeight = "وضع التكبير : طول ثابت"

    override val roomScalingFixedWidth = "وضع التكبير : عرض ثابت"

    override val roomScalingFitScreen = "وضع التكبير : ملاءمة الشاشة"

    override val uisettingHumanColorTitle = "لون خط رسائل المستخدمين"

    override val uisettingHumanColorSummary = "\"يعدل لون خط رسائل المستخدمين (اللون الافتراضي : أبيض) \""

    override val uisettingSystemColorTitle = "لون خـط رسائل النظام"

    override val uisettingSystemColorSummary = "يعدل لون خط رسائل نظام الغرفة التلقائية (أبيض افتراضيا)"

    override val uisettingFriendColorTitle = "لون خـط اسم الصديق"

    override val uisettingFriendColorSummary = "يعدل لون خط اسم الصديق (أزرق افتراضيا)"

    override val uisettingSelfColorTitle = "لون خط الاسم الشخصي"

    override val uisettingSelfColorSummary = "يعدل لون خط اسم الذات (أحمر افتراضياً)"

    override val uisettingTimestampColorTitle = "لون خط الطوابع الزمنية"

    override val uisettingTimestampColorSummary = "يعدل لون خط طوابع الزمن في الرسائل (وردي افتراضياً)"

    override val roomFileMismatchWarningCore = { p0: String -> 
        "ملفك مختلف عن ملف %s بالأوجـه التالية :"
            .format(p0)
    }

    override val roomFileMismatchWarningName = "\"الاسم. \""

    override val roomFileMismatchWarningDuration = "\"المدة. \""

    override val roomFileMismatchWarningSize = "\"الحجم. \""
}