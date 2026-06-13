package com.slagalica.app.ui.tournament;

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
import com.slagalica.app.repository.TournamentRepository;
import com.slagalica.app.ui.HomeActivity;
import com.slagalica.app.ui.game.asocijacije.AsocijacijeActivity;
import com.slagalica.app.ui.game.korakpokorak.KorakPoKorakActivity;
import com.slagalica.app.ui.game.koznazna.KoZnaZnaActivity;
import com.slagalica.app.ui.game.mojbroj.MojBrojActivity;
import com.slagalica.app.ui.game.skocko.SkockoActivity;
import com.slagalica.app.ui.game.spojnice.SpojniceActivity;


public class TournamentMatchActivity extends AppCompatActivity {

    private static final int GAME_KZZ = 0;
    private static final int GAME_CONN = 1;
    private static final int GAME_ASOC = 2;
    private static final int GAME_SKOCK = 3;
    private static final int GAME_KPK = 4;
    private static final int GAME_MB    = 5;

    private static final String[] GAME_NAMES = {
            "Who knows, knows", "Connections", "Asocijacije", "Skočko", "Step by step", "My number"
    };
    private static final Class<?>[] GAME_CLASSES = {
            KoZnaZnaActivity.class, SpojniceActivity.class,
            AsocijacijeActivity.class, SkockoActivity.class,
            KorakPoKorakActivity.class, MojBrojActivity.class
    };

    private String tournamentId, semifinal, matchId;
    private boolean isPlayer1;
    private String myUsername, opponentUsername;
    private String myUid;
    private int currentGame = 0;
    private int totalP1 = 0, totalP2 = 0;
    private boolean matchComplete = false;
    private boolean opponentForfeited = false;
    private boolean gameInProgress = false;
    private String pendingStealQuestionId = null;
    private int pendingP1ForGame4 = 0, pendingP2ForGame4 = 0;
    private MatchRepository matchRepo;
    private TournamentRepository tournamentRepo;
    private ValueEventListener currentScoreListener;
    private ValueEventListener currentTurnListener;
    private ValueEventListener forfeitListener;
    private ValueEventListener finalReadyListener;
    private TextView tvMyName, tvOpponentName, tvMyScore, tvOpponentScore;
    private ImageView ivStateIcon;
    private TextView tvCurrentGame, tvGameStatus;
    private MaterialButton btnPlayGame, btnGoHome;
    private View sectionInProgress, sectionGameOver, sectionWaiting;
    private TextView tvFinalScore, tvWinner, tvWaitingMsg;
    private View[] dots = new View[6];
    private String opponentUid;

