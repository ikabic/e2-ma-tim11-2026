package com.slagalica.app.ui.chat;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.slagalica.app.BaseActivity;
import com.slagalica.app.R;
import com.slagalica.app.adapter.ChatAdapter;
import com.slagalica.app.model.Region;
import com.slagalica.app.model.RegionRegistry;
import com.slagalica.app.util.ChatNotificationManager;
import com.slagalica.app.util.GameToast;
import com.slagalica.app.viewmodel.ChatViewModel;
import com.slagalica.app.repository.DailyMissionRepository;

public class ChatActivity extends BaseActivity {

    private ChatViewModel viewModel;
    private ChatAdapter adapter;
    private RecyclerView rvMessages;
    private EditText etMessage;

    private String regionKey;
    private String myUsername = "You";
    private final DailyMissionRepository missionRepo = new DailyMissionRepository();
    private boolean chatMissionDone = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        regionKey = getIntent().getStringExtra("regionKey");
        String usernameExtra = getIntent().getStringExtra("username");
        if (usernameExtra != null) myUsername = usernameExtra;

        if (regionKey == null || regionKey.isEmpty()) {
            GameToast.show(this, "No region for chat.", GameToast.Type.ERROR);
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        Region region = RegionRegistry.get(regionKey);
        String regionName = region != null && region.getDisplayName() != null
                ? region.getDisplayName() : "Your region";
        ((TextView) findViewById(R.id.tvRegionName)).setText(regionName);

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        adapter = new ChatAdapter(viewModel.getUid());
        rvMessages = findViewById(R.id.rvMessages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        etMessage = findViewById(R.id.etMessage);
        MaterialButton btnSend = findViewById(R.id.btnSend);

        viewModel.getMessages().observe(this, list -> {
            adapter.setItems(list);
            if (list != null && !list.isEmpty()) rvMessages.scrollToPosition(list.size() - 1);
        });

        btnSend.setOnClickListener(v -> sendCurrent());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrent();
                return true;
            }
            return false;
        });

        viewModel.start(regionKey);
    }

    private void sendCurrent() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        viewModel.send(myUsername, text);
        etMessage.setText("");

        if (!chatMissionDone) {
            chatMissionDone = true;
            missionRepo.completeMission(DailyMissionRepository.KEY_SEND_CHAT);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ChatNotificationManager.setVisibleRegion(regionKey);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ChatNotificationManager.setVisibleRegion(null);
    }
}
