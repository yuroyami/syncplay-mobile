package com.yuroyami.syncplay.compose.popups

object PopupSeekToPosition {


    /*
    @Composable
    fun WatchActivity.SeekToPositionPopup(visibilityState: MutableState<Boolean>) {
        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.6f,
            heightPercent = 0.9f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            val focusManager = LocalFocusManager.current

            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {

                val (title, soustitre, boxes, button) = createRefs()

                /* The title */
                FancyText2(
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top, 12.dp)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                    },
                    string = "Seek to Precise Position",
                    solid = Color.Black,
                    size = 18f,
                    font = Font(R.font.directive4bold)
                )

                /* Title's subtext */
                Text(
                    text = "Hours:Minutes:Seconds",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(Font(R.font.inter)),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier = Modifier.constrainAs(soustitre) {
                        top.linkTo(title.bottom, 6.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                        width = Dimension.wrapContent
                    })

                /* The boxes row */
                val hours = remember { mutableStateOf("") }
                val minutes = remember { mutableStateOf("") }
                val seconds = remember { mutableStateOf("") }

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.constrainAs(boxes) {
                        top.linkTo(soustitre.bottom, 8.dp)
                        absoluteLeft.linkTo(parent.absoluteLeft)
                        absoluteRight.linkTo(parent.absoluteRight)
                        bottom.linkTo(button.top, 12.dp)
                        width = Dimension.percent(0.9f)
                        height = Dimension.wrapContent
                    }) {
                    TextField(
                        modifier = Modifier.width(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        value = hours.value,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.moveFocus(FocusDirection.Next)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.DarkGray,
                            unfocusedContainerColor = Color.DarkGray,
                            disabledContainerColor = Color.DarkGray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        onValueChange = { hours.value = it },
                        textStyle = TextStyle(
                            brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                            fontFamily = FontFamily(Font(R.font.inter)),
                            fontSize = 16.sp,
                        ),
                        label = { Text("HH", color = Color.Gray) }
                    )

                    Spacer(Modifier.width(12.dp))


                    TextField(
                        modifier = Modifier.width(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        value = minutes.value,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.moveFocus(FocusDirection.Next)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.DarkGray,
                            unfocusedContainerColor = Color.DarkGray,
                            disabledContainerColor = Color.DarkGray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        onValueChange = { minutes.value = it },
                        textStyle = TextStyle(
                            brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                            fontFamily = FontFamily(Font(R.font.inter)),
                            fontSize = 16.sp,
                        ),
                        label = { Text("MM", color = Color.Gray) }
                    )

                    Spacer(Modifier.width(12.dp))

                    TextField(
                        modifier = Modifier.width(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        value = seconds.value,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.moveFocus(FocusDirection.Next)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.DarkGray,
                            unfocusedContainerColor = Color.DarkGray,
                            disabledContainerColor = Color.DarkGray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        onValueChange = { seconds.value = it },
                        textStyle = TextStyle(
                            brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                            fontFamily = FontFamily(Font(R.font.inter)),
                            fontSize = 16.sp,
                        ),
                        label = { Text("ss", color = Color.Gray) }
                    )
                }

                /* Ok button */
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(width = 1.dp, color = Color.Black),
                    modifier = Modifier.constrainAs(button) {
                        bottom.linkTo(parent.bottom, 4.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                        width = Dimension.wrapContent
                    },
                    onClick = {
                        visibilityState.value = false

                        var ss = seconds.value.toLongOrNull() ?: 0
                        var mm = minutes.value.toLongOrNull() ?: 0
                        val hh = hours.value.toLongOrNull() ?: 0

                        if (ss >= 60) ss = 59
                        if (mm >= 60) mm = 59

                        lifecycleScope.launch(Dispatchers.Main) {
                            val ssMs = ss * 1000
                            val mmMs = mm * 60 * 1000
                            val hhMs = hh * 3600 * 1000
                            val result = ssMs + mmMs + hhMs

                            if (isSoloMode()) {
                                if (player == null) return@launch
                                seeks.add(Pair(player!!.getPositionMs() ?: 0L , result))
                            }

                            player?.seekTo(result)

                            toasty("Seeking to ${timeStamper(result)}")
                        }

                    },
                ) {
                    Icon(imageVector = Icons.Filled.Done, "")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.done), fontSize = 14.sp)
                }
            }
        }
    }
     */
}