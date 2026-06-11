package com.slagalica.app.ui.match;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.R;
import com.slagalica.app.model.Profile;
import com.slagalica.app.repository.MatchRepository;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.ui.HomeActivity;
import com.slagalica.app.ui.game.asocijacije.AsocijacijeActivity;
import com.slagalica.app.ui.game.korakpokorak.KorakPoKorakActivity;
import com.slagalica.app.ui.game.koznazna.KoZnaZnaActivity;
import com.slagalica.app.ui.game.mojbroj.MojBrojActivity;
import com.slagalica.app.ui.game.skocko.SkockoActivity;
import com.slagalica.app.ui.game.spojnice.SpojniceActivity;
import com.slagalica.app.util.UserStatusManager;

public class MatchActivity extends AppCompatActivity {

    private static final int GAME_KZZ   = 0;
    private static final int GAME_CONN  = 1;
    private static final int GAME_ASOC  = 2;
    private static final int GAME_SKOCK = 3;
    private static final int GAME_KPK   = 4;
    private static final int GAME_MB    = 5;

    private static final String[] GAME_NAMES = {
            "Who knows, knows", "Connections", "Asocijacije", "Skočko", "Step by step", "My number"
    };
    private static final Class<?>[] GAME_CLASSES = {
            KoZnaZnaActivity.class,
            SpojniceActivity.class,
            AsocijacijeActivity.class,
            SkockoActivity.class,
            KorakPoKorakActivity.class,
            MojBrojActivity.class
    };

    private String matchId;
    private boolean isPlayer1;
    private String myUsername;
    private String opponentUsername;

    private int currentGame = 0;
    private int totalP1 = 0, totalP2 = 0;
    private boolean matchComplete = false;
    private boolean opponentForfeited = false;
    private boolean gameInProgress = false;
    private boolean waitingForOppScoreThisGame = false;
    private int lastSubmittedMyScore = 0;
    private String pendingStealQuestionId = null;

    private int pendingP1ForGame4 = 0;
    private int pendingP2ForGame4 = 0;

    private MatchRepository matchRepository;
    private ValueEventListener currentScoreListener;
    private ValueEventListener currentTurnListener;
    private ValueEventListener forfeitListener;

    private TextView tvMyName, tvOpponentName, tvMyScore, tvOpponentScore;
    private ImageView ivStateIcon;
    private TextView tvCurrentGame, tvGameStatus;
    private MaterialButton btnPlayGame, btnGoHome;
    private View sectionInProgress, sectionGameOver;
    private TextView tvFinalScore, tvWinner;
    private View[] dots = new View[6];

