package ru.sberdevices.telegramcalls.vendor.notifications.data

import org.telegram.messenger.NotificationCenter

/**
 * Перечисление возможных типов уведомлений в Телеграме, для удобства логирования. В оригинальном
 * клиенте невозможно определить тип по айди, тк айди это числовые значения и не имеют названий.
 * Чтобы не делать бессмысленный upperscale первой буквы всех имен нотификаций, названия типов с
 * маленькой буквы
 *
 * @author Ирина Карпенко on 19.10.2021
 */
enum class Notification(val id: Int) {
    didReceiveNewMessages(id=NotificationCenter.didReceiveNewMessages),
    updateInterfaces(id=NotificationCenter.updateInterfaces),
    dialogsNeedReload(id=NotificationCenter.dialogsNeedReload),
    closeChats(id=NotificationCenter.closeChats),
    messagesDeleted(id=NotificationCenter.messagesDeleted),
    historyCleared(id=NotificationCenter.historyCleared),
    messagesRead(id=NotificationCenter.messagesRead),
    threadMessagesRead(id=NotificationCenter.threadMessagesRead),
    commentsRead(id=NotificationCenter.commentsRead),
    changeRepliesCounter(id=NotificationCenter.changeRepliesCounter),
    messagesDidLoad(id=NotificationCenter.messagesDidLoad),
    messagesDidLoadWithoutProcess(id=NotificationCenter.messagesDidLoadWithoutProcess),
    loadingMessagesFailed(id=NotificationCenter.loadingMessagesFailed),
    messageReceivedByAck(id=NotificationCenter.messageReceivedByAck),
    messageReceivedByServer(id=NotificationCenter.messageReceivedByServer),
    messageSendError(id=NotificationCenter.messageSendError),
    contactsDidLoad(id=NotificationCenter.contactsDidLoad),
    contactsImported(id=NotificationCenter.contactsImported),
    hasNewContactsToImport(id=NotificationCenter.hasNewContactsToImport),
    chatDidCreated(id=NotificationCenter.chatDidCreated),
    chatDidFailCreate(id=NotificationCenter.chatDidFailCreate),
    chatInfoDidLoad(id=NotificationCenter.chatInfoDidLoad),
    chatInfoCantLoad(id=NotificationCenter.chatInfoCantLoad),
    mediaDidLoad(id=NotificationCenter.mediaDidLoad),
    mediaCountDidLoad(id=NotificationCenter.mediaCountDidLoad),
    mediaCountsDidLoad(id=NotificationCenter.mediaCountsDidLoad),
    encryptedChatUpdated(id=NotificationCenter.encryptedChatUpdated),
    messagesReadEncrypted(id=NotificationCenter.messagesReadEncrypted),
    encryptedChatCreated(id=NotificationCenter.encryptedChatCreated),
    dialogPhotosLoaded(id=NotificationCenter.dialogPhotosLoaded),
    reloadDialogPhotos(id=NotificationCenter.reloadDialogPhotos),
    folderBecomeEmpty(id=NotificationCenter.folderBecomeEmpty),
    removeAllMessagesFromDialog(id=NotificationCenter.removeAllMessagesFromDialog),
    notificationsSettingsUpdated(id=NotificationCenter.notificationsSettingsUpdated),
    blockedUsersDidLoad(id=NotificationCenter.blockedUsersDidLoad),
    openedChatChanged(id=NotificationCenter.openedChatChanged),
    didCreatedNewDeleteTask(id=NotificationCenter.didCreatedNewDeleteTask),
    mainUserInfoChanged(id=NotificationCenter.mainUserInfoChanged),
    privacyRulesUpdated(id=NotificationCenter.privacyRulesUpdated),
    updateMessageMedia(id=NotificationCenter.updateMessageMedia),
    replaceMessagesObjects(id=NotificationCenter.replaceMessagesObjects),
    didSetPasscode(id=NotificationCenter.didSetPasscode),
    twoStepPasswordChanged(id=NotificationCenter.twoStepPasswordChanged),
    didSetOrRemoveTwoStepPassword(id=NotificationCenter.didSetOrRemoveTwoStepPassword),
    didRemoveTwoStepPassword(id=NotificationCenter.didRemoveTwoStepPassword),
    replyMessagesDidLoad(id=NotificationCenter.replyMessagesDidLoad),
    didLoadPinnedMessages(id=NotificationCenter.didLoadPinnedMessages),
    newSessionReceived(id=NotificationCenter.newSessionReceived),
    didReceivedWebpages(id=NotificationCenter.didReceivedWebpages),
    didReceivedWebpagesInUpdates(id=NotificationCenter.didReceivedWebpagesInUpdates),
    stickersDidLoad(id=NotificationCenter.stickersDidLoad),
    diceStickersDidLoad(id=NotificationCenter.diceStickersDidLoad),
    featuredStickersDidLoad(id=NotificationCenter.featuredStickersDidLoad),
    groupStickersDidLoad(id=NotificationCenter.groupStickersDidLoad),
    messagesReadContent(id=NotificationCenter.messagesReadContent),
    botInfoDidLoad(id=NotificationCenter.botInfoDidLoad),
    userInfoDidLoad(id=NotificationCenter.userInfoDidLoad),
    pinnedInfoDidLoad(id=NotificationCenter.pinnedInfoDidLoad),
    botKeyboardDidLoad(id=NotificationCenter.botKeyboardDidLoad),
    chatSearchResultsAvailable(id=NotificationCenter.chatSearchResultsAvailable),
    chatSearchResultsLoading(id=NotificationCenter.chatSearchResultsLoading),
    musicDidLoad(id=NotificationCenter.musicDidLoad),
    moreMusicDidLoad(id=NotificationCenter.moreMusicDidLoad),
    needShowAlert(id=NotificationCenter.needShowAlert),
    needShowPlayServicesAlert(id=NotificationCenter.needShowPlayServicesAlert),
    didUpdateMessagesViews(id=NotificationCenter.didUpdateMessagesViews),
    needReloadRecentDialogsSearch(id=NotificationCenter.needReloadRecentDialogsSearch),
    peerSettingsDidLoad(id=NotificationCenter.peerSettingsDidLoad),
    wasUnableToFindCurrentLocation(id=NotificationCenter.wasUnableToFindCurrentLocation),
    reloadHints(id=NotificationCenter.reloadHints),
    reloadInlineHints(id=NotificationCenter.reloadInlineHints),
    newDraftReceived(id=NotificationCenter.newDraftReceived),
    recentDocumentsDidLoad(id=NotificationCenter.recentDocumentsDidLoad),
    needAddArchivedStickers(id=NotificationCenter.needAddArchivedStickers),
    archivedStickersCountDidLoad(id=NotificationCenter.archivedStickersCountDidLoad),
    paymentFinished(id=NotificationCenter.paymentFinished),
    channelRightsUpdated(id=NotificationCenter.channelRightsUpdated),
    openArticle(id=NotificationCenter.openArticle),
    updateMentionsCount(id=NotificationCenter.updateMentionsCount),
    didUpdatePollResults(id=NotificationCenter.didUpdatePollResults),
    chatOnlineCountDidLoad(id=NotificationCenter.chatOnlineCountDidLoad),
    videoLoadingStateChanged(id=NotificationCenter.videoLoadingStateChanged),
    newPeopleNearbyAvailable(id=NotificationCenter.newPeopleNearbyAvailable),
    stopAllHeavyOperations(id=NotificationCenter.stopAllHeavyOperations),
    startAllHeavyOperations(id=NotificationCenter.startAllHeavyOperations),
    sendingMessagesChanged(id=NotificationCenter.sendingMessagesChanged),
    didUpdateReactions(id=NotificationCenter.didUpdateReactions),
    didVerifyMessagesStickers(id=NotificationCenter.didVerifyMessagesStickers),
    scheduledMessagesUpdated(id=NotificationCenter.scheduledMessagesUpdated),
    newSuggestionsAvailable(id=NotificationCenter.newSuggestionsAvailable),
    didLoadChatInviter(id=NotificationCenter.didLoadChatInviter),
    didLoadChatAdmins(id=NotificationCenter.didLoadChatAdmins),
    historyImportProgressChanged(id=NotificationCenter.historyImportProgressChanged),
    dialogDeleted(id=NotificationCenter.dialogDeleted),

