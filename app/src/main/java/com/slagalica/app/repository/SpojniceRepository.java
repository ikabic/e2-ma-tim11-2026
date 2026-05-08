package com.slagalica.app.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.SpojniceQuestion;

import java.util.Collections;
import java.util.List;

public class SpojniceRepository {

    private final FirebaseFirestore db;
    private static final String COLLECTION = "spojnice_questions";

    public SpojniceRepository() {
        db = FirebaseFirestore.getInstance();
    }

    public void getRandomQuestions(RepositoryCallback<List<SpojniceQuestion>> callback) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SpojniceQuestion> all = snapshot.toObjects(SpojniceQuestion.class);

                    Collections.shuffle(all);
                    List<SpojniceQuestion> selected = all.subList(0, Math.min(2, all.size()));
                    for (SpojniceQuestion q : selected) {
                        Collections.shuffle(q.getLeftTerms());
                        Collections.shuffle(q.getRightTerms());
                    }

                    callback.onSuccess(selected);
                })
                .addOnFailureListener(callback::onFailure);
    }
}
