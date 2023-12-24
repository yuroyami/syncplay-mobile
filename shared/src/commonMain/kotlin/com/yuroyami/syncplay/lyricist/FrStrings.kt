package com.yuroyami.syncplay.lyricist

import com.yuroyami.syncplay.utils.format

val FrStrings = object : Strings {
    override val yes = "Oui"

    override val no = "Non"

    override val okay = "D'accord"

    override val cancel = "Annuler"

    override val dontShowAgain = "Ne plus afficher ce message"

    override val play = "Lire"

    override val pause = "Pauser"

    override val delete = "Supprimer"
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

    override val tabConnect = "Connecter"

    override val tabSettings = "Réglages"

    override val tabAbout = "À propos"

    override val connectUsernameA = "Saisissez votre nom d'utilisateur:"

    override val connectUsernameB = "Nom d'utilisateur"

    override val connectUsernameC = "Un nom de votre choix"

    override val connectRoomnameA = "Insérer le nom du salon:"

    override val connectRoomnameB = "Nom du salon"

    override val connectRoomnameC = "\" \""

    override val connectServerA = "Selectionner un serveur:"

    override val connectServerB = "Addresse de serveur"

    override val connectServerC = "Assurer que vous et vos amis sont sur le même serveur"
    override val connectButtonJoin: String
        get() = TODO("Not yet implemented")

    override val connectButton = "Rejoindre / Créer le salon"
    override val connectButtonSaveshortcut: String
        get() = TODO("Not yet implemented")
    override val connectButtonCurrentEngine: (String) -> String
        get() = TODO("Not yet implemented")

    override val connectFootnote = "Client non-officiel de Syncplay pour Android"

    override val connectUsernameEmptyError = "Nom d'utilisateur ne doit pas être vide"

    override val connectRoomnameEmptyError = "Nom du salon no deoit pas être vide"
    override val connectAddressEmptyError: String
        get() = TODO("Not yet implemented")
    override val connectPortEmptyError: String
        get() = TODO("Not yet implemented")

    override val connectEnterCustomServer = "Entrer un serveur personnalisé"

    override val connectCustomServerPassword = "Mot de passe (s'il y a)"

    override val connectPort = "Port"
    override val connectNightmodeswitch: String
        get() = TODO("Not yet implemented")
    override val connectSolomode: String
        get() = TODO("Not yet implemented")

    override val roomStartupHint = "Insérer un nouveau clip de votre stockage en cliquant le bouton avec l'icon suivant"

    override val roomSelectedVid = { p0: String -> 
        "Fichier vidéo séléctionné: %s"
            .format(p0)
    }

    override val roomSelectedSub = { p0: String -> 
        "Fichier sous-titre chargé: %s"
            .format(p0)
    }

    override val roomSelectedSubError = "Fichier sous-tire invalide. Les formates supportées sont: 'SRT', 'ASS', 'SSA', 'VTT' et 'TTML'."

    override val roomSubErrorLoadVidFirst = "\"Vous devez d'abord charger un video \""

    override val roomTypeMessage = "Taper un message..."

    override val roomSendButton = "ENVOYER"

    override val roomReady = "Prêt"

    override val roomDetails = "Afficher les Détails"

    override val roomPingConnected = { p0: String -> 
        "Connecté - Ping: %s ms"
            .format(p0)
    }

    override val roomPingDisconnected = "DÉCONNECTÉ"

    override val roomOverflowSub = "Charger un fichier sous-titre..."

    override val roomOverflowCutout = "Mode Cut-out (Coupée)"

    override val roomOverflowFf = "Boutons rapide-seek"

    override val roomOverflowMsghistory = "Historique de messages"

    override val roomOverflowSettings = "Paramètres"

    override val roomEmptyMessageError = "Taper quelque chose !"

    override val roomAttemptingConnect = "Essayage de connecter à %1s:%2s"

    override val roomConnectedToServer = "Connecté au serveur."

    override val roomConnectionFailed = "Connexion a échoué."

    override val roomAttemptingReconnection = "Connexion au serveur a été perdu."

    override val roomGuyPlayed = { p0: String -> 
        "%s non suspendu."
            .format(p0)
    }

    override val roomGuyPaused = "%1s en pause (%2s)"

    override val roomSeeked = "%1s est passé de %2s à %3s"

    override val roomRewinded = { p0: String -> 
        "Rémonté du à la différence du temps avec %s"
            .format(p0)
    }

    override val roomGuyLeft = { p0: String -> 
        "%s est parti"
            .format(p0)
    }

    override val roomGuyJoined = { p0: String -> 
        "%s a rejoint la salle"
            .format(p0)
    }

    override val roomIsplayingfile = "%1s est en train de regarder '%2s' (%3s)"

    override val roomYouJoinedRoom = { p0: String -> 
        "Vous avez rejoints la salle: %s"
            .format(p0)
    }

    override val roomScalingFitScreen = "Redimensionnement: AJUSTER A L'ÉCRAN"

    override val roomScalingFixedWidth = "Redimensionnement: LARGEUR FIXÉE"

    override val roomScalingFixedHeight = "Redimensionnement: HAUTEUR FIXÉE"

    override val roomScalingFillScreen = "Redimensionnement: REMPLIR L'ÉCRAN"

    override val roomScalingZoom = "Redimensionnement: ZOOM"

    override val roomSubTrackChanged = { p0: String -> 
        "Piste sous-titre changée vers: %s"
            .format(p0)
    }

    override val roomAudioTrackChanged = { p0: String -> 
        "Piste audio changée vers: %s"
            .format(p0)
    }

    override val roomAudioTrackNotFound = "Pas d'audio !"

    override val roomSubTrackDisable = "Désactiver sous-titrage"

    override val roomTrackTrack = "Piste"

    override val roomSubTrackNotfound = "Pas de sous-titres !"

    override val roomDetailsCurrentRoom = { p0: String -> 
        "Salle actuelle: %s"
            .format(p0)
    }

    override val roomDetailsNofileplayed = "(Pas de fichier en lire)"

    override val roomDetailsFileProperties = "Duration: %1s - Taille: %2s MBs"

    override val disconnectedNoticeHeader = "DÉCONNECTÉ"

    override val disconnectedNoticeA = "Vous êtes déconnectés du serveur. C'est peut-être un problème de connexion internet ou le serveur dont vous essayez de connecter. Si le problème persiste, changez le serveur."

    override val disconnectedNoticeB = "Essayage de reconnexion."

    override val roomFileMismatchWarningCore = "Votre fichier diffère de la (des) manière(s) suivante(s):"

    override val roomFileMismatchWarningName = "Nom."

    override val roomFileMismatchWarningDuration = "Duration."

    override val roomFileMismatchWarningSize = "Taille."

    override val roomSharedPlaylist = "Listes de Lecture Partagées"

    override val roomSharedPlaylistBrief = "Importer un fichier ou un dossier pour inclure un (des) nom(s) de fichier sir la liste. Appuyer sur une ligne pour lire la fichier pour tout le monde."

    override val roomSharedPlaylistUpdated = { p0: String -> 
        "%s a renouvlé la liste de lecture"
            .format(p0)
    }

    override val roomSharedPlaylistChanged = { p0: String -> 
        "%s à changé la selection de la liste de lecture."
            .format(p0)
    }

    override val roomSharedPlaylistNoDirectories = "Aucun dossier média a été spécifié pour les listes de lecture. Charger les fichiers manuellement."

    override val roomSharedPlaylistNotFound = "Syncplay n'a pas trouvé le fichier ce qui est lu maintenent sur la liste dans tous les dossiers média spécifiés."

    override val roomSharedPlaylistNotFeatured = "La salle ne supporte pas les listes de lecture."
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

    override val roomButtonDescAspectRatio = "Rapport d'Aspect"

    override val roomButtonDescSharedPlaylist = "Liste de Lecture"

    override val roomButtonDescAudioTracks = "Pistes d'Audio"

    override val roomButtonDescSubtitleTracks = "Pistes de sous-titres"

    override val roomButtonDescRewind = "Rémonter"

    override val roomButtonDescToggle = "."

    override val roomButtonDescPlay = "Lire"

    override val roomButtonDescPause = "Pauser"

    override val roomButtonDescFfwd = "Rapide-seek"

    override val roomButtonDescAdd = "Ajouter un fichier média"

    override val roomButtonDescLock = "Serrure de Contrôle"

    override val roomButtonDescMore = "Plus"
    override val roomAddmediaOffline: String
        get() = TODO("Not yet implemented")
    override val roomAddmediaOnline: String
        get() = TODO("Not yet implemented")
    override val roomAddmediaOnlineUrl: String
        get() = TODO("Not yet implemented")

    override val mediaDirectories = "Dossiers média pour les listes de lecture"

    override val mediaDirectoriesBrief = "Syncplay va rechercher dans les dossiers média spécifiés ce-dessous pour trouver un nom de fichier sur les listes de lecture."

    override val mediaDirectoriesSettingSummary = "Syncplay va rechercher dans les dossiers média spécifiés ce-dessous pour trouver un nom de fichier sur les listes de lecture."

    override val mediaDirectoriesSave = "Sauvegarder et quitter"

    override val mediaDirectoriesClearAll = "Tous effacer"

    override val mediaDirectoriesClearAllConfirm = "Vous êtes sûr de tout effacer ?"

    override val mediaDirectoriesAddFolder = "Ajouter un dossier"

    override val mediaDirectoriesDelete = "Supprimer de la liste"

    override val mediaDirectoriesShowFullPath = "Afficher le chemin complet"

    override val settingsCategGeneral = "Paramètres Générales"

    override val settingsCategPlayer = "Paramètres de Lécteur"

    override val settingsCategRoom = "Paramètres du Salon"

    override val settingsCategVideo = "Paramètres de Video"

    override val settingsCategMisc = "Divers"
    override val settingNightModeTitle: String
        get() = TODO("Not yet implemented")
    override val settingNightModeSummary: String
        get() = TODO("Not yet implemented")

    override val settingRememberJoinInfoTitle = "Mémoriser la config"

    override val settingRememberJoinInfoSummary = "Activé par défault. Syncplay va sauvegarder le nom d'utilisateur, nom du salon et le serveur pour la prochaine fois vous connectez."

    override val settingDisplayLanguageTitle = "Langage"

    override val settingDisplayLanguageSummry = "Séléctionner une langue de votre choix."

    override val settingDisplayLanguageToast = "Langue changé. Relancer l'application pour établir le réglage complètement."
    override val settingAudioDefaultLanguageTitle: String
        get() = TODO("Not yet implemented")
    override val settingAudioDefaultLanguageSummry: String
        get() = TODO("Not yet implemented")
    override val settingCcDefaultLanguageTitle: String
        get() = TODO("Not yet implemented")
    override val settingCcDefaultLanguageSummry: String
        get() = TODO("Not yet implemented")

    override val settingUseBufferTitle = "Utiliser des Tailles Tampon Personnalisées"

    override val settingUseBufferSummary = "Si vous êtes pas convaincu avec les durations dont le lécteur prends pour charger un vidéo, vous pouvez manipuler cest values de tampon."

    override val settingMaxBufferTitle = "Taille Tampon Maximum"
    override val settingMaxBufferSummary: String
        get() = TODO("Not yet implemented")
    override val settingMinBufferSummary: String
        get() = TODO("Not yet implemented")
    override val settingMinBufferTitle: String
        get() = TODO("Not yet implemented")
    override val settingPlaybackBufferSummary: String
        get() = TODO("Not yet implemented")
    override val settingPlaybackBufferTitle: String
        get() = TODO("Not yet implemented")
    override val settingReadyFirsthandSummary: String
        get() = TODO("Not yet implemented")
    override val settingReadyFirsthandTitle: String
        get() = TODO("Not yet implemented")
    override val settingRewindThresholdSummary: String
        get() = TODO("Not yet implemented")
    override val settingRewindThresholdTitle: String
        get() = TODO("Not yet implemented")
    override val settingTlsSummary: String
        get() = TODO("Not yet implemented")
    override val settingTlsTitle: String
        get() = TODO("Not yet implemented")
    override val settingResetdefaultTitle: String
        get() = TODO("Not yet implemented")
    override val settingResetdefaultSummary: String
        get() = TODO("Not yet implemented")
    override val settingResetdefaultDialog: String
        get() = TODO("Not yet implemented")
    override val settingPauseIfSomeoneLeftTitle: String
        get() = TODO("Not yet implemented")
    override val settingPauseIfSomeoneLeftSummary: String
        get() = TODO("Not yet implemented")
    override val settingWarnFileMismatchTitle: String
        get() = TODO("Not yet implemented")
    override val settingWarnFileMismatchSummary: String
        get() = TODO("Not yet implemented")
    override val settingFileinfoBehaviourNameTitle: String
        get() = TODO("Not yet implemented")
    override val settingFileinfoBehaviourNameSummary: String
        get() = TODO("Not yet implemented")
    override val settingFileinfoBehaviourSizeTitle: String
        get() = TODO("Not yet implemented")
    override val settingFileinfoBehaviourSizeSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingApply: String
        get() = TODO("Not yet implemented")
    override val uisettingTimestampSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingTimestampTitle: String
        get() = TODO("Not yet implemented")
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
    override val uisettingOverviewAlphaSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingOverviewAlphaTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingMessageryAlphaSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingMessageryAlphaTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingMsgsizeSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingMsgsizeTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingMsgcountSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingMsgcountTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingMsglifeSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingMsglifeTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingTimestampColorSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingTimestampColorTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingSelfColorSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingSelfColorTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingFriendColorSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingFriendColorTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingSystemColorSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingSystemColorTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingHumanColorSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingHumanColorTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingErrorColorSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingErrorColorTitle: String
        get() = TODO("Not yet implemented")
    override val uisettingSubtitleSizeSummary: String
        get() = TODO("Not yet implemented")
    override val uisettingSubtitleSizeTitle: String
        get() = TODO("Not yet implemented")
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

    override val uisettingResetdefaultSummary = "Restaurer tout le réglage vers son état par défault"

    override val uisettingResetdefaultTitle = "Restaurer les paramètres défaults"

    override val settingFileinfoBehaviorA = "Envoyer brut"

    override val settingFileinfoBehaviorB = "Envoyer encrypté"

    override val settingFileinfoBehaviorC = "Ne pas envoyer"

    override val settingNightModeA: String
        get() = TODO("Not yet implemented")

    override val settingNightModeB: String
        get() = TODO("Not yet implemented")

    override val settingNightModeC: String
        get() = TODO("Not yet implemented")

    override val en = "Anglais"

    override val ar = "Arabe"

    override val zh = "Chinois"

    override val fr = "Français"
}