    walletPendingTransactionsChanged(id=NotificationCenter.walletPendingTransactionsChanged),
    walletSyncProgressChanged(id=NotificationCenter.walletSyncProgressChanged),

    httpFileDidLoad(id=NotificationCenter.httpFileDidLoad),
    httpFileDidFailedLoad(id=NotificationCenter.httpFileDidFailedLoad),

    didUpdateConnectionState(id=NotificationCenter.didUpdateConnectionState),

    FileDidUpload(id=NotificationCenter.fileUploaded),
    FileDidFailUpload(id=NotificationCenter.fileUploadFailed),
    FileUploadProgressChanged(id=NotificationCenter.fileUploadProgressChanged),
    FileLoadProgressChanged(id=NotificationCenter.fileLoadProgressChanged),
    fileDidLoad(id=NotificationCenter.fileLoaded),
    fileDidFailToLoad(id=NotificationCenter.fileLoadFailed),
    filePreparingStarted(id=NotificationCenter.filePreparingStarted),
    fileNewChunkAvailable(id=NotificationCenter.fileNewChunkAvailable),
    filePreparingFailed(id=NotificationCenter.filePreparingFailed),

    dialogsUnreadCounterChanged(id=NotificationCenter.dialogsUnreadCounterChanged),

    messagePlayingProgressDidChanged(id=NotificationCenter.messagePlayingProgressDidChanged),
    messagePlayingDidReset(id=NotificationCenter.messagePlayingDidReset),
    messagePlayingPlayStateChanged(id=NotificationCenter.messagePlayingPlayStateChanged),
    messagePlayingDidStart(id=NotificationCenter.messagePlayingDidStart),
    messagePlayingDidSeek(id=NotificationCenter.messagePlayingDidSeek),
    messagePlayingGoingToStop(id=NotificationCenter.messagePlayingGoingToStop),
    recordProgressChanged(id=NotificationCenter.recordProgressChanged),
    recordStarted(id=NotificationCenter.recordStarted),
    recordStartError(id=NotificationCenter.recordStartError),
    recordStopped(id=NotificationCenter.recordStopped),
    screenshotTook(id=NotificationCenter.screenshotTook),
    albumsDidLoad(id=NotificationCenter.albumsDidLoad),
    audioDidSent(id=NotificationCenter.audioDidSent),
    audioRecordTooShort(id=NotificationCenter.audioRecordTooShort),
    audioRouteChanged(id=NotificationCenter.audioRouteChanged),

