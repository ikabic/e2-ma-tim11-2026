package com.slagalica.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.User;
import com.slagalica.app.repository.RankingRepository;
import com.slagalica.app.repository.RegionRepository;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.ui.HomeActivity;
import com.slagalica.app.ui.auth.LoginActivity;
import com.slagalica.app.util.InviteManager;
import com.slagalica.app.util.UserStatusManager;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            InviteManager.get().startListening();

            FirebaseFirestore.getInstance().collection("users")
                    .document(FirebaseAuth.getInstance().getCurrentUser().getUid())
                    .get().addOnSuccessListener(document -> {
                        User user = document.toObject(User.class);
                        if (user != null)
                            UserStatusManager.goOnline(FirebaseAuth.getInstance(), user.getRegion());
                    })
                    .addOnFailureListener(e -> UserStatusManager.goOnline(FirebaseAuth.getInstance(), ""));

            RankingRepository rankingRepo = new RankingRepository();
            RegionRepository regionRepo = new RegionRepository();

            String pastWeeklyId = RankingRepository.getPastCycleId("weekly");
            String pastMonthlyId = RankingRepository.getPastCycleId("monthly");

            rankingRepo.secureAwardDistribution(pastWeeklyId, new RepositoryCallback<>() {
                @Override public void onSuccess(Void r) {}
                @Override public void onFailure(Exception e) { e.printStackTrace(); }
            });

            rankingRepo.secureAwardDistribution(pastMonthlyId, new RepositoryCallback<>() {
                @Override public void onSuccess(Void r) {}
                @Override public void onFailure(Exception e) { e.printStackTrace(); }
            });

            regionRepo.secureAwardDistribution(pastMonthlyId, new RepositoryCallback<>() {
                @Override public void onSuccess(Void r) {}
                @Override public void onFailure(Exception e) { e.printStackTrace(); }
            });
            
            startActivity(new Intent(this, HomeActivity.class));
        } else {
            startActivity(new Intent(this, LoginActivity.class));
        }
        finish();
    }
}
