package org.telegram.ui.Components.voip;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BetterRatingView;
import org.telegram.ui.Components.JoinCallAlert;
import org.telegram.ui.Components.JoinCallByUrlAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.GroupCallActivity;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import ru.sberdevices.sbdv.SbdvServiceLocator;
import ru.sberdevices.sbdv.util.OnClickListenerWrapper;

public class VoIPHelper {
	private static final String TAG = "VoIPHelper";

	public static long lastCallTime = 0;

	private static final int VOIP_SUPPORT_ID = 4244000;
	private static final String LAST_VOIP_LOG_PATH = "/data/user/0/ru.sberdevices.telegramcalls/cache/voip_logs/last.log";
	private static final long THANKFUL_SCREEN_DISPLAYING_DURATION_MS = TimeUnit.SECONDS.toMillis(3);

	public static void startCall(TLRPC.User user, boolean videoCall, boolean canVideoCall, final Activity activity, TLRPC.UserFull userFull, AccountInstance accountInstance) {
		if (userFull != null && userFull.phone_calls_private) {
			new AlertDialog.Builder(activity)
					.setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
					.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable,
							ContactsController.formatName(user.first_name, user.last_name))))
					.setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
					.show();
			return;
		}

		int selectedAccountConnectionState = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
		boolean isConnected = selectedAccountConnectionState == ConnectionsManager.ConnectionStateConnected;
		boolean disableAlertIfUpdating = SbdvServiceLocator.getConfig().getDisableNoConnectionAlertDuringUpdating();
		boolean isUpdating = selectedAccountConnectionState == ConnectionsManager.ConnectionStateUpdating;
		boolean showNoConnectionAlert = !isConnected && (!disableAlertIfUpdating || !isUpdating);
		Log.d(TAG, "startCall(): connected: " + isConnected + ", updating: " + isUpdating + ", disable alert if updating: " + disableAlertIfUpdating + " => show no connection alert: " + showNoConnectionAlert);
		if (showNoConnectionAlert) {
			Log.d(TAG, "Selected account connection state: " + selectedAccountConnectionState);
			View dialogView = activity.getLayoutInflater().inflate(R.layout.sbdv_alert_dialog, null);
			AlertDialog alertDialog = new AlertDialog.Builder(activity)
					.setView(dialogView)
					.setTransparentBackground(true)
					.show();

			TextView title = dialogView.findViewById(R.id.alertTitle);
			TextView message = dialogView.findViewById(R.id.alertMessage);
			TextView positiveButton = dialogView.findViewById(R.id.positiveButton);
			title.setText(LocaleController.getString("VoipOfflineTitle", R.string.VoipOfflineTitle));
			message.setText(LocaleController.getString("VoipOffline", R.string.VoipOffline));
			positiveButton.setText(LocaleController.getString("OK", R.string.OK));
			positiveButton.setOnClickListener((View v) -> alertDialog.dismiss());
			return;
		}

		if (Build.VERSION.SDK_INT >= 23) {
			int code;
			ArrayList<String> permissions = new ArrayList<>();
			if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
				permissions.add(Manifest.permission.RECORD_AUDIO);
			}
			if (videoCall && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
				permissions.add(Manifest.permission.CAMERA);
			}
			if (permissions.isEmpty()) {
				initiateCall(user, null, null, videoCall, canVideoCall, false, null, activity, null, accountInstance);
			} else {
				activity.requestPermissions(permissions.toArray(new String[0]), videoCall ? 102 : 101);
			}
		} else {
			initiateCall(user, null, null, videoCall, canVideoCall, false, null, activity, null, accountInstance);
		}
	}

	public static void startCall(TLRPC.Chat chat, TLRPC.InputPeer peer, String hash, boolean createCall, Activity activity, BaseFragment fragment, AccountInstance accountInstance) {
		startCall(chat, peer, hash, createCall, null, activity, fragment, accountInstance);
	}

	public static void startCall(TLRPC.Chat chat, TLRPC.InputPeer peer, String hash, boolean createCall, Boolean checkJoiner, Activity activity, BaseFragment fragment, AccountInstance accountInstance) {
		if (activity == null) {
			return;
		}
		if (ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState() != ConnectionsManager.ConnectionStateConnected) {
			boolean isAirplaneMode = Settings.System.getInt(activity.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
			AlertDialog.Builder bldr = new AlertDialog.Builder(activity)
					.setTitle(isAirplaneMode ? LocaleController.getString("VoipOfflineAirplaneTitle", R.string.VoipOfflineAirplaneTitle) : LocaleController.getString("VoipOfflineTitle", R.string.VoipOfflineTitle))
					.setMessage(isAirplaneMode ? LocaleController.getString("VoipGroupOfflineAirplane", R.string.VoipGroupOfflineAirplane) : LocaleController.getString("VoipGroupOffline", R.string.VoipGroupOffline))
					.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
			if (isAirplaneMode) {
				final Intent settingsIntent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
				if (settingsIntent.resolveActivity(activity.getPackageManager()) != null) {
					bldr.setNeutralButton(LocaleController.getString("VoipOfflineOpenSettings", R.string.VoipOfflineOpenSettings), (dialog, which) -> activity.startActivity(settingsIntent));
				}
			}
			try {
				bldr.show();
			} catch (Exception e) {
				FileLog.e(e);
			}
			return;
		}

		if (Build.VERSION.SDK_INT >= 23) {
			ArrayList<String> permissions = new ArrayList<>();
			ChatObject.Call call = accountInstance.getMessagesController().getGroupCall(chat.id, false);
			if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && !(call != null && call.call.rtmp_stream)) {
				permissions.add(Manifest.permission.RECORD_AUDIO);
			}
			if (permissions.isEmpty()) {
				initiateCall(null, chat, hash, false, false, createCall, checkJoiner, activity, fragment, accountInstance);
			} else {
				activity.requestPermissions(permissions.toArray(new String[0]), 103);
			}
		} else {
			initiateCall(null, chat, hash, false, false, createCall, checkJoiner, activity, fragment, accountInstance);
		}
	}

	private static void initiateCall(TLRPC.User user, TLRPC.Chat chat, String hash, boolean videoCall, boolean canVideoCall, boolean createCall, Boolean checkJoiner, final Activity activity, BaseFragment fragment, AccountInstance accountInstance) {
		if (activity == null || user == null && chat == null) {
			return;
		}
		VoIPService voIPService = VoIPService.getSharedInstance();
		if (voIPService != null) {
			long newId = user != null ? user.id : -chat.id;
			long callerId = VoIPService.getSharedInstance().getCallerId();
			if (callerId != newId || voIPService.getAccount() != accountInstance.getCurrentAccount()) {
				String newName;
				String oldName;
				String key1;
				int key2;
				if (callerId > 0) {
					TLRPC.User callUser = voIPService.getUser();
					oldName = ContactsController.formatName(callUser.first_name, callUser.last_name);
					if (newId > 0) {
						key1 = "VoipOngoingAlert";
						key2 = R.string.VoipOngoingAlert;
					} else {
						key1 = "VoipOngoingAlert2";
						key2 = R.string.VoipOngoingAlert2;
					}
				} else {
					TLRPC.Chat callChat = voIPService.getChat();
					oldName = callChat.title;
					if (newId > 0) {
						key1 = "VoipOngoingChatAlert2";
						key2 = R.string.VoipOngoingChatAlert2;
					} else {
						key1 = "VoipOngoingChatAlert";
						key2 = R.string.VoipOngoingChatAlert;
					}
				}
				if (user != null) {
					newName = ContactsController.formatName(user.first_name, user.last_name);
				} else {
					newName = chat.title;
				}

				View dialogView = activity.getLayoutInflater().inflate(R.layout.sbdv_alert_dialog_canceled, null);
				Typeface sbdvTypeFace = Typeface.SANS_SERIF;
				AlertDialog.Builder builder = new AlertDialog.Builder(activity);
				AlertDialog dialog = builder
						.setView(dialogView)
						.show();

				TextView title = dialogView.findViewById(R.id.alertTitle);
				title.setTypeface(sbdvTypeFace);
				title.setText(callerId < 0 ? LocaleController.getString("VoipOngoingChatAlertTitle", R.string.VoipOngoingChatAlertTitle) : LocaleController.getString("VoipOngoingAlertTitle", R.string.VoipOngoingAlertTitle));

				TextView message = dialogView.findViewById(R.id.alertMessage);
				message.setTypeface(sbdvTypeFace);
				message.setText(AndroidUtilities.replaceTags(LocaleController.formatString(key1, key2, oldName, newName)));

				TextView positiveButton = dialogView.findViewById(R.id.positiveButton);
				positiveButton.setTypeface(sbdvTypeFace);
				positiveButton.setText(LocaleController.getString("OK", R.string.OK));
				positiveButton.setOnClickListener((View v) -> {
					if (VoIPService.getSharedInstance() != null) {
						VoIPService.getSharedInstance().hangUp(() -> {
							lastCallTime = 0;
							doInitiateCall(user, chat, hash, null, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, true, true);
						});
					} else {
						doInitiateCall(user, chat, hash, null, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, true, true);
					}
					dialog.dismiss();
				});

				TextView cancelButton = dialogView.findViewById(R.id.cancelButton);
				cancelButton.setTypeface(sbdvTypeFace);
				cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel));
				cancelButton.setOnClickListener((View v) -> {
					dialog.dismiss();
				});

			} else {
				Log.d(TAG, "initiateCall(). User: " + user + ", activity: " + activity);
				if (user != null || !(activity instanceof LaunchActivity)) {
					Log.d(TAG, "initiateCall(), start launch activity");
					activity.startActivity(new Intent(activity, LaunchActivity.class).setAction(user != null ? "voip" : "voip_chat"));
				} else {
					if (!TextUtils.isEmpty(hash)) {
						voIPService.setGroupCallHash(hash);
					}
					GroupCallActivity.create((LaunchActivity) activity, AccountInstance.getInstance(UserConfig.selectedAccount), null, null, false, null);
				}
			}
		} else if (VoIPService.callIShouldHavePutIntoIntent == null) {
			doInitiateCall(user, chat, hash, null, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, checkJoiner != null ? checkJoiner : true, true);
		}
	}

	private static void doInitiateCall(TLRPC.User user, TLRPC.Chat chat, String hash, TLRPC.InputPeer peer, boolean hasFewPeers, boolean videoCall, boolean canVideoCall, boolean createCall, Activity activity, BaseFragment fragment, AccountInstance accountInstance, boolean checkJoiner, boolean checkAnonymous) {
		if (activity == null || user == null && chat == null) {
			Log.d(TAG, "can't initiate call: activity (" + activity + ") or user" + (user) + ") is NULL");
			return;
		}
		if (SystemClock.elapsedRealtime() - lastCallTime < 200) {
			Log.d(TAG, "can't initiate call: currentTime - lastCallTime < 2000");
			return;
		}
		if (checkJoiner && chat != null && !createCall) {
			TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(chat.id);
			if (chatFull != null && chatFull.groupcall_default_join_as != null) {
				long did = MessageObject.getPeerId(chatFull.groupcall_default_join_as);
				TLRPC.InputPeer	inputPeer = accountInstance.getMessagesController().getInputPeer(did);
				JoinCallAlert.checkFewUsers(activity, -chat.id, accountInstance, param -> {
					if (!param && hash != null) {
						JoinCallByUrlAlert alert = new JoinCallByUrlAlert(activity, chat) {
							@Override
							protected void onJoin() {
								doInitiateCall(user, chat, hash, inputPeer, true, videoCall, canVideoCall, false, activity, fragment, accountInstance, false, false);
							}
						};
						if (fragment != null) {
							fragment.showDialog(alert);
						}
					} else {
						doInitiateCall(user, chat, hash, inputPeer, !param, videoCall, canVideoCall, false, activity, fragment, accountInstance, false, false);
					}
				});
				return;
			}
		}
		if (checkJoiner && chat != null) {
			JoinCallAlert.open(activity, -chat.id, accountInstance, fragment, createCall ? JoinCallAlert.TYPE_CREATE : JoinCallAlert.TYPE_JOIN, null, (selectedPeer, hasFew, schedule) -> {
				if (createCall && schedule) {
					GroupCallActivity.create((LaunchActivity) activity, accountInstance, chat, selectedPeer, hasFew, hash);
				} else if (!hasFew && hash != null) {
					JoinCallByUrlAlert alert = new JoinCallByUrlAlert(activity, chat) {
						@Override
						protected void onJoin() {
							doInitiateCall(user, chat, hash, selectedPeer, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, false, true);
						}
					};
					if (fragment != null) {
						fragment.showDialog(alert);
					}
				} else {
					doInitiateCall(user, chat, hash, selectedPeer, hasFew, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, false, true);
				}
			});
			return;
		}
		if (checkAnonymous && !hasFewPeers && peer instanceof TLRPC.TL_inputPeerUser && ChatObject.shouldSendAnonymously(chat) && (!ChatObject.isChannel(chat) || chat.megagroup)) {
			new AlertDialog.Builder(activity)
					.setTitle(ChatObject.isChannelOrGiga(chat) ? LocaleController.getString("VoipChannelVoiceChat", R.string.VoipChannelVoiceChat) : LocaleController.getString("VoipGroupVoiceChat", R.string.VoipGroupVoiceChat))
					.setMessage(ChatObject.isChannelOrGiga(chat) ? LocaleController.getString("VoipChannelJoinAnonymouseAlert", R.string.VoipChannelJoinAnonymouseAlert) : LocaleController.getString("VoipGroupJoinAnonymouseAlert", R.string.VoipGroupJoinAnonymouseAlert))
					.setPositiveButton(LocaleController.getString("VoipChatJoin", R.string.VoipChatJoin), (dialog, which) -> doInitiateCall(user, chat, hash, peer, false, videoCall, canVideoCall, createCall, activity, fragment, accountInstance, false, false))
					.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
					.show();
			return;
		}
		if (chat != null && peer != null) {
			TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(chat.id);
			if (chatFull != null) {
				if (peer instanceof TLRPC.TL_inputPeerUser) {
					chatFull.groupcall_default_join_as = new TLRPC.TL_peerUser();
					chatFull.groupcall_default_join_as.user_id = peer.user_id;
				} else if (peer instanceof TLRPC.TL_inputPeerChat) {
					chatFull.groupcall_default_join_as = new TLRPC.TL_peerChat();
					chatFull.groupcall_default_join_as.chat_id = peer.chat_id;
				} else if (peer instanceof TLRPC.TL_inputPeerChannel) {
					chatFull.groupcall_default_join_as = new TLRPC.TL_peerChannel();
					chatFull.groupcall_default_join_as.channel_id = peer.channel_id;
				}
				if (chatFull instanceof TLRPC.TL_chatFull) {
					chatFull.flags |= 32768;
				} else {
					chatFull.flags |= 67108864;
				}
			}
		}
		if (chat != null && !createCall) {
			ChatObject.Call call = accountInstance.getMessagesController().getGroupCall(chat.id, false);
			if (call != null && call.isScheduled()) {
				GroupCallActivity.create((LaunchActivity) activity, accountInstance, chat, peer, hasFewPeers, hash);
				return;
			}
		}

		lastCallTime = SystemClock.elapsedRealtime();
		Intent intent = new Intent(activity, VoIPService.class);
		if (user != null) {
			intent.putExtra("user_id", user.id);
		} else {
			intent.putExtra("chat_id", chat.id);
			intent.putExtra("createGroupCall", createCall);
			intent.putExtra("hasFewPeers", hasFewPeers);
			intent.putExtra("hash", hash);
			if (peer != null) {
				intent.putExtra("peerChannelId", peer.channel_id);
				intent.putExtra("peerChatId", peer.chat_id);
				intent.putExtra("peerUserId", peer.user_id);
				intent.putExtra("peerAccessHash", peer.access_hash);
			}
		}
		intent.putExtra("is_outgoing", true);
		intent.putExtra("start_incall_activity", true);
		intent.putExtra("video_call", Build.VERSION.SDK_INT >= 18 && videoCall);
		intent.putExtra("can_video_call", Build.VERSION.SDK_INT >= 18 && canVideoCall);
		intent.putExtra("account", UserConfig.selectedAccount);
		try {
			activity.startService(intent);
		} catch (Throwable e) {
			FileLog.e(e);
		}
	}

	@TargetApi(Build.VERSION_CODES.M)
	public static void permissionDenied(final Activity activity, final Runnable onFinish, int code) {
		boolean mergedRequest = code == 102;
		if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) || mergedRequest && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
			AlertDialog.Builder dlg = new AlertDialog.Builder(activity)
					.setMessage(AndroidUtilities.replaceTags(mergedRequest ? LocaleController.getString("VoipNeedMicCameraPermissionWithHint", R.string.VoipNeedMicCameraPermissionWithHint) : LocaleController.getString("VoipNeedMicPermissionWithHint", R.string.VoipNeedMicPermissionWithHint)))
					.setPositiveButton(LocaleController.getString("Settings", R.string.Settings), (dialog, which) -> {
						Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
						Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
						intent.setData(uri);
						activity.startActivity(intent);
					})
					.setNegativeButton(LocaleController.getString("ContactsPermissionAlertNotNow", R.string.ContactsPermissionAlertNotNow), null)
					.setOnDismissListener(dialog -> {
						if (onFinish != null)
							onFinish.run();
					})
					.setTopAnimation(mergedRequest ? R.raw.permission_request_camera : R.raw.permission_request_microphone, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground));

			dlg.show();
		}
	}

	public static File getLogsDir() {
		File logsDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "voip_logs");
		if (!logsDir.exists()) {
			logsDir.mkdirs();
		}
		return logsDir;
	}

	public static boolean canRateCall(TLRPC.TL_messageActionPhoneCall call) {
		if (!(call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) && !(call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed)) {
			SharedPreferences prefs = MessagesController.getNotificationsSettings(UserConfig.selectedAccount); // always called from chat UI
			Set<String> hashes = prefs.getStringSet("calls_access_hashes", (Set<String>) Collections.EMPTY_SET);
			for (String hash : hashes) {
				String[] d = hash.split(" ");
				if (d.length < 2) {
					continue;
				}
				if (d[0].equals(call.call_id + "")) {
					return true;
				}
			}
		}
		return false;
	}

	public static void sendCallRating(final long callID, final long accessHash, final int account, int rating) {
		final int currentAccount = UserConfig.selectedAccount;
		final TLRPC.TL_phone_setCallRating req = new TLRPC.TL_phone_setCallRating();
		req.rating = rating;
		req.comment = "";
		req.peer = new TLRPC.TL_inputPhoneCall();
		req.peer.access_hash = accessHash;
		req.peer.id = callID;
		req.user_initiative = false;
		ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
			if (response instanceof TLRPC.TL_updates) {
				TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
				MessagesController.getInstance(currentAccount).processUpdates(updates, false);
			}
		});
	}

	public static void showRateAlert(ViewGroup viewGroup, TLRPC.TL_messageActionPhoneCall call) {
		SharedPreferences prefs = MessagesController.getNotificationsSettings(UserConfig.selectedAccount); // always called from chat UI
		Set<String> hashes = prefs.getStringSet("calls_access_hashes", (Set<String>) Collections.EMPTY_SET);
		for (String hash : hashes) {
			String[] d = hash.split(" ");
			if (d.length < 2) {
				continue;
			}
			if (d[0].equals(call.call_id + "")) {
				try {
					long accessHash = Long.parseLong(d[1]);
					showRateAlert(viewGroup, null, call.video, call.call_id, accessHash, UserConfig.selectedAccount, true);
				} catch (Exception ignore) {
				}
				return;
			}
		}
	}

	public static void showRateAlert(final ViewGroup viewGroup, @Nullable final Runnable onDismiss, boolean isVideo, final long callID, final long accessHash, final int account, final boolean userInitiative) {
		final Context context = viewGroup.getContext();
		final View ratingLayout = LayoutInflater.from(context).inflate(R.layout.sbdv_rate_call_layout, viewGroup);
		int backgroundRes;
		if (SbdvServiceLocator.getConfig().getNewBackgroundEnabled()) {
			backgroundRes = R.drawable.sbdv_new_background;
		} else {
			backgroundRes = R.drawable.sbdv_background;
		}
		ratingLayout.setBackgroundResource(backgroundRes);
		final Button skipButton = ratingLayout.findViewById(R.id.rate_skip_button);
		final BetterRatingView ratingView = ratingLayout.findViewById(R.id.call_rating_view);
		skipButton.setOnClickListener(OnClickListenerWrapper.throttleFirst((View) -> {
			if (onDismiss != null) {
				onDismiss.run();
			}
		}));
		ratingView.setOnRatingChangeListener(rating -> {
			if (rating < 5) {
				skipButton.setText(R.string.sbdv_further);
				skipButton.setOnClickListener(OnClickListenerWrapper.throttleFirst((View) -> {
					showRateProblemsLayout(ratingLayout, callID, onDismiss, viewGroup);
				}));
			} else {
				skipButton.setText(R.string.sbdv_send);
				skipButton.setOnClickListener(OnClickListenerWrapper.throttleFirst((View) -> {
					SbdvServiceLocator.getVoIPModelSharedInstance().onCallRate(Long.toString(callID), rating);
					sendBugReportAsync(callID, null);
					showThankfulScreen(onDismiss, viewGroup);
				}));
			}
		});
	}

	private static void showRateProblemsLayout(View ratingLayout, long callId, Runnable onDismiss, ViewGroup root) {
		final TextView titleTextView = ratingLayout.findViewById(R.id.title_tv);
		final TextView descriptionTextView = ratingLayout.findViewById(R.id.description_tv);
		final LinearLayout problemsLayout = ratingLayout.findViewById(R.id.problems_layout);
		final BetterRatingView ratingView = ratingLayout.findViewById(R.id.call_rating_view);
		final Button skipButton = ratingLayout.findViewById(R.id.rate_skip_button);

		final ToggleButton badSoundButton = problemsLayout.findViewById(R.id.bad_sound_toggle);
		final ToggleButton badImageButton = problemsLayout.findViewById(R.id.bad_image_toggle);
		final Button otherProblemButton = problemsLayout.findViewById(R.id.other_problem_button);

		titleTextView.setText(R.string.sbdv_what_didnt_like);
		descriptionTextView.setText(R.string.sbdv_what_didnt_like);
		problemsLayout.setVisibility(View.VISIBLE);
		ratingView.setVisibility(View.GONE);

		skipButton.setText(R.string.sbdv_send);
		skipButton.setOnClickListener(OnClickListenerWrapper.throttleFirst((view) -> {
			String description = "";
			if (badImageButton.isChecked()) {
				description += view.getContext().getResources().getString(R.string.sbdv_bad_image);
			}
			if (badSoundButton.isChecked()) {
				if (!description.isEmpty()) {
					description += ", ";
				}
				description += view.getContext().getResources().getString(R.string.sbdv_bad_sound);
			}
			sendBugReportAsync(callId, description);
			showThankfulScreen(onDismiss, root);
		}));

		//TODO
		otherProblemButton.setVisibility(View.GONE);
		otherProblemButton.setOnClickListener((view) -> {
		});
	}

	private static void showThankfulScreen(Runnable onDismiss, ViewGroup root) {
		View screen = LayoutInflater.from(root.getContext()).inflate(R.layout.sbdv_rate_thankful_screen, root);
		int backgroundRes;
		if (SbdvServiceLocator.getConfig().getNewBackgroundEnabled()) {
			backgroundRes = R.drawable.sbdv_new_background;
		} else {
			backgroundRes = R.drawable.sbdv_background;
		}
		screen.setBackgroundResource(backgroundRes);
		screen.setOnClickListener(OnClickListenerWrapper.throttleFirst((View) -> {
			onDismiss.run();
		}));
		screen.postDelayed(onDismiss, THANKFUL_SCREEN_DISPLAYING_DURATION_MS);
	}

	private static void sendBugReportAsync(long callID, String description) {
		boolean descriptionIsEmpty = description == null || description.isEmpty();
		String message = "Call feedback. CallId: " + callID + (descriptionIsEmpty ? "" : "; Description: " + description);
		Utilities.globalQueue.postRunnable(() -> {
			try {
				Log.d(TAG, "Sending bugReport. CallId: " + callID);
				AndroidUtilities.copyFile(getLogFile(callID), new File(LAST_VOIP_LOG_PATH));
				SbdvServiceLocator.getSettingsSharedInstance().sendBugReport(message);
			} catch (IOException exception) {
				Log.w(TAG, "Unable to send voip bugReport.", exception);
			}
		});
	}

	private static File getLogFile(long callID) {
		if (BuildVars.DEBUG_VERSION) {
			File debugLogsDir = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "logs");
			String[] logs = debugLogsDir.list();
			if (logs != null) {
				for (String log : logs) {
					if (log.endsWith("voip" + callID + ".txt")) {
						return new File(debugLogsDir, log);
					}
				}
			}
		}
		return new File(getLogsDir(), callID + ".log");
	}

	public static void showCallDebugSettings(final Context context) {
		final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
		LinearLayout ll = new LinearLayout(context);
		ll.setOrientation(LinearLayout.VERTICAL);

		TextView warning = new TextView(context);
		warning.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
		warning.setText("Please only change these settings if you know exactly what they do.");
		warning.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
		ll.addView(warning, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 8));

		final TextCheckCell tcpCell = new TextCheckCell(context);
		tcpCell.setTextAndCheck("Force TCP", preferences.getBoolean("dbg_force_tcp_in_calls", false), false);
		tcpCell.setOnClickListener(v -> {
			boolean force = preferences.getBoolean("dbg_force_tcp_in_calls", false);
			SharedPreferences.Editor editor = preferences.edit();
			editor.putBoolean("dbg_force_tcp_in_calls", !force);
			editor.commit();
			tcpCell.setChecked(!force);
		});
		ll.addView(tcpCell);

		if (BuildVars.DEBUG_VERSION && BuildVars.LOGS_ENABLED) {
			final TextCheckCell dumpCell = new TextCheckCell(context);
			dumpCell.setTextAndCheck("Dump detailed stats", preferences.getBoolean("dbg_dump_call_stats", false), false);
			dumpCell.setOnClickListener(v -> {
				boolean force = preferences.getBoolean("dbg_dump_call_stats", false);
				SharedPreferences.Editor editor = preferences.edit();
				editor.putBoolean("dbg_dump_call_stats", !force);
				editor.commit();
				dumpCell.setChecked(!force);
			});
			ll.addView(dumpCell);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			final TextCheckCell connectionServiceCell = new TextCheckCell(context);
			connectionServiceCell.setTextAndCheck("Enable ConnectionService", preferences.getBoolean("dbg_force_connection_service", false), false);
			connectionServiceCell.setOnClickListener(v -> {
				boolean force = preferences.getBoolean("dbg_force_connection_service", false);
				SharedPreferences.Editor editor = preferences.edit();
				editor.putBoolean("dbg_force_connection_service", !force);
				editor.commit();
				connectionServiceCell.setChecked(!force);
			});
			ll.addView(connectionServiceCell);
		}

		new AlertDialog.Builder(context)
				.setTitle(LocaleController.getString("DebugMenuCallSettings", R.string.DebugMenuCallSettings))
				.setView(ll)
				.show();
	}

	public static int getDataSavingDefault() {
		boolean low = DownloadController.getInstance(0).lowPreset.lessCallData,
				medium = DownloadController.getInstance(0).mediumPreset.lessCallData,
				high = DownloadController.getInstance(0).highPreset.lessCallData;
		if (!low && !medium && !high) {
			return Instance.DATA_SAVING_NEVER;
		} else if (low && !medium && !high) {
			return Instance.DATA_SAVING_ROAMING;
		} else if (low && medium && !high) {
			return Instance.DATA_SAVING_MOBILE;
		} else if (low && medium && high) {
			return Instance.DATA_SAVING_ALWAYS;
		}
		if (BuildVars.LOGS_ENABLED)
			FileLog.w("Invalid call data saving preset configuration: " + low + "/" + medium + "/" + high);
		return Instance.DATA_SAVING_NEVER;
	}


	public static String getLogFilePath(String name) {
		final Calendar c = Calendar.getInstance();
		final File externalFilesDir = ApplicationLoader.applicationContext.getExternalFilesDir(null);
		return new File(externalFilesDir, String.format(Locale.US, "logs/%02d_%02d_%04d_%02d_%02d_%02d_%s.txt",
				c.get(Calendar.DATE), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR), c.get(Calendar.HOUR_OF_DAY),
				c.get(Calendar.MINUTE), c.get(Calendar.SECOND), name)).getAbsolutePath();
	}

	public static String getLogFilePath(long callId, boolean stats) {
		final File logsDir = getLogsDir();
		if (!BuildVars.DEBUG_VERSION) {
			final File[] _logs = logsDir.listFiles();
			if (_logs != null) {
				final ArrayList<File> logs = new ArrayList<>(Arrays.asList(_logs));
				while (logs.size() > 20) {
					File oldest = logs.get(0);
					for (File file : logs) {
						if (file.getName().endsWith(".log") && file.lastModified() < oldest.lastModified()) {
							oldest = file;
						}
					}
					oldest.delete();
					logs.remove(oldest);
				}
			}
		}
		if (stats) {
			return new File(logsDir, callId + "_stats.log").getAbsolutePath();
		} else {
			return new File(logsDir, callId + ".log").getAbsolutePath();
		}
	}

    public static void showGroupCallAlert(BaseFragment fragment, TLRPC.Chat currentChat, TLRPC.InputPeer peer, boolean recreate, AccountInstance accountInstance) {
		if (fragment == null || fragment.getParentActivity() == null) {
			return;
		}
		JoinCallAlert.checkFewUsers(fragment.getParentActivity(), -currentChat.id, accountInstance, param -> startCall(currentChat, peer, null, true, fragment.getParentActivity(), fragment, accountInstance));
    }
}
