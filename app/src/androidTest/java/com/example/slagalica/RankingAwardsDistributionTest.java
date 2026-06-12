package com.example.slagalica;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.repository.RankingRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class RankingAwardsDistributionTest {

    private final String testCycleId = "monthly_2026_test_cycle";

    @Test
    public void runAwardDistributionTest() throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        Tasks.await(auth.signInAnonymously(), 5, TimeUnit.SECONDS);
        RankingRepository repository = new RankingRepository();

        String type = "monthly";

        for (int i = 1; i <= 11; i++) {
            String uid = "test_user_" + i;

            Map<String, Object> profile = new HashMap<>();
            profile.put("tokens", 0);
            profile.put("stars", 100);
            Tasks.await(db.collection("profiles").document(uid).set(profile), 5, TimeUnit.SECONDS);

            Map<String, Object> entry = new HashMap<>();
            entry.put("username", "Player " + i);
            entry.put("cycleStars", 1200 - (i * 100));
            Tasks.await(db.collection("rankingCycles").document(testCycleId)
                    .collection("entries").document(uid).set(entry), 5, TimeUnit.SECONDS);
        }

        CountDownLatch distributionLatch = new CountDownLatch(1);
        AtomicBoolean isSuccess = new AtomicBoolean(false);

        repository.distributeRewards(testCycleId, type, new RepositoryCallback<>() {
            @Override
            public void onSuccess(List<RankingRepository.RewardResult> results) {
                isSuccess.set(true);
                distributionLatch.countDown();
            }
            @Override
            public void onFailure(Exception e) { distributionLatch.countDown(); }
        });

        boolean distributionCompleted = distributionLatch.await(15, TimeUnit.SECONDS);
        assertTrue("Reward distribution timed out.", distributionCompleted);
        assertTrue("Reward distribution reported a failure callback.", isSuccess.get());

        DocumentSnapshot rank1Snap = Tasks.await(db.collection("profiles").document("test_user_1").get(), 5, TimeUnit.SECONDS);
        DocumentSnapshot rank2Snap = Tasks.await(db.collection("profiles").document("test_user_2").get(), 5, TimeUnit.SECONDS);
        DocumentSnapshot rank3Snap = Tasks.await(db.collection("profiles").document("test_user_3").get(), 5, TimeUnit.SECONDS);
        DocumentSnapshot rank4Snap = Tasks.await(db.collection("profiles").document("test_user_4").get(), 5, TimeUnit.SECONDS);
        DocumentSnapshot rank11Snap = Tasks.await(db.collection("profiles").document("test_user_11").get(), 5, TimeUnit.SECONDS);

        assertNotNull("Rank 1 profile missing", rank1Snap);
        assertEquals("Rank 1 tokens mismatch", 10, rank1Snap.getLong("tokens").intValue());
        assertEquals("Rank 1 stars mismatch", 100, rank1Snap.getLong("stars").intValue());

        assertNotNull("Rank 2 profile missing", rank2Snap);
        assertEquals("Rank 2 tokens mismatch", 6, rank2Snap.getLong("tokens").intValue());
        assertEquals("Rank 2 stars mismatch", 100, rank2Snap.getLong("stars").intValue());

        assertNotNull("Rank 3 profile missing", rank3Snap);
        assertEquals("Rank 3 tokens mismatch", 4, rank3Snap.getLong("tokens").intValue());
        assertEquals("Rank 3 stars mismatch", 100, rank3Snap.getLong("stars").intValue());

        assertNotNull("Rank 4 profile missing", rank4Snap);
        assertEquals("Rank 4 tokens mismatch", 2, rank4Snap.getLong("tokens").intValue());
        assertEquals("Rank 4 stars mismatch", 100, rank4Snap.getLong("stars").intValue());

        assertNotNull("Rank 11 profile missing", rank11Snap);
        assertEquals("Rank 11 tokens mismatch", 0, rank11Snap.getLong("tokens").intValue());
        assertEquals("Rank 11 stars mismatch", 70, rank11Snap.getLong("stars").intValue()); // 30% penalty
    }

    @After
    public void tearDown() throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (int i = 1; i <= 11; i++) {
            String uid = "test_user_" + i;
            Tasks.await(db.collection("profiles").document(uid).delete(), 5, TimeUnit.SECONDS);
            Tasks.await(db.collection("rankingCycles").document(testCycleId)
                    .collection("entries").document(uid).delete(), 5, TimeUnit.SECONDS);
        }

        Tasks.await(db.collection("rankingCycles").document(testCycleId).delete(), 5, TimeUnit.SECONDS);
    }
}