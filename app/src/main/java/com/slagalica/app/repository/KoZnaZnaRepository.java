package com.slagalica.app.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.KoZnaZnaQuestion;

import java.util.Collections;
import java.util.List;

public class KoZnaZnaRepository {

    private final FirebaseFirestore db;
    private static final String COLLECTION = "ko_zna_zna_questions";

    public KoZnaZnaRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void getRandomQuestions(RepositoryCallback<List<KoZnaZnaQuestion>> callback) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<KoZnaZnaQuestion> all = snapshot.toObjects(KoZnaZnaQuestion.class);
                    Collections.shuffle(all);
                    callback.onSuccess(all.subList(0, Math.min(5, all.size())));
                })
                .addOnFailureListener(callback::onFailure);
    }
}