    private ActivityResultLauncher<Intent> gameLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament_match);

        tournamentId = getIntent().getStringExtra("tournamentId");
        semifinal = getIntent().getStringExtra("semifinal");
        matchId = getIntent().getStringExtra("matchId");
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);
        myUsername = getIntent().getStringExtra("username");
        opponentUsername = getIntent().getStringExtra("opponentUsername");
        if (opponentUsername == null) opponentUsername = "Opponent";
        opponentUid = getIntent().getStringExtra("opponentUid");

        matchRepo = new MatchRepository();
        tournamentRepo = new TournamentRepository();
        myUid = matchRepo.getUid();

        initViews();
        setupGameLauncher();
        setupForfeitHandling();
        advanceToGame(0);
    }

    private void initViews() {
        tvMyName = findViewById(R.id.tvMyName);
        tvOpponentName  = findViewById(R.id.tvOpponentName);
        tvMyScore = findViewById(R.id.tvMyScore);
        tvOpponentScore = findViewById(R.id.tvOpponentScore);
        ivStateIcon = findViewById(R.id.ivStateIcon);
        tvCurrentGame = findViewById(R.id.tvCurrentGame);
        tvGameStatus = findViewById(R.id.tvGameStatus);
        btnPlayGame = findViewById(R.id.btnPlayGame);
        btnGoHome = findViewById(R.id.btnGoHome);
        sectionInProgress = findViewById(R.id.sectionInProgress);
        sectionGameOver = findViewById(R.id.sectionGameOver);
        sectionWaiting = findViewById(R.id.sectionWaiting);
        tvFinalScore = findViewById(R.id.tvFinalScore);
        tvWinner = findViewById(R.id.tvWinner);
        tvWaitingMsg = findViewById(R.id.tvWaitingMsg);

        int[] dotIds = { R.id.dot0, R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4, R.id.dot5 };
        for (int i = 0; i < 6; i++) dots[i] = findViewById(dotIds[i]);

        tvMyName.setText(myUsername != null ? myUsername : "You");
        tvOpponentName.setText(opponentUsername);

        btnGoHome.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }

    private void setupForfeitHandling() {
        if (myUid == null) return;
        forfeitListener = matchRepo.listenForForfeit(matchId, myUid, () -> runOnUiThread(() -> {
            if (opponentForfeited || matchComplete) return;
            opponentForfeited = true;
            if (!gameInProgress) showSemifinalOver();
        }));
    }

    private void setupGameLauncher() {
        gameLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    gameInProgress = false;

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
                        stealQId   = result.getData().getStringExtra("kpkStealQuestionId");
                    }

                    final int fp1 = returnedP1, fp2 = returnedP2;
                    final String fStealQId = stealQId;

                    showWaitingForOpponent("Waiting for " + opponentUsername + "...");

                    if (idx == GAME_ASOC || idx == GAME_SKOCK) {
                        totalP1 = fp1; totalP2 = fp2;
                        updateScoreDisplay();
                        if (opponentForfeited) showSemifinalOver();
                        else advanceToGame(idx + 1);

                    } else if (idx == GAME_KPK && fStealQId != null) {
                        int myScore = isPlayer1 ? fp1 : fp2;
                        RepositoryCallback<Void> after = new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void v) { afterMyScoreWritten(idx, isPlayer1 ? fp1 : fp2); }
                            @Override public void onFailure(Exception e) { afterMyScoreWritten(idx, isPlayer1 ? fp1 : fp2); }
                        };
                        RepositoryCallback<Void> write = new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void v) { matchRepo.writePlayerScore(matchId, idx, isPlayer1, myScore, after); }
                            @Override public void onFailure(Exception e) { matchRepo.writePlayerScore(matchId, idx, isPlayer1, myScore, after); }
                        };
                        if (isPlayer1) matchRepo.writeKpkP1StealQuestion(matchId, fStealQId, write);
                        else matchRepo.writeKpkP2StealQuestion(matchId, fStealQId, write);

                    } else {
                        int myScore = isPlayer1 ? fp1 : fp2;
                        RepositoryCallback<Void> after = new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void v) { afterMyScoreWritten(idx, myScore); }
                            @Override public void onFailure(Exception e) { afterMyScoreWritten(idx, myScore); }
                        };
                        matchRepo.writePlayerScore(matchId, idx, isPlayer1, myScore, after);
                    }
                });
    }

    private void showWaitingForOpponent(String msg) {
        ivStateIcon.setImageResource(R.drawable.ic_hourglass);
        tvGameStatus.setText(msg);
        btnPlayGame.setVisibility(View.GONE);
    }

    private void afterMyScoreWritten(int idx, int myScore) {
        if (opponentForfeited) {
            if (isPlayer1) totalP1 += myScore;
            else  totalP2 += myScore;
            updateScoreDisplay();
            showSemifinalOver();
        } else {
            listenForBothScores(idx);
        }
    }

    private void advanceToGame(int idx) {
        currentGame = idx;
        updateProgressDots();
        if (idx >= 6) { showSemifinalOver(); return; }

        tvCurrentGame.setText("GAME " + (idx + 1) + " / 6");

        if (idx == GAME_KZZ || idx == GAME_CONN) {
            if (isPlayer1) showPlayButton(idx);
            else {
                showWaitingForOpponent("Waiting for " + opponentUsername + " to start...");
                currentTurnListener = matchRepo.listenForKzzOrConnStarted(matchId, String.valueOf(idx), () -> {
                    currentTurnListener = null;
                    launchGame(idx);
                });
            }
            return;
        }

        if (idx == GAME_ASOC || idx == GAME_SKOCK || idx == GAME_MB) {
            if (isPlayer1) showPlayButton(idx);
            else {
                showWaitingForOpponent("Waiting for " + opponentUsername + "...");
                currentTurnListener = matchRepo.listenForAsocSkockoStarted(matchId, String.valueOf(idx), () -> {
                    currentTurnListener = null;
                    launchGame(idx);
                });
            }
            return;
        }

        if (isPlayer1) {
            showPlayButton(idx);
        } else {
            showWaitingForOpponent("Waiting for " + opponentUsername + "...");
            currentTurnListener = matchRepo.listenForP1Done(matchId, idx, () -> {
                currentTurnListener = null;
                if (idx == GAME_KPK) {
                    matchRepo.readKpkP1StealQuestion(matchId, new RepositoryCallback<String>() {
                        @Override public void onSuccess(String qId) {
                            pendingStealQuestionId = qId;
                            showPlayButton(idx);
                        }
                        @Override public void onFailure(Exception e) { showPlayButton(idx); }
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
            matchRepo.writeAsocSkockoStarted(matchId, String.valueOf(idx));
        }
        Intent intent = new Intent(this, GAME_CLASSES[idx]);
        intent.putExtra("isMatchGame",true);
        intent.putExtra("isPlayer1", isPlayer1);
        intent.putExtra("matchId", matchId);
        intent.putExtra("username", myUsername);
        intent.putExtra("opponentUsername", opponentUsername);
        intent.putExtra("prevTotalP1", totalP1);
        intent.putExtra("prevTotalP2", totalP2);
        if (idx == GAME_KPK && pendingStealQuestionId != null) {
            intent.putExtra("kpkStealQuestionId", pendingStealQuestionId);
            pendingStealQuestionId = null;
        }
        gameLauncher.launch(intent);
    }

    private void listenForBothScores(int idx) {
        if (idx == GAME_KPK) {
            if (isPlayer1) {
                currentScoreListener = matchRepo.listenForGameScore(matchId, idx, (p1, p2, bothDone) -> {
                    if (!bothDone) return;
                    currentScoreListener = null;
                    handleGame4P1Side(p1, p2);
                });
            } else {
                currentScoreListener = matchRepo.listenForKpkComplete(matchId, (fp1, p2) -> {
                    currentScoreListener = null;
                    totalP1 += fp1; totalP2 += p2;
                    updateScoreDisplay();
                    advanceToGame(idx + 1);
                });
            }
        } else {
            currentScoreListener = matchRepo.listenForGameScore(matchId, idx, (p1, p2, bothDone) -> {
                if (bothDone) {
                    currentScoreListener = null;
                    totalP1 += p1; totalP2 += p2;
                    updateScoreDisplay();
                    advanceToGame(idx + 1);
                } else {
                    int myT = isPlayer1 ? (totalP1 + p1) : (totalP2 + p2);
                    int oppT = isPlayer1 ? (totalP2 + p2) : (totalP1 + p1);
                    tvMyScore.setText(String.valueOf(myT));
                    tvOpponentScore.setText(String.valueOf(oppT));
                }
            });
        }
    }

    private void handleGame4P1Side(int p1, int p2) {
        matchRepo.readKpkP2StealQuestion(matchId, new RepositoryCallback<String>() {
            @Override public void onSuccess(String p2StealQId) {
                if (p2StealQId != null && !p2StealQId.isEmpty()) {
                    pendingP1ForGame4 = p1; pendingP2ForGame4 = p2;
                    launchKpkSoloSteal(p2StealQId);
                } else {
                    writeKpkFinalAndAdvance(p1, p2);
                }
            }
            @Override public void onFailure(Exception e) { writeKpkFinalAndAdvance(p1, p2); }
        });
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

    private void writeKpkFinalAndAdvance(int fp1, int p2) {
        matchRepo.writeKpkFinal(matchId, fp1, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void v) { totalP1 += fp1; totalP2 += p2; updateScoreDisplay(); advanceToGame(GAME_KPK + 1); }
            @Override public void onFailure(Exception e) { totalP1 += fp1; totalP2 += p2; updateScoreDisplay(); advanceToGame(GAME_KPK + 1); }
        });
    }

    private void showSemifinalOver() {
        matchComplete = true;
        cleanupListeners();

        int myTotal  = isPlayer1 ? totalP1 : totalP2;
        int oppTotal = isPlayer1 ? totalP2 : totalP1;
        boolean iWon = myTotal > oppTotal || opponentForfeited;

        String winnerUid = iWon ? myUid : opponentUid;
        String winnerUsername = iWon ? myUsername : opponentUsername;
        String loserUid = iWon ? opponentUid : myUid;
        int winnerScore = iWon ? myTotal : oppTotal;
        int loserScore = iWon ? oppTotal : myTotal;

        matchRepo.writeForfeit(matchId, "");

        showWaitingForFinal(iWon, myTotal, oppTotal);

        finalReadyListener = tournamentRepo.listenForFinalReady(tournamentId, (fmId, fp1u, fp1n, fp2u, fp2n, iAmIn, iP1) -> runOnUiThread(() -> onFinalReady(fmId, fp1u, fp1n, fp2u, fp2n, iAmIn, iP1)));

        if (iWon) {
            tournamentRepo.reportSemifinalResult(this,
                    tournamentId, semifinal,
                    winnerUid != null ? winnerUid : "unknown", winnerUsername,
                    loserUid != null ? loserUid : "unknown",
                    winnerScore, loserScore,
                    (finalMatchId, p1Uid, p1Username, p2Uid, p2Username, iAmInFinal, isP1InFinal) -> {
                        if (finalMatchId != null) {
                            runOnUiThread(() -> onFinalReady(finalMatchId, p1Uid, p1Username, p2Uid, p2Username, iAmInFinal, isP1InFinal));
                        }
                    });
        }
    }

    private void showWaitingForFinal(boolean iWon, int myTotal, int oppTotal) {
        sectionInProgress.setVisibility(View.GONE);
        sectionGameOver.setVisibility(View.GONE);
        sectionWaiting.setVisibility(View.VISIBLE);

        String resultLine = iWon
                ? "You won " + myTotal + " – " + oppTotal + "!"
                : "You lost " + myTotal + " – " + oppTotal;
        tvWaitingMsg.setText(resultLine + "\n\nWaiting for the other semi final to end...");
    }

    private void onFinalReady(String finalMatchId, String p1Uid, String p1Username, String p2Uid, String p2Username, boolean iAmInFinal, boolean isP1InFinal) {
        android.util.Log.d("FINAL", "onFinalReady finalMatchId=" + finalMatchId
                + " iAmInFinal=" + iAmInFinal
                + " isP1InFinal=" + isP1InFinal
                + " p1Username=" + p1Username
                + " p2Username=" + p2Username);
        if (iAmInFinal) {
            Intent intent = new Intent(this, TournamentFinalActivity.class);
            intent.putExtra("tournamentId",  tournamentId);
            intent.putExtra("matchId", finalMatchId);
            intent.putExtra("isPlayer1", isP1InFinal);
            intent.putExtra("username", myUsername);
            intent.putExtra("opponentUsername", isP1InFinal ? p2Username : p1Username);
            startActivity(intent);
            finish();
        } else {
            sectionWaiting.setVisibility(View.GONE);
            sectionGameOver.setVisibility(View.VISIBLE);

            tvFinalScore.setText("Eliminated");
            tvWinner.setText("Final: " + p1Username + " vs " + p2Username);
            btnPlayGame.setVisibility(View.GONE);
        }
    }

    private void updateScoreDisplay() {
        tvMyScore.setText(String.valueOf(isPlayer1 ? totalP1 : totalP2));
        tvOpponentScore.setText(String.valueOf(isPlayer1 ? totalP2 : totalP1));
    }

    private void updateProgressDots() {
        for (int i = 0; i < 6; i++) {
            dots[i].setBackgroundResource(i <= currentGame && currentGame < 6 ? R.drawable.bg_clue_active : R.drawable.bg_input);
        }
    }

    private void cleanupListeners() {
        if (forfeitListener != null) { matchRepo.removeForfeitListener(matchId, forfeitListener); forfeitListener = null; }
        if (currentScoreListener != null) { matchRepo.removeScoreListener(matchId, currentGame, currentScoreListener); currentScoreListener = null; }
        if (currentTurnListener  != null) { matchRepo.removeP1DoneListener(matchId, currentGame, currentTurnListener); currentTurnListener = null; }
        if (finalReadyListener != null) {
            tournamentRepo.removeListener(tournamentRepo.getFinalMatchIdRef(tournamentId), finalReadyListener);
            finalReadyListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupListeners();
        if (!matchComplete && myUid != null) {
            matchRepo.writeForfeit(matchId, myUid);
            tournamentRepo.writeForfeit(tournamentId, myUid);
        }
    }
}