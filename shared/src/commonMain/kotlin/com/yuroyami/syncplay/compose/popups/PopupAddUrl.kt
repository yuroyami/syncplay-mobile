package com.yuroyami.syncplay.compose.popups

object PopupAddUrl {

    /*
    @Composable
    fun AddUrlPopup(visibilityState: MutableState<Boolean>) {
        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.8f,
            heightPercent = 0.85f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            val clipboardManager: ClipboardManager = LocalClipboardManager.current

            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {

                val (title, soustitre, urlbox, buttons) = createRefs()

                /* The title */
                FancyText2(
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top, 12.dp)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                    },
                    string = "Load media from URL",
                    solid = Color.Black,
                    size = 18f,
                    font = Font(R.font.directive4bold)
                )

                /* Title's subtext */
                Text(
                    text = "Make sure to provide direct links (for example: www.example.com/video.mp4). YouTube and other media streaming services are not supported yet.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily(Font(R.font.inter)),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier = Modifier.constrainAs(soustitre) {
                        top.linkTo(title.bottom, 6.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                        width = Dimension.percent(0.6f)
                    })

                /* The URL input box */
                val url = remember { mutableStateOf("") }
                TextField(
                    modifier = Modifier.constrainAs(urlbox) {
                        top.linkTo(soustitre.bottom, 8.dp)
                        absoluteLeft.linkTo(parent.absoluteLeft)
                        absoluteRight.linkTo(parent.absoluteRight)
                        bottom.linkTo(buttons.top, 12.dp)
                        width = Dimension.percent(0.9f)
                        height = Dimension.wrapContent
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    value = url.value,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.DarkGray,
                        unfocusedContainerColor = Color.DarkGray,
                        disabledContainerColor = Color.DarkGray,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            url.value = clipboardManager.getText().toString()
                            toasty("Pasted clipboard content")
                        }) {
                            Icon(imageVector = Icons.Filled.ContentPaste, "", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Link, "", tint = MaterialTheme.colorScheme.primary)
                    },
                    onValueChange = { url.value = it },
                    textStyle = TextStyle(
                        brush = Brush.linearGradient(
                            colors = Paletting.SP_GRADIENT
                        ),
                        fontFamily = FontFamily(Font(R.font.inter)),
                        fontSize = 16.sp,
                    ),
                    label = {
                        Text("URL Address", color = Color.Gray)
                    }
                )

                /* Ok button */
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(width = 1.dp, color = Color.Black),
                    modifier = Modifier.constrainAs(buttons) {
                        bottom.linkTo(parent.bottom, 4.dp)
                        end.linkTo(parent.end, 12.dp)
                        start.linkTo(parent.start, 12.dp)
                        width = Dimension.wrapContent
                    },
                    onClick = {
                        visibilityState.value = false

                        if (url.value.trim().isNotBlank()) {
                            player?.injectVideo(this@AddUrlPopup, url.value.trim().toUri(), isUrl = true)
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