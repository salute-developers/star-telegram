package org.telegram.ui;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.ui.Components.voip.VoIPHelper;

import ru.sberdevices.sbdv.SbdvServiceLocator;
import ru.sberdevices.sbdv.appstate.AppStateRepository;
import ru.sberdevices.sbdv.appstate.CallState;

public class VoIPFeedbackActivity extends Activity {

	private static final String TAG = "VoIPFeedbackActivity";
	private final AppStateRepository stateRepository = SbdvServiceLocator.getAppStateRepository();
	private final AppStateRepository.CallStateProvider stateProvider = () -> new CallState(false, false, CallState.State.RATING);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate()");
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		super.onCreate(savedInstanceState);
		overridePendingTransition(0, 0);
		FrameLayout contentLayout = new FrameLayout(this);
		setContentView(contentLayout);
		VoIPHelper.showRateAlert(contentLayout, this::finish, getIntent().getBooleanExtra("call_video", false), getIntent().getLongExtra("call_id", 0), getIntent().getLongExtra("call_access_hash", 0), getIntent().getIntExtra("account", 0), false);
	}

	@Override
	protected void onResume() {
		Log.v(TAG, "onResume()");
		stateRepository.setCallStateProvider(stateProvider);
		super.onResume();
	}

	@Override
	protected void onPause() {
		Log.v(TAG, "onPause()");
		super.onPause();
		stateRepository.setCallStateProvider(null);
	}

	@Override
	public void finish() {
		Log.d(TAG, "finish()");
		super.finish();
		overridePendingTransition(0, 0);
	}
}