    didStartedCall(id=NotificationCenter.didStartedCall),
    groupCallUpdated(id=NotificationCenter.groupCallUpdated),
    applyGroupCallVisibleParticipants(id=NotificationCenter.applyGroupCallVisibleParticipants),
    groupCallTypingsUpdated(id=NotificationCenter.groupCallTypingsUpdated),
    didEndCall(id=NotificationCenter.didEndCall),
    closeInCallActivity(id=NotificationCenter.closeInCallActivity),
    groupCallVisibilityChanged(id=NotificationCenter.groupCallVisibilityChanged),

    appDidLogout(id=NotificationCenter.appDidLogout),

    configLoaded(id=NotificationCenter.configLoaded),

    needDeleteDialog(id=NotificationCenter.needDeleteDialog),

    newEmojiSuggestionsAvailable(id=NotificationCenter.newEmojiSuggestionsAvailable),

    themeUploadedToServer(id=NotificationCenter.themeUploadedToServer),
    themeUploadError(id=NotificationCenter.themeUploadError),

    dialogFiltersUpdated(id=NotificationCenter.dialogFiltersUpdated),
    filterSettingsUpdated(id=NotificationCenter.filterSettingsUpdated),
    suggestedFiltersLoaded(id=NotificationCenter.suggestedFiltersLoaded),

