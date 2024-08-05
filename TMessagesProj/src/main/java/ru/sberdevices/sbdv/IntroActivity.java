/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package ru.sberdevices.sbdv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.BaseFragment;

public class IntroActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private boolean startPressed = false;

    @Override
    public boolean onFragmentCreate() {
        MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", System.currentTimeMillis()).apply();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);

        fragmentView = LayoutInflater.from(context).inflate(R.layout.sbdv_intro_layout, null);
        TextView startMessagingButtonTextView = fragmentView.findViewById(R.id.startMessagingButton);
        startMessagingButtonTextView.setOnClickListener(view -> {
            if (startPressed) {
                return;
            }
            startPressed = true;
            presentFragment(new LoginActivity(), true, true);
        });
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            startMessagingButtonTextView.setOnLongClickListener(v -> {
                ConnectionsManager.getInstance(currentAccount).switchBackend(true);
                return true;
            });
        }
        startMessagingButtonTextView.requestFocus();

        setBottomScreenText(context);

        setRussianLocale();

        return fragmentView;
    }

    private void setBottomScreenText(Context context) {
        String version = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
        String bottomScreenText = context.getString(R.string.sbdv_version, version);
        bottomScreenText += "\n" + context.getString(R.string.IsNotAnOfficialClientOfTelegram);

        TextView telegramTextView = fragmentView.findViewById(R.id.telegramTextView);
        telegramTextView.setText(bottomScreenText);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", 0).apply();
    }

    public IntroActivity setOnLogout() {
        return this;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
    }

    private void setRussianLocale() {
        for (LocaleController.LocaleInfo info : LocaleController.getInstance().languages) {
            if (info.shortName.equals("ru")) {
                LocaleController.getInstance().applyLanguage(info, true, false, currentAccount);
                return;
            }
        }
    }
}
