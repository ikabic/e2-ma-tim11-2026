package com.slagalica.app.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.SkockoQuestion;

import java.util.Arrays;

public class SkockoRepository {

    private final FirebaseFirestore db;
    private static final String COLLECTION = "skocko_questions";

    public SkockoRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void seedTestData() {
        SkockoQuestion[] questions = {
                new SkockoQuestion(null, Arrays.asList(0, 1, 2, 3)),
                new SkockoQuestion(null, Arrays.asList(5, 5, 1, 4)),
                new SkockoQuestion(null, Arrays.asList(3, 0, 5, 2)),
                new SkockoQuestion(null, Arrays.asList(4, 3, 3, 0)),
                new SkockoQuestion(null, Arrays.asList(2, 4, 1, 5)),
                new SkockoQuestion(null, Arrays.asList(1, 2, 4, 3)),
                new SkockoQuestion(null, Arrays.asList(0, 0, 5, 1)),
        };
        for (SkockoQuestion q : questions) {
            db.collection(COLLECTION).add(q);
        }
    }

    public void getRandomQuestion(RepositoryCallback<SkockoQuestion> callback) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onFailure(new Exception("No Skočko questions available."));
                        return;
                    }
                    int idx = (int) (Math.random() * snapshot.size());
                    SkockoQuestion q = snapshot.getDocuments()
                            .get(idx)
                            .toObject(SkockoQuestion.class);
                    if (q != null) q.setId(snapshot.getDocuments().get(idx).getId());
                    callback.onSuccess(q);
                })
                .addOnFailureListener(callback::onFailure);
    }
}