    //global
    pushMessagesUpdated(id=NotificationCenter.pushMessagesUpdated),
    wallpapersDidLoad(id=NotificationCenter.wallpapersDidLoad),
    wallpapersNeedReload(id=NotificationCenter.wallpapersNeedReload),
    didReceiveSmsCode(id=NotificationCenter.didReceiveSmsCode),
    didReceiveCall(id=NotificationCenter.didReceiveCall),
    emojiDidLoad(id=NotificationCenter.emojiLoaded),
    closeOtherAppActivities(id=NotificationCenter.closeOtherAppActivities),
    cameraInitied(id=NotificationCenter.cameraInitied),
    didReplacedPhotoInMemCache(id=NotificationCenter.didReplacedPhotoInMemCache),
    didSetNewTheme(id=NotificationCenter.didSetNewTheme),
    themeListUpdated(id=NotificationCenter.themeListUpdated),
    didApplyNewTheme(id=NotificationCenter.didApplyNewTheme),
    themeAccentListUpdated(id=NotificationCenter.themeAccentListUpdated),
    needCheckSystemBarColors(id=NotificationCenter.needCheckSystemBarColors),
    needShareTheme(id=NotificationCenter.needShareTheme),
    needSetDayNightTheme(id=NotificationCenter.needSetDayNightTheme),
    goingToPreviewTheme(id=NotificationCenter.goingToPreviewTheme),
    locationPermissionGranted(id=NotificationCenter.locationPermissionGranted),
    reloadInterface(id=NotificationCenter.reloadInterface),
    suggestedLangpack(id=NotificationCenter.suggestedLangpack),
    didSetNewWallpapper(id=NotificationCenter.didSetNewWallpapper),
    proxySettingsChanged(id=NotificationCenter.proxySettingsChanged),
    proxyCheckDone(id=NotificationCenter.proxyCheckDone),
    liveLocationsChanged(id=NotificationCenter.liveLocationsChanged),
    newLocationAvailable(id=NotificationCenter.newLocationAvailable),
    liveLocationsCacheChanged(id=NotificationCenter.liveLocationsCacheChanged),
    notificationsCountUpdated(id=NotificationCenter.notificationsCountUpdated),
    playerDidStartPlaying(id=NotificationCenter.playerDidStartPlaying),
    closeSearchByActiveAction(id=NotificationCenter.closeSearchByActiveAction),
    messagePlayingSpeedChanged(id=NotificationCenter.messagePlayingSpeedChanged),
    screenStateChanged(id=NotificationCenter.screenStateChanged),
    didClearDatabase(id=NotificationCenter.didClearDatabase),
    voipServiceCreated(id=NotificationCenter.voipServiceCreated),
    webRtcMicAmplitudeEvent(id=NotificationCenter.webRtcMicAmplitudeEvent),
    webRtcSpeakerAmplitudeEvent(id=NotificationCenter.webRtcSpeakerAmplitudeEvent),
    showBulletin(id=NotificationCenter.showBulletin),

    SbdvOnUserAvatarChanged(id=NotificationCenter.sbdv_onUserAvatarChanged),
    SbdvOnUserNameChanged(id=NotificationCenter.sbdv_onUserNameChanged),
    SbdvOnUserAdded(id=NotificationCenter.sbdv_onUserAdded),
    SbdvOnUserRemoved(id=NotificationCenter.sbdv_onUserRemoved);

    companion object {
        @JvmStatic
        fun get(id: Int): Notification? {
            return values().find { it.id == id }
        }
    }
}