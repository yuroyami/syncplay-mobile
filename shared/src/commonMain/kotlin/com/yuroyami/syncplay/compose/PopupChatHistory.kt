package com.yuroyami.syncplay.compose

object PopupChatHistory {

    /*
    @Composable
    fun ChatHistoryPopup(visibilityState: MutableState<Boolean>) {
        val colorTimestamp =
            DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP, Paletting.MSG_TIMESTAMP.toArgb())
                .collectAsState(initial = Paletting.MSG_TIMESTAMP.toArgb())
        val colorSelftag = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_SELFTAG, Paletting.MSG_SELF_TAG.toArgb())
            .collectAsState(initial = Paletting.MSG_SELF_TAG.toArgb())
        val colorFriendtag =
            DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG, Paletting.MSG_FRIEND_TAG.toArgb())
                .collectAsState(initial = Paletting.MSG_FRIEND_TAG.toArgb())
        val colorSystem = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG, Paletting.MSG_SYSTEM.toArgb())
            .collectAsState(initial = Paletting.MSG_SYSTEM.toArgb())
        val colorUserchat = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_USERMSG, Paletting.MSG_CHAT.toArgb())
            .collectAsState(initial = Paletting.MSG_CHAT.toArgb())
        val colorError = DataStoreKeys.DATASTORE_INROOM_PREFERENCES.ds().intFlow(DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG, Paletting.MSG_ERROR.toArgb())
            .collectAsState(initial = Paletting.MSG_ERROR.toArgb())
        val msgs = remember { p.session.messageSequence }

        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.7f,
            heightPercent = 0.8f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {

                val (title, messages, button) = createRefs()

                /* The title */
                FancyText2(
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top, 12.dp)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                    },
                    string = "Chat History",
                    solid = Color.Black,
                    size = 18f,
                    font = Font(R.font.directive4bold)
                )

                /* The actual messages */
                LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier
                        .background(Color(50, 50, 50, 50))
                        .constrainAs(messages) {
                            top.linkTo(title.bottom, 8.dp)
                            absoluteLeft.linkTo(parent.absoluteLeft)
                            absoluteRight.linkTo(parent.absoluteRight)
                            bottom.linkTo(button.top, 12.dp)
                            width = Dimension.percent(0.9f)
                            height = Dimension.fillToConstraints
                        }
                ) {
                    //TODO: Show errors in red
                    items(msgs) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp,
                            text = it.factorize(
                                timestampColor = Color(colorTimestamp.value),
                                selftagColor = Color(colorSelftag.value),
                                friendtagColor = Color(colorFriendtag.value),
                                systemmsgColor = Color(colorSystem.value),
                                usermsgColor = Color(colorUserchat.value),
                                errormsgColor = Color(colorError.value),
                                includeTimestamp = true
                            ),
                            fontSize = 10.sp
                        )
                    }
                }

                /* Exit button */
                Button(
                    modifier = Modifier.constrainAs(button) {
                        bottom.linkTo(parent.bottom, 4.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                        width = Dimension.wrapContent
                    },
                    onClick = {
                        visibilityState.value = false
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Close, "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.close), fontSize = 14.sp)
                }

            }
        }
    }

     */
}