    private ActivityResultLauncher<Intent> gameLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match);

        matchId          = getIntent().getStringExtra("matchId");
        isPlayer1        = getIntent().getBooleanExtra("isPlayer1", true);
        myUsername       = getIntent().getStringExtra("username");
        opponentUsername = getIntent().getStringExtra("opponentUsername");
        if (opponentUsername == null) opponentUsername = "Opponent";

        matchRepository = new MatchRepository();
        initViews();
        loadStatusStrip();
        setupGameLauncher();
        setupForfeitHandling();
        advanceToGame(0);
    }

    private void initViews() {
        tvMyName        = findViewById(R.id.tvMyName);
        tvOpponentName  = findViewById(R.id.tvOpponentName);
        tvMyScore       = findViewById(R.id.tvMyScore);
        tvOpponentScore = findViewById(R.id.tvOpponentScore);
        ivStateIcon     = findViewById(R.id.ivStateIcon);
        tvCurrentGame   = findViewById(R.id.tvCurrentGame);
        tvGameStatus    = findViewById(R.id.tvGameStatus);
        btnPlayGame     = findViewById(R.id.btnPlayGame);
        btnGoHome       = findViewById(R.id.btnGoHome);
        sectionInProgress = findViewById(R.id.sectionInProgress);
        sectionGameOver   = findViewById(R.id.sectionGameOver);
        tvFinalScore    = findViewById(R.id.tvFinalScore);
        tvWinner        = findViewById(R.id.tvWinner);

        int[] dotIds = { R.id.dot0, R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4, R.id.dot5 };
        for (int i = 0; i < 6; i++) dots[i] = findViewById(dotIds[i]);

        tvMyName.setText(myUsername != null ? myUsername : "You");
        tvOpponentName.setText(opponentUsername);

        btnGoHome.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }

    private void loadStatusStrip() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            findViewById(R.id.matchStatusStrip).setVisibility(View.GONE);
            return;
        }
        FirebaseFirestore.getInstance()
                .collection("profiles").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    Profile profile = doc.toObject(Profile.class);
                    Long tokens = doc.getLong("tokens");
                    Long stars  = doc.getLong("stars");
                    int t = tokens != null ? tokens.intValue() : 0;
                    long s = stars  != null ? stars  : 0;
                    ((TextView) findViewById(R.id.tvMatchTokenCount)).setText(String.valueOf(t));
                    ((TextView) findViewById(R.id.tvMatchStarCount)).setText(String.valueOf(s));
                    String league = profile != null ? profile.getLeague(null) : "";
                    ((TextView) findViewById(R.id.tvMatchLeague)).setText(league);
                });
    }

    private void setupForfeitHandling() {
        String myUid = matchRepository.getUid();
        if (myUid == null) return;
        forfeitListener = matchRepository.listenForForfeit(matchId, myUid, () -> runOnUiThread(() -> {
            if (opponentForfeited || matchComplete) return;
            opponentForfeited = true;
            if (currentTurnListener != null) {
                matchRepository.removeP1DoneListener(matchId, currentGame, currentTurnListener);
                currentTurnListener = null;
            }
            if (currentScoreListener != null) {
                matchRepository.removeScoreListener(matchId, currentGame, currentScoreListener);
                currentScoreListener = null;
            }
            if (!gameInProgress) {
                if (waitingForOppScoreThisGame) {
                    waitingForOppScoreThisGame = false;
                    addMyScoreAndAdvance(currentGame, lastSubmittedMyScore);
                } else {
                    advanceToGame(currentGame);
                }
            }
        }));
    }

    private void setupGameLauncher() {
        gameLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    gameInProgress = false;

                    boolean userQuit = result.getData() != null
                            && result.getData().getBooleanExtra("quitMatch", false);
                    if (userQuit && !matchComplete && !opponentForfeited) {
                        matchComplete = true;
                        String myUid = matchRepository.getUid();
                        if (myUid != null) matchRepository.writeForfeit(matchId, myUid);
                        startActivity(new Intent(this, HomeActivity.class));
                        finish();
                        return;
                    }

                    if (result.getData() != null && result.getData().hasExtra("kpkSoloStealBonus")) {
                        int bonus = result.getData().getIntExtra("kpkSoloStealBonus", 0);
                        writeKpkFinalAndAdvance(pendingP1ForGame4 + bonus, pendingP2ForGame4);
                        return;
                    }

                    int idx = currentGame;
                    int returnedP1 = 0, returnedP2 = 0;
                    String stealQId = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        returnedP1 = result.getData().getIntExtra("p1Score", 0);
                        returnedP2 = result.getData().getIntExtra("p2Score", 0);
                        stealQId  = result.getData().getStringExtra("kpkStealQuestionId");
                    }

                    final int finalReturnedP1 = returnedP1;
                    final int finalReturnedP2 = returnedP2;
                    final String finalStealQId = stealQId;

                    if (!opponentForfeited) showWaitingForOpponent("Waiting for " + opponentUsername + "...");

                    if (idx == GAME_ASOC || idx == GAME_SKOCK) {
                        totalP1 = finalReturnedP1;
                        totalP2 = finalReturnedP2;
                        updateScoreDisplay();
                        advanceToGame(idx + 1);

                    } else if (idx == GAME_KPK && finalStealQId != null) {
                        int myScore = isPlayer1 ? finalReturnedP1 : finalReturnedP2;
                        RepositoryCallback<Void> afterScore = new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void v)      { afterMyScoreWritten(idx, isPlayer1 ? finalReturnedP1 : finalReturnedP2); }
                            @Override public void onFailure(Exception e) { afterMyScoreWritten(idx, isPlayer1 ? finalReturnedP1 : finalReturnedP2); }
                        };
                        RepositoryCallback<Void> writeScore = new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void v) {
                                matchRepository.writePlayerScore(matchId, idx, isPlayer1, myScore, afterScore);
                            }
                            @Override public void onFailure(Exception e) {
                                matchRepository.writePlayerScore(matchId, idx, isPlayer1, myScore, afterScore);
                            }
                        };
                        if (isPlayer1) {
                            matchRepository.writeKpkP1StealQuestion(matchId, finalStealQId, writeScore);
                        } else {
                            matchRepository.writeKpkP2StealQuestion(matchId, finalStealQId, writeScore);
                        }

                    } else {
                        int myScore = isPlayer1 ? finalReturnedP1 : finalReturnedP2;
                        RepositoryCallback<Void> afterScore = new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void v)      { afterMyScoreWritten(idx, myScore); }
                            @Override public void onFailure(Exception e) { afterMyScoreWritten(idx, myScore); }
                        };
                        matchRepository.writePlayerScore(matchId, idx, isPlayer1, myScore, afterScore);
                    }
                }
        );
    }

    private void showWaitingForOpponent(String statusText) {
        ivStateIcon.setImageResource(R.drawable.ic_hourglass);
        tvGameStatus.setText(statusText);
        btnPlayGame.setVisibility(View.GONE);
    }

    private void afterMyScoreWritten(int idx, int myScore) {
        if (opponentForfeited) {
            addMyScoreAndAdvance(idx, myScore);
        } else {
            lastSubmittedMyScore = myScore;
            waitingForOppScoreThisGame = true;
            listenForBothScores(idx);
        }
    }

    private void addMyScoreAndAdvance(int idx, int myScore) {
        if (isPlayer1) totalP1 += myScore;
        else           totalP2 += myScore;
        updateScoreDisplay();
        advanceToGame(idx + 1);
    }

    private void advanceToGame(int idx) {
        currentGame = idx;
        updateProgressDots();

        if (idx >= 6) {
            showMatchOver();
            return;
        }

        tvCurrentGame.setText("GAME " + (idx + 1) + " / 6");

        waitingForOppScoreThisGame = false;

        if (opponentForfeited) {
            showPlayButton(idx);
            return;
        }

        if (idx == GAME_KZZ || idx == GAME_CONN) {
            if (isPlayer1) showPlayButton(idx);
            else {
                showWaitingForOpponent("Waiting for " + opponentUsername + " to start " + GAME_NAMES[idx] + "...");
                currentTurnListener = matchRepository.listenForKzzOrConnStarted(matchId, String.valueOf(idx), () -> {
                    currentTurnListener = null;
                    launchGame(idx);
                });
            }
            return;
        }

        if (idx == GAME_ASOC || idx == GAME_SKOCK || idx == GAME_MB) {
            if (isPlayer1) {
                showPlayButton(idx);
            } else {
                showWaitingForOpponent("Waiting for " + opponentUsername + " to start " + GAME_NAMES[idx] + "...");
                currentTurnListener = matchRepository.listenForAsocSkockoStarted(
                        matchId, String.valueOf(idx), () -> {
                            currentTurnListener = null;
                            launchGame(idx);
                        });
            }
            return;
        }

        if (isPlayer1) {
            showPlayButton(idx);
        } else {
            showWaitingForOpponent("Waiting for " + opponentUsername + " to play " + GAME_NAMES[idx] + "...");
            currentTurnListener = matchRepository.listenForP1Done(matchId, idx, () -> {
                currentTurnListener = null;
                if (idx == GAME_KPK) {
                    matchRepository.readKpkP1StealQuestion(matchId, new RepositoryCallback<String>() {
                        @Override public void onSuccess(String qId) {
                            pendingStealQuestionId = qId;
                            showPlayButton(idx);
                        }
                        @Override public void onFailure(Exception e) {
                            showPlayButton(idx);
                        }
                    });
                } else {
                    showPlayButton(idx);
                }
            });
        }
    }

    private void showPlayButton(int idx) {
        ivStateIcon.setImageResource(R.drawable.ic_play_match);
        tvGameStatus.setText(GAME_NAMES[idx]);
        btnPlayGame.setVisibility(View.VISIBLE);
        btnPlayGame.setEnabled(true);
        btnPlayGame.setOnClickListener(v -> launchGame(idx));
    }

    private void launchGame(int idx) {
        btnPlayGame.setEnabled(false);
        gameInProgress = true;
        if ((idx == GAME_ASOC || idx == GAME_SKOCK || idx == GAME_MB) && isPlayer1) {
            matchRepository.writeAsocSkockoStarted(matchId, String.valueOf(idx));
        }
        Intent intent = new Intent(this, GAME_CLASSES[idx]);
        intent.putExtra("isMatchGame", true);
        intent.putExtra("isPlayer1", isPlayer1);
        intent.putExtra("matchId", matchId);
        intent.putExtra("username", myUsername);
        intent.putExtra("opponentUsername", opponentUsername);
        intent.putExtra("prevTotalP1", totalP1);
        intent.putExtra("prevTotalP2", totalP2);
        if (opponentForfeited) intent.putExtra("soloContinue", true);
        if (idx == GAME_KPK && pendingStealQuestionId != null) {
            intent.putExtra("kpkStealQuestionId", pendingStealQuestionId);
            pendingStealQuestionId = null;
        }
        gameLauncher.launch(intent);
    }

    private void launchKpkSoloSteal(String questionId) {
        gameInProgress = true;
        Intent intent = new Intent(this, KorakPoKorakActivity.class);
        intent.putExtra("isMatchGame", true);
        intent.putExtra("isPlayer1", true);
        intent.putExtra("kpkSoloSteal", true);
        intent.putExtra("kpkStealQuestionId", questionId);
        intent.putExtra("matchId", matchId);
        intent.putExtra("username", myUsername);
        intent.putExtra("opponentUsername", opponentUsername);
        gameLauncher.launch(intent);
    }

    private void listenForBothScores(int idx) {
        if (idx == GAME_KPK) {
            if (isPlayer1) {
                currentScoreListener = matchRepository.listenForGameScore(matchId, idx, (p1, p2, bothDone) -> {
                    if (!bothDone) return;
                    currentScoreListener = null;
                    handleGame4P1Side(p1, p2);
                });
            } else {
                currentScoreListener = matchRepository.listenForKpkComplete(matchId, (finalP1, p2) -> {
                    currentScoreListener = null;
                    totalP1 += finalP1;
                    totalP2 += p2;
                    updateScoreDisplay();
                    advanceToGame(idx + 1);
                });
            }
        } else {
            currentScoreListener = matchRepository.listenForGameScore(matchId, idx, (p1, p2, bothDone) -> {
                if (bothDone) {
                    currentScoreListener = null;
                    totalP1 += p1;
                    totalP2 += p2;
                    updateScoreDisplay();
                    advanceToGame(idx + 1);
                } else {
                    int myDisplayTotal  = isPlayer1 ? (totalP1 + p1) : (totalP2 + p2);
                    int oppDisplayTotal = isPlayer1 ? (totalP2 + p2) : (totalP1 + p1);
                    tvMyScore.setText(String.valueOf(myDisplayTotal));
                    tvOpponentScore.setText(String.valueOf(oppDisplayTotal));
                }
            });
        }
    }

    private void handleGame4P1Side(int p1, int p2) {
        matchRepository.readKpkP2StealQuestion(matchId, new RepositoryCallback<String>() {
            @Override public void onSuccess(String p2StealQId) {
                if (p2StealQId != null && !p2StealQId.isEmpty()) {
                    pendingP1ForGame4 = p1;
                    pendingP2ForGame4 = p2;
                    launchKpkSoloSteal(p2StealQId);
                } else {
                    writeKpkFinalAndAdvance(p1, p2);
                }
            }
            @Override public void onFailure(Exception e) {
                writeKpkFinalAndAdvance(p1, p2);
            }
        });
    }

    private void writeKpkFinalAndAdvance(int finalP1, int p2) {
        matchRepository.writeKpkFinal(matchId, finalP1, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void v) { proceedAfterKpk(finalP1, p2); }
            @Override public void onFailure(Exception e) { proceedAfterKpk(finalP1, p2); }
        });
    }

    private void proceedAfterKpk(int finalP1, int p2) {
        totalP1 += finalP1;
        totalP2 += p2;
        updateScoreDisplay();
        advanceToGame(GAME_KPK + 1);
    }

    private void updateScoreDisplay() {
        int myTotal  = isPlayer1 ? totalP1 : totalP2;
        int oppTotal = isPlayer1 ? totalP2 : totalP1;
        tvMyScore.setText(String.valueOf(myTotal));
        tvOpponentScore.setText(String.valueOf(oppTotal));
    }

    private void updateProgressDots() {
        for (int i = 0; i < 6; i++) {
            if (i <= currentGame && currentGame < 6) {
                dots[i].setBackgroundResource(R.drawable.bg_clue_active);
            } else {
                dots[i].setBackgroundResource(R.drawable.bg_input);
            }
        }
    }

    private void showMatchOver() {
        matchComplete = true;
        if (forfeitListener != null) {
            matchRepository.removeForfeitListener(matchId, forfeitListener);
            forfeitListener = null;
        }
        sectionInProgress.setVisibility(View.GONE);
        sectionGameOver.setVisibility(View.VISIBLE);

        int myTotal  = isPlayer1 ? totalP1 : totalP2;
        int oppTotal = isPlayer1 ? totalP2 : totalP1;
        tvFinalScore.setText(myTotal + " – " + oppTotal);

        String result;
        if (opponentForfeited)        result = "You win!";
        else if (myTotal > oppTotal)  result = "You win!";
        else if (oppTotal > myTotal)  result = opponentUsername + " wins!";
        else                          result = "Draw!";
        tvWinner.setText(result);

        String myUid = matchRepository.getUid();
        if (myUid != null) {
            int oppForStars = opponentForfeited ? -1 : oppTotal;
            matchRepository.finishMatch(this, matchId, myUid, myTotal, oppForStars, opponentUsername);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentScoreListener != null) {
            if (currentGame == GAME_KPK && !isPlayer1) {
                matchRepository.removeKpkCompleteListener(matchId, currentScoreListener);
            } else {
                matchRepository.removeScoreListener(matchId, currentGame, currentScoreListener);
            }
        }
        if (currentTurnListener != null) {
            if ((currentGame == GAME_ASOC || currentGame == GAME_SKOCK || currentGame == GAME_MB) && !isPlayer1) {
                matchRepository.removeAsocSkockoStartedListener(matchId, String.valueOf(currentGame), currentTurnListener);
            } else {
                matchRepository.removeP1DoneListener(matchId, currentGame, currentTurnListener);
            }
        }
        if (forfeitListener != null) {
            matchRepository.removeForfeitListener(matchId, forfeitListener);
        }
        if (!matchComplete) {
            String myUid = matchRepository.getUid();
            if (myUid != null) matchRepository.writeForfeit(matchId, myUid);
        }
    }
}