package com.example.slagalica;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.Region;
import com.slagalica.app.repository.RegionRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class RegionAwardsDistributionTest {

    private final String testCycleId = "regional_test_cycle_2026";
    private final String[] userIds = {"user_vojvodina", "user_beograd", "user_sumadija", "user_juzna"};

    @Test
    public void runRegionRewardDistributionTest() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();

        Tasks.await(auth.signInAnonymously(), 5, TimeUnit.SECONDS);
        RegionRepository repository = new RegionRepository();

        CountDownLatch loadLatch = new CountDownLatch(1);
        repository.loadRegions(context, new RepositoryCallback<>() {
            @Override
            public void onSuccess(List<Region> result) { loadLatch.countDown(); }
            @Override
            public void onFailure(Exception e) { loadLatch.countDown(); }
        });
        assertTrue("Loading regions timed out.", loadLatch.await(10, TimeUnit.SECONDS));

        Map<String, Object> cycleMetadata = new HashMap<>();
        cycleMetadata.put("regionRewardsDistributed", false);
        Tasks.await(db.collection("rankingCycles").document(testCycleId).set(cycleMetadata), 5, TimeUnit.SECONDS);

        String[] regions = {"vojvodina", "beogradski_region", "sumadija_i_zapadna_srbija", "juzna_i_istocna_srbija"};
        long[] scores = {600L, 450L, 300L, 150L};

        for (int i = 0; i < userIds.length; i++) {
            String uid = userIds[i];
            String regionKey = regions[i];
            long stars = scores[i];

            Map<String, Object> userDoc = new HashMap<>();
            userDoc.put("region", regionKey);
            Tasks.await(db.collection("users").document(uid).set(userDoc), 5, TimeUnit.SECONDS);

            Map<String, Object> profileDoc = new HashMap<>();
            profileDoc.put("prevCycleRegionRank", -1);
            Tasks.await(db.collection("profiles").document(uid).set(profileDoc), 5, TimeUnit.SECONDS);

            Map<String, Object> entryDoc = new HashMap<>();
            entryDoc.put("cycleStars", stars);
            Tasks.await(db.collection("rankingCycles").document(testCycleId)
                    .collection("entries").document(uid).set(entryDoc), 5, TimeUnit.SECONDS);
        }

        CountDownLatch distributionLatch = new CountDownLatch(1);
        AtomicBoolean isSuccess = new AtomicBoolean(false);

        repository.secureAwardDistribution(testCycleId, new RepositoryCallback<>() {
            @Override
            public void onSuccess(Void result) {
                isSuccess.set(true);
                distributionLatch.countDown();
            }
            @Override
            public void onFailure(Exception e) {
                distributionLatch.countDown();
            }
        });

        boolean completed = distributionLatch.await(20, TimeUnit.SECONDS);
        assertTrue("Distribution process timed out.", completed);
        assertTrue("Distribution callback reported a failure error.", isSuccess.get());

        DocumentSnapshot cycleSnap = Tasks.await(db.collection("rankingCycles").document(testCycleId).get(), 5, TimeUnit.SECONDS);
        assertTrue("regionRewardsDistributed flag should switch to true", cycleSnap.getBoolean("regionRewardsDistributed"));

        DocumentSnapshot pVojvodina = Tasks.await(db.collection("profiles").document("user_vojvodina").get(), 5, TimeUnit.SECONDS);
        DocumentSnapshot pBeograd = Tasks.await(db.collection("profiles").document("user_beograd").get(), 5, TimeUnit.SECONDS);
        DocumentSnapshot pSumadija = Tasks.await(db.collection("profiles").document("user_sumadija").get(), 5, TimeUnit.SECONDS);
        DocumentSnapshot pJuzna = Tasks.await(db.collection("profiles").document("user_juzna").get(), 5, TimeUnit.SECONDS);

        assertEquals("Vojvodina user should be designated Rank 1", 1, pVojvodina.getLong("prevCycleRegionRank").intValue());
        assertEquals("Beograd user should be designated Rank 2", 2, pBeograd.getLong("prevCycleRegionRank").intValue());
        assertEquals("Šumadija user should be designated Rank 3", 3, pSumadija.getLong("prevCycleRegionRank").intValue());
        assertEquals("Južna user should remain 0 (unplaced outside podium)", 0, pJuzna.getLong("prevCycleRegionRank").intValue());

        DocumentSnapshot rGold = Tasks.await(db.collection("regions").document("vojvodina").get(), 5, TimeUnit.SECONDS);
        DocumentSnapshot rSilver = Tasks.await(db.collection("regions").document("beogradski_region").get(), 5, TimeUnit.SECONDS);
        DocumentSnapshot rBronze = Tasks.await(db.collection("regions").document("sumadija_i_zapadna_srbija").get(), 5, TimeUnit.SECONDS);

        assertNotNull("Gold region object context shouldn't be null", rGold);
        assertTrue("Vojvodina gold count should be incremented", rGold.getLong("goldCount") >= 1);
        assertTrue("Beograd silver count should be incremented", rSilver.getLong("silverCount") >= 1);
        assertTrue("Šumadija bronze count should be incremented", rBronze.getLong("bronzeCount") >= 1);
    }

    @After
    public void tearDown() throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Tasks.await(db.collection("regions").document("vojvodina")
                .update("goldCount", FieldValue.increment(-1)), 5, TimeUnit.SECONDS);
        Tasks.await(db.collection("regions").document("beogradski_region")
                .update("silverCount", FieldValue.increment(-1)), 5, TimeUnit.SECONDS);
        Tasks.await(db.collection("regions").document("sumadija_i_zapadna_srbija")
                .update("bronzeCount", FieldValue.increment(-1)), 5, TimeUnit.SECONDS);

        for (String uid : userIds) {
            Tasks.await(db.collection("users").document(uid).delete(), 5, TimeUnit.SECONDS);
            Tasks.await(db.collection("profiles").document(uid).delete(), 5, TimeUnit.SECONDS);
            Tasks.await(db.collection("rankingCycles").document(testCycleId)
                    .collection("entries").document(uid).delete(), 5, TimeUnit.SECONDS);
        }

        Tasks.await(db.collection("rankingCycles").document(testCycleId).delete(), 5, TimeUnit.SECONDS);
    }
}